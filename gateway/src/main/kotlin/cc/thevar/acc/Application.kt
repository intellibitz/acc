package cc.thevar.acc

import cc.thevar.acc.protocol.*
import cc.thevar.acc.service.FleetManager
import cc.thevar.acc.service.ProvisioningService
import cc.thevar.acc.service.SupervisorService
import io.ktor.http.*
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

// Robust project root detection
private fun findProjectRoot(): File {
    val userDir = System.getProperty("user.dir")?.let { File(it) } ?: File(".")
    var current: File? = userDir.absoluteFile
    
    while (current != null) {
        if (File(current, "acc").exists() && File(current, "settings.gradle.kts").exists()) {
            return current
        }
        current = current.parentFile
    }
    return userDir.absoluteFile
}

val projectRoot = findProjectRoot()
val fleetManager = FleetManager(File(projectRoot, "config"))
val provisioningService = ProvisioningService(projectRoot, fleetManager)
val supervisorService = SupervisorService(projectRoot)

val uiSessions = Collections.newSetFromMap(ConcurrentHashMap<DefaultWebSocketServerSession, Boolean>())
val systemSessions = Collections.newSetFromMap(ConcurrentHashMap<DefaultWebSocketServerSession, Boolean>())

var systemStatusMsg = "Initializing Acc..."

fun main() {
    println("[Manager] Starting from: ${System.getProperty("user.dir")}")
    println("[Manager] Detected Project Root: ${projectRoot.absolutePath}")
    
    val keyStoreFile = File(projectRoot, "config/keystore.p12")
    val httpPort = 8333
    val httpsPort = 8334
    
    val server = embeddedServer(Netty, configure = {
        // Always add HTTP connector
        connector {
            port = httpPort
            host = "0.0.0.0"
        }

        // Add HTTPS connector if keystore exists
        if (keyStoreFile.exists()) {
            val keyStorePassword = "password".toCharArray()
            val keyStore = KeyStore.getInstance("PKCS12")
            keyStoreFile.inputStream().use { keyStore.load(it, keyStorePassword) }

            sslConnector(
                keyStore = keyStore,
                keyAlias = "acc",
                keyStorePassword = { keyStorePassword },
                privateKeyPassword = { keyStorePassword }
            ) {
                port = httpsPort
                keyStorePath = keyStoreFile
                host = "0.0.0.0"
            }
        }
    }) {
        module()
    }
    server.start(wait = true)
}

fun Application.module() {
    // Check for first-run bootstrap
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
                    lines.forEach { line -> systemStatusMsg = line }
                }
                process.waitFor()
                initSentinel.createNewFile()
                systemStatusMsg = "Bootstrap complete. Refreshing..."
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

    // System metrics polling
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
                            val statesJson = Json.encodeToString(supervisorService.workerStates.value)
                            output.replace("\"statusMsg\"\\s*:\\s*\"[^\"]*\"".toRegex(), "\"statusMsg\":\"$systemStatusMsg\"")
                                  .replace("\"workers\"\\s*:\\s*\\[\\]".toRegex(), "\"workers\":$statesJson")
                        } catch (e: Exception) { output }
                        
                        sessionsToUpdate.forEach { session ->
                            session.launch { try { session.send(Frame.Text(json)) } catch (e: Exception) {} }
                        }
                    }
                } catch (e: Exception) {
                    println("System poll error: ${e.message}")
                }
            }
            delay(2000)
        }
    }

    // Provisioning updates streaming
    launch(Dispatchers.IO) {
        provisioningService.updates.collect { updates ->
            val updateFrame = Frame.Text(Json.encodeToString(updates.values.toList()))
            uiSessions.forEach { session ->
                session.launch { try { session.send(updateFrame) } catch (e: Exception) {} }
            }
        }
    }

    // Supervisor updates streaming
    launch(Dispatchers.IO) {
        supervisorService.workerStates.collect { states ->
            val stateUpdate = Frame.Text(Json.encodeToString(states))
            uiSessions.forEach { session ->
                session.launch { try { session.send(stateUpdate) } catch (e: Exception) {} }
            }
        }
    }

    routing {
        // Essential APIs
        get("/health") {
            val states = supervisorService.workerStates.value
            val isHealthy = states.all { it.status == WorkerStatus.RUNNING || it.status == WorkerStatus.COMPLETED }
            if (isHealthy) {
                call.respondText("Acc Gateway is Online. All workers healthy.")
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, states)
            }
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

        // WebSockets
        webSocket("/ws/ui") { uiSessions.add(this); try { for (frame in incoming) { } } finally { uiSessions.remove(this) } }
        webSocket("/ws/system") { systemSessions.add(this); try { for (frame in incoming) { } } finally { systemSessions.remove(this) } }
        
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
                        send(Frame.Text(Json.encodeToString(ConsoleLine("Task Finished", "SUCCESS"))))
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
                        if (frame.readText() == "UP") provisioningService.provisionAll()
                    }
                }
            } finally { }
        }

        // STATIC CONTENT SERVING (MOVED AND CONFIGURED)
        val staticDir = File(projectRoot, "frontend/web/build/dist/wasmJs/productionExecutable")
        
        // Manual root handler with Recovery/Bootstrap UI
        get("/") {
            val indexFile = File(staticDir, "index.html")
            if (indexFile.exists()) {
                call.respondFile(indexFile)
            } else {
                val workerStates = supervisorService.workerStates.value
                val builder = workerStates.find { it.name == "FRONTEND_BUILDER" }
                
                val bootstrapHtml = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <title>Acc Bootstrapping...</title>
                        <style>
                            body { background: #121212; color: #BB86FC; font-family: monospace; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100vh; margin: 0; }
                            .loader { border: 4px solid #1E1E1E; border-top: 4px solid #BB86FC; border-radius: 50%; width: 40px; height: 40px; animation: spin 1s linear infinite; margin-bottom: 20px; }
                            @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
                            .status { color: #03DAC6; font-size: 1.2em; }
                        </style>
                        <meta http-equiv="refresh" content="5">
                    </head>
                    <body>
                        <div class="loader"></div>
                        <div>AI Command Center is initializing...</div>
                        <div class="status" style="color: #03DAC6; margin-top: 10px;">
                            ${systemStatusMsg}
                        </div>
                        <div style="margin-top: 20px; color: ${if (builder?.status == WorkerStatus.CRASHED) "red" else "#BB86FC"}">
                            Frontend Build: ${builder?.status ?: "WAITING"}
                        </div>
                        <div style="margin-top: 10px; color: gray; font-size: 0.9em; max-width: 80%; text-align: center;">
                            ${builder?.lastMsg ?: ""}
                            ${if (builder?.status == WorkerStatus.CRASHED) "<br><br><button onclick=\"location.reload()\" style=\"background: #BB86FC; border: none; padding: 10px 20px; border-radius: 4px; color: black; font-weight: bold; cursor: pointer;\">Retry Build</button>" else ""}
                        </div>
                        <div style="margin-top: 30px; color: gray; font-size: 0.7em;">(Auto-refreshing every 5s)</div>
                    </body>
                    </html>
                """.trimIndent()
                
                call.respondText(bootstrapHtml, ContentType.Text.Html)
                
                // Trigger builder if not started
                if (builder == null || builder.status == WorkerStatus.STOPPED) {
                    supervisorService.startWorker("FRONTEND_BUILDER")
                }
            }
        }

        // Serve everything else
        staticFiles("/", staticDir) {
            // Ensure WASM MIME type is set
            contentType { file ->
                when (file.extension) {
                    "wasm" -> ContentType.Application.Wasm
                    "js" -> ContentType.Application.JavaScript
                    "css" -> ContentType.Text.CSS
                    "html" -> ContentType.Text.Html
                    else -> null
                }
            }
        }
    }
}
