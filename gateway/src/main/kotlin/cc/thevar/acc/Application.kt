package cc.thevar.acc

import cc.thevar.acc.protocol.*
import cc.thevar.acc.service.*
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
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import java.io.File
import java.security.KeyStore
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

// Robust project root detection for source and standalone modes
private fun findProjectRoot(): File {
    val envRoot = System.getenv("ACC_ROOT")?.let { File(it) }
    if (envRoot != null && envRoot.exists()) return envRoot

    val userDir = System.getProperty("user.dir")?.let { File(it) } ?: File(".")
    var current: File? = userDir.absoluteFile

    while (current != null) {
        // Source mode indicators
        if (File(current, "acc").exists() && File(current, "settings.gradle.kts").exists()) {
            return current
        }
        // Standalone mode indicators (installed via install.sh)
        if (File(current, "acc").exists() && File(current, "config").exists() && File(
                current,
                "data"
            ).exists()
        ) {
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
val commandHandler = CommandHandler(projectRoot, provisioningService)

val uiSessions =
    Collections.newSetFromMap(ConcurrentHashMap<DefaultWebSocketServerSession, Boolean>())
val systemSessions =
    Collections.newSetFromMap(ConcurrentHashMap<DefaultWebSocketServerSession, Boolean>())

var systemStatusMsg = "Initializing Acc..."

fun main() {
    println("[Manager] Starting from: ${System.getProperty("user.dir")}")
    println("[Manager] Detected Project Root: ${projectRoot.absolutePath}")

    val keyStoreFile = File(projectRoot, "config/keystore.p12")
    val httpPort = 8333
    val httpsPort = 8334

    val server = embeddedServer(Netty, configure = {
        connector {
            port = httpPort
            host = "0.0.0.0"
        }

        if (keyStoreFile.exists()) {
            val keyStorePassword = System.getenv("ACC_KEYSTORE_PASSWORD")?.toCharArray()
                ?: "password".toCharArray()
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
    val initSentinel = File(projectRoot, "data/.initialized")
    if (!initSentinel.exists()) {
        launch(Dispatchers.IO) {
            try {
                systemStatusMsg = "Bootstrapping environment..."
                val process = ProcessBuilder("./acc", "setup")
                    .directory(projectRoot)
                    .redirectErrorStream(true)
                    .start()

                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line -> systemStatusMsg = line }
                }
                process.waitFor()
                initSentinel.createNewFile()
                systemStatusMsg = "Acc Ready."
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

    // System metrics streaming from Supervisor (System Bridge)
    launch(Dispatchers.IO) {
        supervisorService.getWorkerOutput("SYSTEM_BRIDGE").collect { output ->
            try {
                if (output.startsWith("{") && output.endsWith("}")) {
                    val bridgeData = Json.parseToJsonElement(output).jsonObject

                    if (bridgeData.containsKey("error")) {
                        systemStatusMsg =
                            bridgeData["error"]?.jsonPrimitive?.content ?: "Bridge Error"
                        return@collect
                    }

                    val fullState = SystemState(
                        stats = Json.decodeFromJsonElement<SystemStats>(bridgeData["stats"]!!),
                        fleet = Json.decodeFromJsonElement<List<ModelStatus>>(bridgeData["fleet"]!!),
                        partialDownloads = Json.decodeFromJsonElement<List<String>>(bridgeData["partialDownloads"]!!),
                        proxyOnline = Json.decodeFromJsonElement<Boolean>(bridgeData["proxyOnline"]!!),
                        engineOnline = bridgeData["engineOnline"]?.jsonPrimitive?.boolean ?: false,
                        workers = supervisorService.workerStates.value,
                        provisioning = provisioningService.updates.value.values.toList(),
                        statusMsg = systemStatusMsg
                    )

                    val jsonFrame = Frame.Text(Json.encodeToString(fullState))
                    val sessions = systemSessions + uiSessions
                    sessions.forEach { session ->
                        session.launch {
                            try {
                                session.send(jsonFrame)
                            } catch (e: Exception) {
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore parsing errors for partial lines
            }
        }
    }

    // Provisioning updates streaming
    launch(Dispatchers.IO) {
        provisioningService.updates.collect { updates ->
            val updateFrame = Frame.Text(Json.encodeToString(updates.values.toList()))
            uiSessions.forEach { session ->
                session.launch {
                    try {
                        session.send(updateFrame)
                    } catch (e: Exception) {
                    }
                }
            }
        }
    }

    // Supervisor updates streaming
    launch(Dispatchers.IO) {
        supervisorService.workerStates.collect { states ->
            val stateUpdate = Frame.Text(Json.encodeToString(states))
            uiSessions.forEach { session ->
                session.launch {
                    try {
                        session.send(stateUpdate)
                    } catch (e: Exception) {
                    }
                }
            }
        }
    }

    routing {
        get("/health") {
            val states = supervisorService.workerStates.value
            val isHealthy =
                states.all { it.status == WorkerStatus.RUNNING || it.status == WorkerStatus.COMPLETED || it.name == "FRONTEND_BUILDER" }
            if (isHealthy) {
                call.respondText("Acc Gateway is Online.")
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, states)
            }
        }

        post("/provisioning/up") {
            val modelName = call.request.queryParameters["model"]
            if (modelName != null) {
                val model = fleetManager.getFleet().find { it.name == modelName }
                if (model != null) {
                    provisioningService.startProvisioning(model)
                    call.respondText("Provisioning for $modelName started.")
                } else {
                    call.respond(HttpStatusCode.NotFound, "Model $modelName not found in fleet.")
                }
            } else {
                provisioningService.provisionAll()
                call.respondText("Global provisioning started.")
            }
        }

        post("/system/update") {
            launch(Dispatchers.IO) {
                delay(500)
                ProcessBuilder("./acc", "update").directory(projectRoot).start()
            }
            call.respondText("Update sequence initiated.")
        }

        post("/agent/spawn") {
            val agentName = call.request.queryParameters["name"] ?: "Architect-${System.currentTimeMillis() % 1000}"
            val model = call.request.queryParameters["model"] ?: "ollama/phi3"
            val apiBase = call.request.queryParameters["apiBase"]
            
            supervisorService.spawnAgent(agentName, model, apiBase)
            call.respondText("Agent $agentName spawned for model $model.")
        }

        post("/engine/start") {
            val provider = call.request.queryParameters["provider"] ?: "ollama"
            supervisorService.startEngine(provider)
            call.respondText("Starting engine: $provider")
        }

        webSocket("/ws/ui") {
            uiSessions.add(this); try {
            for (frame in incoming) {
            }
        } finally {
            uiSessions.remove(this)
        }
        }
        webSocket("/ws/system") {
            systemSessions.add(this); try {
            for (frame in incoming) {
            }
        } finally {
            systemSessions.remove(this)
        }
        }

        webSocket("/ws/console") {
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val commandLine = frame.readText().trim()
                        commandHandler.handleCommand(commandLine).collect { line: ConsoleLine ->
                            send(Frame.Text(Json.encodeToString<ConsoleLine>(line)))
                        }
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
                        uiSessions.forEach { session ->
                            session.launch {
                                session.send(
                                    Frame.Text(
                                        text
                                    )
                                )
                            }
                        }
                    }
                }
            } finally {
            }
        }

        webSocket("/ws/provisioning") {
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        if (frame.readText() == "UP") provisioningService.provisionAll()
                    }
                }
            } finally {
            }
        }

        val staticDir = File(projectRoot, "frontend/web/build/dist/wasmJs/productionExecutable")

        get("/") {
            // Priority 1: Check classpath resources (bundled mode)
            val resource = this::class.java.classLoader.getResource("static/index.html")
            if (resource != null) {
                call.respondText(
                    resource.readBytes().toString(Charsets.UTF_8),
                    ContentType.Text.Html
                )
                return@get
            }

            // Priority 2: Check disk (dev mode)
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
                        <div class="status" style="color: #03DAC6; margin-top: 10px;">${systemStatusMsg}</div>
                        <div style="margin-top: 20px; color: ${if (builder?.status == WorkerStatus.CRASHED) "red" else "#BB86FC"}">
                            Frontend Build: ${builder?.status ?: "WAITING"}
                        </div>
                        <div style="margin-top: 30px; color: gray; font-size: 0.7em;">(Auto-refreshing every 5s)</div>
                    </body>
                    </html>
                """.trimIndent()

                call.respondText(bootstrapHtml, ContentType.Text.Html)

                if (builder == null || builder.status == WorkerStatus.STOPPED) {
                    supervisorService.startWorker("FRONTEND_BUILDER")
                }
            }
        }

        // Static resource handling
        staticResources("/", "static") {
            contentType { file ->
                val name = file.path.lowercase()
                when {
                    name.endsWith(".wasm") -> ContentType.Application.Wasm
                    name.endsWith(".js") -> ContentType.Application.JavaScript
                    name.endsWith(".css") -> ContentType.Text.CSS
                    name.endsWith(".html") -> ContentType.Text.Html
                    else -> null
                }
            }
        }

        staticFiles("/", staticDir) {
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
