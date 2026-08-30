package cc.thevar.acc

import cc.thevar.acc.protocol.*
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

fun main() {
    embeddedServer(Netty, port = 8333, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

val uiSessions = Collections.newSetFromMap(ConcurrentHashMap<DefaultWebSocketServerSession, Boolean>())
val systemSessions = Collections.newSetFromMap(ConcurrentHashMap<DefaultWebSocketServerSession, Boolean>())

fun Application.module() {
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
            if (systemSessions.isNotEmpty()) {
                try {
                    val process = ProcessBuilder("python3", "brain/system_bridge.py")
                        .redirectError(ProcessBuilder.Redirect.PIPE)
                        .start()
                    val output = process.inputStream.bufferedReader().readText()
                    if (output.isNotEmpty()) {
                        systemSessions.forEach { session ->
                            session.launch {
                                session.send(Frame.Text(output))
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

    routing {
        // Serve the Visual Dashboard (Web Frontend)
        staticFiles("/", File("frontend/web/build/dist/wasmJs/productionExecutable")) {
            default("index.html")
        }

        get("/health") {
            call.respondText("Acc Gateway is Online.")
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
    }
}
