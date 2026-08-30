package cc.thevar.acc.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.thevar.acc.protocol.AgentMessage
import cc.thevar.acc.protocol.AgentStatus
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class AgentViewModel : ViewModel() {
    private val _messages = MutableStateFlow<List<AgentMessage>>(emptyList())
    val messages: StateFlow<List<AgentMessage>> = _messages.asStateFlow()

    private val client = HttpClient {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Json)
        }
        install(ContentNegotiation) {
            json()
        }
    }

    fun connect(host: String = "localhost", port: Int = 8080) {
        viewModelScope.launch {
            try {
                client.webSocket(host = host, port = port, path = "/ws/ui") {
                    println("Connected to Gateway WebSocket")
                    while (true) {
                        try {
                            val message = receiveDeserialized<AgentMessage>()
                            _messages.update { it + message }
                        } catch (e: Exception) {
                            println("Error receiving message: ${e.message}")
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                println("WebSocket connection failed: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        client.close()
        super.onCleared()
    }
}
