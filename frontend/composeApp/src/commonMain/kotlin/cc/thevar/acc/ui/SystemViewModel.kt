package cc.thevar.acc.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.thevar.acc.protocol.SystemState
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class SystemViewModel : ViewModel() {
    private val _state = MutableStateFlow<SystemState?>(null)
    val state: StateFlow<SystemState?> = _state.asStateFlow()

    private val client = HttpClient {
        install(WebSockets)
    }

    fun connect(host: String = "localhost", port: Int = 8333) {
        viewModelScope.launch {
            try {
                client.webSocket(host = host, port = port, path = "/ws/system") {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            _state.value = Json.decodeFromString<SystemState>(text)
                        }
                    }
                }
            } catch (e: Exception) {
                println("System WebSocket failed: ${e.message}")
            }
        }
    }
}
