package cc.thevar.acc.service

import cc.thevar.acc.protocol.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.time.Duration.Companion.seconds

class MonitoringService(
    private val projectRoot: File,
    private val supervisorService: SupervisorService,
    private val provisioningService: ProvisioningService,
    private val fleetManager: FleetManager,
    private val systemMetricsService: SystemMetricsService,
    private val sessionManager: SessionManager,
    private val client: HttpClient,
    private val ollamaHost: String = System.getenv("OLLAMA_HOST") ?: "http://localhost:11434"
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }

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
                    logger.error("Initialization error: {}", e.message, e)
                }
            }
        } else {
            sessionManager.systemStatusMsg = "Acc Ready."
        }

        // Metrics streaming
        applicationScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val stats = systemMetricsService.getSystemStats()
                    
                    val installedModels = try {
                        val response = client.get("$ollamaHost/api/tags")
                        if (response.status.value == 200) {
                            val body = response.bodyAsText()
                            json.parseToJsonElement(body).jsonObject["models"]?.jsonArray
                                ?.map { it.jsonObject["name"]?.jsonPrimitive?.content?.split(":")?.first() ?: "" }
                                ?.toSet() ?: emptySet()
                        } else emptySet()
                    } catch (e: Exception) { emptySet() }

                    val runningModels = try {
                        val response = client.get("$ollamaHost/api/ps")
                        if (response.status.value == 200) {
                            val body = response.bodyAsText()
                            json.parseToJsonElement(body).jsonObject["models"]?.jsonArray
                                ?.map { it.jsonObject["name"]?.jsonPrimitive?.content?.split(":")?.first() ?: "" }
                                ?.toSet() ?: emptySet()
                        } else emptySet()
                    } catch (e: Exception) { emptySet() }

                    val fleet = fleetManager.getFleet().map { model ->
                        ModelStatus(
                            name = model.name,
                            provider = model.provider,
                            isInstalled = model.provider != "ollama" || installedModels.contains(model.name),
                            isRunning = model.provider != "ollama" || runningModels.contains(model.name),
                            type = if (model.isPrivate) "PRIV" else "COMM"
                        )
                    }

                    val fullState = SystemState(
                        stats = stats,
                        fleet = fleet,
                        workers = supervisorService.workerStates.value,
                        provisioning = provisioningService.updates.value.values.toList(),
                        proxyOnline = false,
                        engineOnline = installedModels.isNotEmpty() || runningModels.isNotEmpty() || true,
                        statusMsg = sessionManager.systemStatusMsg
                    )

                    broadcast(Frame.Text(Json.encodeToString(fullState)), sessionManager.systemSessions + sessionManager.uiSessions)
                } catch (e: Exception) {
                    logger.debug("Metrics stream error: {}", e.message)
                }
                delay(2.seconds)
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
                } catch (e: Exception) {
                    logger.debug("Failed to broadcast frame to session: {}", e.message)
                }
            }
        }
    }

    override fun close() {
        logger.info("Closing MonitoringService...")
        scope.cancel("MonitoringService closing")
    }
}
