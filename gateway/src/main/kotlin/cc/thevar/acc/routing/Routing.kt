package cc.thevar.acc.routing

import cc.thevar.acc.protocol.*
import cc.thevar.acc.service.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import java.io.File

fun Application.configureRouting() {
    val projectRoot by inject<File>()
    val fleetManager by inject<FleetManager>()
    val provisioningService by inject<ProvisioningService>()
    val supervisorService by inject<SupervisorService>()
    val commandHandler by inject<CommandHandler>()
    val sessionManager by inject<SessionManager>()

    routing {
        authenticate("auth-basic") {
            get("/health") {
            val states = supervisorService.workerStates.value
            val isHealthy = states.all { 
                it.status == WorkerStatus.RUNNING || 
                it.status == WorkerStatus.COMPLETED || 
                it.name == "FRONTEND_BUILDER" 
            }
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
            call.respondText("Update sequence initiated (Native).")
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
            sessionManager.uiSessions.add(this)
            try {
                for (frame in incoming) { }
            } finally {
                sessionManager.uiSessions.remove(this)
            }
        }

        webSocket("/ws/system") {
            sessionManager.systemSessions.add(this)
            try {
                for (frame in incoming) { }
            } finally {
                sessionManager.systemSessions.remove(this)
            }
        }

        webSocket("/ws/console") {
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val commandLine = frame.readText().trim()
                        commandHandler.handleCommand(commandLine).collect { line: ConsoleLine ->
                            send(Frame.Text(Json.encodeToString(line)))
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
                        sessionManager.uiSessions.forEach { session ->
                            session.launch {
                                session.send(Frame.Text(text))
                            }
                        }
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
        } // End authenticate

        val staticDir = File(projectRoot, "frontend/web/build/dist/wasmJs/productionExecutable")

        get("/") {
            val resource = this::class.java.classLoader.getResource("static/index.html")
            if (resource != null) {
                call.respondText(resource.readBytes().toString(Charsets.UTF_8), ContentType.Text.Html)
                return@get
            }

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
                        <div class="status" style="color: #03DAC6; margin-top: 10px;">${sessionManager.systemStatusMsg}</div>
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
