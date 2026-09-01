package cc.thevar.acc.service

import io.ktor.server.websocket.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class SessionManager {
    val uiSessions = Collections.newSetFromMap(ConcurrentHashMap<DefaultWebSocketServerSession, Boolean>())
    val systemSessions = Collections.newSetFromMap(ConcurrentHashMap<DefaultWebSocketServerSession, Boolean>())
    var systemStatusMsg = "Initializing Acc..."
}
