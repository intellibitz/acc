package cc.thevar.acc

import cc.thevar.acc.protocol.*
import cc.thevar.acc.service.FleetManager
import cc.thevar.acc.service.ProvisioningService
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

// Improved project root detection: find the directory containing 'acc' script
private fun findProjectRoot(): File {
    var current = File(".").absoluteFile
    while (current != null) {
        if (File(current, "acc").exists()) return current
        current = current.parentFile
    }
    return File(".").absoluteFile
}

val projectRoot = findProjectRoot()
val fleetManager = FleetManager(File(projectRoot, "config"))
val provisioningService = ProvisioningService(projectRoot, fleetManager)

val uiSessions = Collections.newSetFromMap(ConcurrentHashMap<DefaultWebSocketServerSession, Boolean>())
val systemSessions = Collections.newSetFromMap(ConcurrentHashMap<DefaultWebSocketServerSession, Boolean>())

var systemStatusMsg = "Initializing Acc..."

fun main() {
    val keyStoreFile = File(projectRoot, "config/keystore.p12")
    val serverPort = 8333
    
    val server = if (keyStoreFile.exists()) {
        val keyStorePassword = "password".toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStoreFile.inputStream().use { keyStore.load(it, keyStorePassword) }

        embeddedServer(Netty, configure = {
            sslConnector(
                keyStore = keyStore,
                keyAlias = "acc",
                keyStorePassword = { keyStorePassword },
                privateKeyPassword = { keyStorePassword }
            ) {
                port = serverPort
                keyStorePath = keyStoreFile
            }
        }) {
            module()
        }
    } else {
        embeddedServer(Netty, port = serverPort, host = "0.0.0.0") {
            module()
        }
    }
    server.start(wait = true)
}

fun Application.module() {
    // Check for first-run
    val initSentinel = File(projectRoot, "data/.initialized")
    if (!initSentinel.exists()) {
        launch(Dispatchers.IO) {
            try {
                systemStatusMsg = "Bootstrapping environment (Health Audit)..."
                val process = ProcessBuilder("./acc", "setup")
                    .directory(projectRoot)
                    .redirectErrorStream(true)
                    .start()
                
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        systemStatusMsg = line
                    }
                }
                process.waitFor()
                initSentinel.createNewFile()
                systemStatusMsg = "Bootstrap complete. Restarting for security..."
                delay(1000)
                ProcessBuilder("./acc", "refresh").directory(projectRoot).start()
            } catch (e: Exception) {
                systemStatusMsg = "Bootstrap Error: ${e.message}"
            }
        }
    } else {
        systemStatusMsg = "Acc Ready."
    }

    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    install(ContentNegotiation) {
        json()
    }

    // Launch background task for system state polling
    launch(Dispatchers.IO) {
        while (isActive) {
            val sessionsToUpdate = systemSessions + uiSessions
            if (sessionsToUpdate.isNotEmpty()) {
                try {
                    val process = ProcessBuilder("python3", "brain/system_bridge.py")
                        .directory(projectRoot)
                        .redirectError(ProcessBuilder.Redirect.PIPE)
                        .start()
                    val output = process.inputStream.bufferedReader().readText()
                    if (output.isNotEmpty()) {
                        val json = try {
                            output.replace("\"statusMsg\"\\s*:\\s*\"[^\"]*\"".toRegex(), "\"statusMsg\":\"$systemStatusMsg\"")
                        } catch (e: Exception) { output }
                        
                        sessionsToUpdate.forEach { session ->
                            session.launch {
                                try { session.send(Frame.Text(json)) } catch (e: Exception) {}
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("System poll error: ${e.message}")
                }
            }
            delay(2000)
        }
    }

    // Launch background task for provisioning updates
    launch(Dispatchers.IO) {
        provisioningService.updates.collect { updates ->
            val systemStateUpdate = Frame.Text(Json.encodeToString(updates.values.toList()))
            uiSessions.forEach { session ->
                session.launch {
                    try { session.send(systemStateUpdate) } catch (e: Exception) {}
                }
            }
        }
    }

    routing {
        get("/") {
            val file = File(projectRoot, "frontend/web/build/dist/wasmJs/productionExecutable/index.html")
            if (file.exists()) {
                call.respondFile(file)
            } else {
                call.respondText("Acc Visual Dashboard is compiling... Please refresh in a few seconds.\n(Check logs/gateway.log for progress)")
            }
        }

        get("/health") {
            call.respondText("Acc Gateway is Online.")
        }

        post("/provisioning/up") {
            provisioningService.provisionAll()
            call.respondText("Provisioning started.")
        }

        post("/system/update") {
            launch(Dispatchers.IO) {
                delay(500)
                ProcessBuilder("./acc", "update").directory(projectRoot).start()
            }
            call.respondText("Update sequence initiated.")
        }

        webSocket("/ws/ui") {
            uiSessions.add(this)
            try { for (frame in incoming) { } } finally { uiSessions.remove(this) }
        }

        webSocket("/ws/system") {
            systemSessions.add(this)
            try { for (frame in incoming) { } } finally { systemSessions.remove(this) }
        }

        webSocket("/ws/console") {
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val command = frame.readText()
                        send(Frame.Text(Json.encodeToString(ConsoleLine("$ acc $command", "COMMAND"))))
                        val process = ProcessBuilder("./acc", command).directory(projectRoot).redirectErrorStream(true).start()
                        process.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { line -> launch { send(Frame.Text(Json.encodeToString(ConsoleLine(line, "INFO")))) } }
                        }
                        process.waitFor()
                        send(Frame.Text(Json.encodeToString(ConsoleLine("Task Finished: $command", "SUCCESS"))))
                    }
                }
            } catch (e: Exception) {
                send(Frame.Text(Json.encodeToString(ConsoleLine("Error: ${e.message}", "ERROR"))))
            }
        }

        webSocket("/ws/agent") {
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        uiSessions.forEach { session -> session.launch { session.send(Frame.Text(text)) } }
                    }
                }
            } finally { }
        }

        webSocket("/ws/provisioning") {
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val cmd = frame.readText()
                        if (cmd == "UP") provisioningService.provisionAll()
                    }
                }
            } finally { }
        }

        staticFiles("/", File(projectRoot, "frontend/web/build/dist/wasmJs/productionExecutable")) {
            default("index.html")
        }
    }
}
