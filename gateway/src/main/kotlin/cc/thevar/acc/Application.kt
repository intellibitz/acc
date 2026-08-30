package cc.thevar.acc

import cc.thevar.acc.protocol.AgentMessage
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

val uiSessions = Collections.newSetFromMap(ConcurrentHashMap<DefaultWebSocketServerSession, Boolean>())

fun Application.module() {
    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }
    install(ContentNegotiation) {
        json()
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
            println("UI Client connected: $this")
            uiSessions.add(this)
            try {
                for (frame in incoming) {
                    // Just keep the connection alive
                }
            } finally {
                uiSessions.remove(this)
                println("UI Client disconnected: $this")
            }
        }

        // Endpoint for Agents to stream their state
        webSocket("/ws/agent") {
            println("Agent Bridge connected: $this")
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        // Broadcast to all UI clients
                        val message = Json.decodeFromString<AgentMessage>(text)
                        println("Agent [${message.agentName}] is ${message.status}")
                        
                        uiSessions.forEach { session ->
                            session.send(Frame.Text(text))
                        }
                    }
                }
            } catch (e: Exception) {
                println("Agent Bridge error: ${e.message}")
            } finally {
                println("Agent Bridge disconnected")
            }
        }
    }
}
