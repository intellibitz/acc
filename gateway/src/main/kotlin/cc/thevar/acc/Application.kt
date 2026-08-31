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

fun main() {
    val keyStoreFile = File("config/keystore.p12")
    val serverPort = 8333
    
    if (keyStoreFile.exists()) {
        val keyStorePassword = "password".toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStoreFile.inputStream().use { 
            keyStore.load(it, keyStorePassword) 
        }

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
        }.start(wait = true)
    } else {
        embeddedServer(Netty, port = serverPort, host = "0.0.0.0", module = Application::module)
            .start(wait = true)
    }
}

val uiSessions = Collections.newSetFromMap(ConcurrentHashMap<DefaultWebSocketServerSession, Boolean>())
val systemSessions = Collections.newSetFromMap(ConcurrentHashMap<DefaultWebSocketServerSession, Boolean>())

val projectRoot = File(".").absoluteFile.parentFile ?: File(".")
val fleetManager = FleetManager(File(projectRoot, "config"))
val provisioningService = ProvisioningService(projectRoot, fleetManager)

var systemStatusMsg = "Initializing Acc..."

fun Application.module() {
    // Check for first-run
    val initSentinel = File(projectRoot, "data/.initialized")
    if (!initSentinel.exists()) {
        launch(Dispatchers.IO) {
            systemStatusMsg = "Bootstrapping environment (Health Audit)..."
            val process = ProcessBuilder("./acc", "setup")
                .directory(projectRoot)
                .redirectErrorStream(true)
                .start()
            
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    systemStatusMsg = line
                    // Stream to console if needed
                }
            }
            process.waitFor()
            initSentinel.createNewFile()
            systemStatusMsg = "Bootstrap complete. Restarting for security..."
            delay(1000)
            ProcessBuilder("./acc", "refresh").directory(projectRoot).start()
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
                        .redirectError(ProcessBuilder.Redirect.PIPE)
                        .start()
                    val output = process.inputStream.bufferedReader().readText()
                    if (output.isNotEmpty()) {
                        // Inject our internal status message
                        val json = Json.parseToJsonElement(output).let { element ->
                            val map = element.run { 
                                // In a real implementation, we'd use a proper serializer
                                // but for a quick fix, we'll just update the statusMsg field
                                output.replace("\"statusMsg\":\"\"", "\"statusMsg\":\"$systemStatusMsg\"")
                            }
                            map
                        }
                        
                        sessionsToUpdate.forEach { session ->
                            session.launch {
                                session.send(Frame.Text(json))
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
                    session.send(systemStateUpdate)
                }
            }
        }
    }

    routing {
        get("/") {
            call.respondFile(File("frontend/web/build/dist/wasmJs/productionExecutable/index.html"))
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
                delay(500) // Give response time to reach client
                ProcessBuilder("./acc", "update")
                    .directory(File("."))
                    .start()
            }
            call.respondText("Update sequence initiated. Gateway will restart.")
        }

        // Endpoint for the UI to subscribe to updates
        webSocket("/ws/ui") {
            uiSessions.add(this)
            try {
                for (frame in incoming) { }
            } finally {
                uiSessions.remove(this)
            }
        }

        // Endpoint for System Stats
        webSocket("/ws/system") {
            systemSessions.add(this)
            try {
                for (frame in incoming) { }
            } finally {
                systemSessions.remove(this)
            }
        }

        // Endpoint for Console Commands
        webSocket("/ws/console") {
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val command = frame.readText()
                        send(Frame.Text(Json.encodeToString(ConsoleLine("$ acc $command", "COMMAND"))))
                        
                        val process = ProcessBuilder("./acc", command)
                            .directory(File("."))
                            .redirectErrorStream(true)
                            .start()
                        
                        process.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                launch {
                                    send(Frame.Text(Json.encodeToString(ConsoleLine(line, "INFO"))))
                                }
                            }
                        }
                        process.waitFor()
                        send(Frame.Text(Json.encodeToString(ConsoleLine("Task Finished: $command", "SUCCESS"))))
                    }
                }
            } catch (e: Exception) {
                send(Frame.Text(Json.encodeToString(ConsoleLine("Error: ${e.message}", "ERROR"))))
            }
        }

        // Endpoint for Agents to stream their state
        webSocket("/ws/agent") {
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        uiSessions.forEach { session ->
                            session.launch {
                                session.send(Frame.Text(text))
                            }
                        }
                    }
                }
            } finally { }
        }

        // Endpoint for Provisioning Control
        webSocket("/ws/provisioning") {
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val cmd = frame.readText()
                        when {
                            cmd == "UP" -> provisioningService.provisionAll()
                            cmd.startsWith("ADD|") -> {
                                // Add logic
                            }
                        }
                    }
                }
            } finally { }
        }

        // Serve the Visual Dashboard (Web Frontend)
        staticFiles("/", File("frontend/web/build/dist/wasmJs/productionExecutable")) {
            default("index.html")
        }
    }
}
