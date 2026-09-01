package cc.thevar.acc.service

import cc.thevar.acc.protocol.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ProvisioningService(
    private val projectRoot: File,
    private val fleetManager: FleetManager,
    private val client: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    },
    private val ollamaHost: String = System.getenv("OLLAMA_HOST") ?: "http://localhost:11434"
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _updates = MutableStateFlow<Map<String, ProvisioningUpdate>>(emptyMap())
    val updates = _updates.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    
    private val json = Json { ignoreUnknownKeys = true }

    fun provisionAll() {
        val fleet = fleetManager.getFleet()
        fleet.forEach { model ->
            startProvisioning(model)
        }
    }

    fun startProvisioning(model: ModelManifest) {
        if (activeJobs.containsKey(model.name)) return

        val job = scope.launch {
            try {
                updateStatus(model.name, ProvisioningStage.SCANNING, 0f, message = "Checking remote status...")
                
                val regDir = File(projectRoot, "registry/${model.name}").apply { mkdirs() }
                val shaFile = File(regDir, "last_sync_sha")
                val localSha = if (shaFile.exists()) shaFile.readText().trim() else ""
                
                val remoteSha = try {
                    val response: HttpResponse = client.get("https://huggingface.co/api/models/${model.repo}")
                    if (response.status.value == 200) {
                        val body = response.bodyAsText()
                        val jsonElement = json.parseToJsonElement(body).jsonObject
                        jsonElement["sha"]?.jsonPrimitive?.content ?: ""
                    } else ""
                } catch (e: Exception) {
                    logger.warn("Failed to fetch remote SHA for {}: {}", model.name, e.message)
                    ""
                }

                val isInstalled = try {
                    val response: HttpResponse = client.get("$ollamaHost/api/tags")
                    if (response.status.value == 200) {
                        val body = response.bodyAsText()
                        val jsonElement = json.parseToJsonElement(body).jsonObject
                        val models = jsonElement["models"]?.jsonArray ?: JsonArray(emptyList())
                        models.any { it.jsonObject["name"]?.jsonPrimitive?.content?.startsWith(model.name) ?: false }
                    } else false
                } catch (e: Exception) {
                    logger.warn("Failed to check Ollama status for {}: {}", model.name, e.message)
                    false
                }

                if (remoteSha != localSha || !isInstalled) {
                    updateStatus(model.name, ProvisioningStage.DOWNLOADING, 0.1f, message = "Downloading model...")
                    
                    val dlDir = File(projectRoot, ".cache/${model.name}").apply { mkdirs() }
                    
                    val process = ProcessBuilder(
                        "huggingface-cli", "download", model.repo, 
                        "--include", model.filePattern,
                        "--local-dir", dlDir.absolutePath,
                        "--max-workers", "8"
                    ).directory(projectRoot)
                    .redirectErrorStream(true)
                    .start()
                    
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (line.contains("%") || line.contains("MB/s")) {
                                updateStatus(model.name, ProvisioningStage.DOWNLOADING, message = line.trim())
                            }
                        }
                    }
                    
                    val exitCode = process.waitFor()
                    if (exitCode != 0) {
                        updateStatus(model.name, ProvisioningStage.ERROR, message = "Download failed (Code $exitCode)")
                        return@launch
                    }

                    updateStatus(model.name, ProvisioningStage.REGISTERING, 0.9f, message = "Building Ollama model...")
                    registerInOllama(model, regDir, dlDir)
                    
                    if (remoteSha.isNotEmpty()) {
                        shaFile.writeText(remoteSha)
                    }
                    
                    updateStatus(model.name, ProvisioningStage.COMPLETED, 1.0f, message = "Ready.")
                    // Cleanup download dir after success
                    dlDir.deleteRecursively()
                } else {
                    updateStatus(model.name, ProvisioningStage.COMPLETED, 1.0f, message = "Already current.")
                }
            } catch (e: Exception) {
                updateStatus(model.name, ProvisioningStage.ERROR, message = e.message ?: "Unknown error")
            } finally {
                activeJobs.remove(model.name)
            }
        }
        activeJobs[model.name] = job
    }

    private fun updateStatus(
        name: String, 
        stage: ProvisioningStage, 
        progress: Float = 0f, 
        speed: String = "", 
        message: String = ""
    ) {
        val current = _updates.value.toMutableMap()
        current[name] = ProvisioningUpdate(name, stage, progress, speed, message)
        _updates.value = current
    }

    private suspend fun registerInOllama(model: ModelManifest, optDir: File, dlDir: File) {
        val ggufFile = dlDir.walkTopDown().find { it.extension == "gguf" } ?: throw Exception("GGUF not found")
        
        // Map path for the Ollama container (using the shared volume path)
        val ollamaGgufPath = "/app/.cache/${model.name}/${ggufFile.name}"
        
        val threads = Runtime.getRuntime().availableProcessors().let { if (it > 4) it - 4 else it }
        val gpuLayers = calculateGpuLayers(model.name)
        
        val paramFile = File(optDir, "user_params")
        val userParams = if (paramFile.exists()) paramFile.readText() else "PARAMETER temperature 0.7\nPARAMETER top_p 0.9"
        
        val modelfileContent = """
            FROM $ollamaGgufPath
            PARAMETER num_gpu $gpuLayers
            PARAMETER num_thread $threads
            PARAMETER num_ctx 32768
            $userParams
            SYSTEM "You are the Master Architect, an elite Android Lead Engineer."
        """.trimIndent()
        
        updateStatus(model.name, ProvisioningStage.REGISTERING, 0.92f, message = "Sending Modelfile to Ollama...")

        try {
            val response: HttpResponse = client.post("$ollamaHost/api/create") {
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("name", model.name)
                    put("modelfile", modelfileContent)
                    put("stream", true)
                })
            }

            if (response.status.value == 200) {
                response.bodyAsText().lines().forEach { line ->
                    if (line.isNotBlank()) {
                        val status = json.parseToJsonElement(line).jsonObject["status"]?.jsonPrimitive?.content
                        if (status != null) {
                            updateStatus(model.name, ProvisioningStage.REGISTERING, 0.95f, message = status)
                        }
                    }
                }
            } else {
                throw Exception("Ollama API Error: ${response.status}")
            }
        } catch (e: Exception) {
            throw Exception("Ollama registration failed: ${e.message}")
        }
    }

    private fun calculateGpuLayers(name: String): Int {
        return when {
            name.contains("70b", ignoreCase = true) -> 20
            name.contains("8x22b", ignoreCase = true) || name.contains("command-r-plus", ignoreCase = true) -> 10
            else -> 99
        }
    }

    fun pruneFleet() {
        scope.launch {
            try {
                val fleet = fleetManager.getFleet().map { it.name }.toSet()
                
                val response: HttpResponse = client.get("$ollamaHost/api/tags")
                if (response.status.value == 200) {
                    val body = response.bodyAsText()
                    val jsonElement = json.parseToJsonElement(body).jsonObject
                    val installed = jsonElement["models"]?.jsonArray?.map { it.jsonObject["name"]?.jsonPrimitive?.content?.split(":")?.first() ?: "" } ?: emptyList()
                    
                    installed.forEach { model ->
                        if (model.isNotEmpty() && !fleet.contains(model)) {
                            logger.info("[Prune] Removing unmanaged model: {}", model)
                            client.delete("$ollamaHost/api/delete") {
                                contentType(io.ktor.http.ContentType.Application.Json)
                                setBody(buildJsonObject { put("name", model) })
                            }
                        }
                    }
                }
                
                File(projectRoot, ".cache").listFiles()?.forEach { it.deleteRecursively() }
                logger.info("[Prune] Fleet pruned and disk space reclaimed.")
            } catch (e: Exception) {
                logger.error("[Prune] Error during prune: {}", e.message, e)
            }
        }
    }

    fun backupConfig() {
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val backupDir = File(projectRoot, "data/backups/$ts").apply { mkdirs() }
        
        try {
            File(projectRoot, "config").listFiles()?.forEach { file ->
                if (file.isFile) file.copyTo(File(backupDir, file.name))
            }
            File(projectRoot, "registry").copyRecursively(File(backupDir, "registry"), overwrite = true)
            logger.info("[Backup] Configuration backed up to {}", backupDir.absolutePath)
        } catch (e: Exception) {
            logger.error("[Backup] Error: {}", e.message, e)
        }
    }

    fun autoScale() {
        scope.launch {
            try {
                var vram = 0
                try {
                    val process = ProcessBuilder("nvidia-smi", "--query-gpu=memory.total", "--format=csv,noheader,nounits").start()
                    vram = process.inputStream.bufferedReader().readLine()?.trim()?.toInt() ?: 0
                } catch (e: Exception) {
                    logger.debug("[AutoScale] nvidia-smi failed or not present: {}", e.message)
                }

                val ram = (Runtime.getRuntime().maxMemory() / (1024 * 1024 * 1024)).toInt() // GB approximate
                
                val tier = when {
                    vram >= 40000 -> "ELITE"
                    vram >= 16000 -> "STRONG"
                    else -> "FAST"
                }
                logger.info("[AutoScale] VRAM: {}MB | RAM: {}GB | Target Tier: {}", vram, ram, tier)
            } catch (e: Exception) {
                logger.error("[AutoScale] Error: {}", e.message, e)
            }
        }
    }

    override fun close() {
        logger.info("Closing ProvisioningService, cancelling active jobs...")
        activeJobs.values.forEach { it.cancel() }
        scope.cancel("ProvisioningService closing")
        client.close()
    }
}
