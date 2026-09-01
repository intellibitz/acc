package cc.thevar.acc.service

import cc.thevar.acc.protocol.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File

class MonitoringService(
    private val projectRoot: File,
    private val supervisorService: SupervisorService,
    private val provisioningService: ProvisioningService,
    private val sessionManager: SessionManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(applicationScope: CoroutineScope) {
        val initSentinel = File(projectRoot, "data/.initialized")
        if (!initSentinel.exists()) {
            applicationScope.launch(Dispatchers.IO) {
                try {
                    sessionManager.systemStatusMsg = "Initializing fleet and registry..."
                    val fleetJson = File(projectRoot, "config/fleet.json")
                    if (!fleetJson.exists()) {
                        File(projectRoot, "config").mkdirs()
                        val defaultFleet = """{"models": [{"provider": "ollama", "name": "phi3", "repo": "microsoft/Phi-3-mini-4k-instruct-gguf", "filePattern": "*Q4_K_M.gguf", "tier": "FAST", "quant": "Q4_K_M", "isPrivate": false}]}"""
                        fleetJson.writeText(defaultFleet)
                    }
                    
                    File(projectRoot, "data/backups").mkdirs()
                    File(projectRoot, "logs").mkdirs()
                    File(projectRoot, ".cache").mkdirs()
                    File(projectRoot, "registry").mkdirs()

                    initSentinel.parentFile.mkdirs()
                    initSentinel.createNewFile()
                    sessionManager.systemStatusMsg = "Acc Ready."
                } catch (e: Exception) {
                    sessionManager.systemStatusMsg = "Initialization Error: ${e.message}"
                }
            }
        } else {
            sessionManager.systemStatusMsg = "Acc Ready."
        }

        // Metrics streaming
        applicationScope.launch(Dispatchers.IO) {
            supervisorService.getWorkerOutput("SYSTEM_BRIDGE").collect { output ->
                try {
                    if (output.startsWith("{") && output.endsWith("}")) {
                        val bridgeData = Json.parseToJsonElement(output).jsonObject

                        if (bridgeData.containsKey("error")) {
                            sessionManager.systemStatusMsg = bridgeData["error"]?.jsonPrimitive?.content ?: "Bridge Error"
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
                            statusMsg = sessionManager.systemStatusMsg
                        )

                        broadcast(Frame.Text(Json.encodeToString(fullState)), sessionManager.systemSessions + sessionManager.uiSessions)
                    }
                } catch (e: Exception) { }
            }
        }

        // Provisioning updates
        applicationScope.launch(Dispatchers.IO) {
            provisioningService.updates.collect { updates ->
                broadcast(Frame.Text(Json.encodeToString(updates.values.toList())), sessionManager.uiSessions)
            }
        }

        // Supervisor updates
        applicationScope.launch(Dispatchers.IO) {
            supervisorService.workerStates.collect { states ->
                broadcast(Frame.Text(Json.encodeToString(states)), sessionManager.uiSessions)
            }
        }
    }

    private fun broadcast(frame: Frame, sessions: Set<DefaultWebSocketServerSession>) {
        sessions.forEach { session ->
            session.launch {
                try {
                    session.send(frame)
                } catch (e: Exception) { }
            }
        }
    }
}
