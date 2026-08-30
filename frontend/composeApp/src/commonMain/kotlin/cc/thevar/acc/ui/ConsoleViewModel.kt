package cc.thevar.acc.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.thevar.acc.protocol.ConsoleLine
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class ConsoleViewModel : ViewModel() {
    private val _lines = MutableStateFlow<List<ConsoleLine>>(emptyList())
    val lines: StateFlow<List<ConsoleLine>> = _lines.asStateFlow()

    private val client = HttpClient {
        install(WebSockets)
    }
    private var session: DefaultClientWebSocketSession? = null

    fun connect(host: String = "localhost", port: Int = 8333) {
        viewModelScope.launch {
            try {
                // Try secure first
                try {
                    client.webSocket(host = host, port = port, path = "/ws/console", request = { url.protocol = io.ktor.http.URLProtocol.WSS }) {
                        this@ConsoleViewModel.session = this
                        processSession(this)
                    }
                } catch (e: Exception) {
                    client.webSocket(host = host, port = port, path = "/ws/console") {
                        this@ConsoleViewModel.session = this
                        processSession(this)
                    }
                }
            } catch (e: Exception) {
                println("Console WebSocket failed: ${e.message}")
            } finally {
                session = null
            }
        }
    }

    private suspend fun processSession(session: DefaultClientWebSocketSession) {
        for (frame in session.incoming) {
            if (frame is Frame.Text) {
                val line = Json.decodeFromString<ConsoleLine>(frame.readText())
                _lines.update { it + line }
            }
        }
    }

    fun runCommand(command: String) {
        viewModelScope.launch {
            session?.send(Frame.Text(command))
        }
    }
}
