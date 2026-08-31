package cc.thevar.acc.service

import cc.thevar.acc.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ProvisioningService(
    private val projectRoot: File,
    private val fleetManager: FleetManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _updates = MutableStateFlow<Map<String, ProvisioningUpdate>>(emptyMap())
    val updates = _updates.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()

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
                updateStatus(model.name, ProvisioningStage.SCANNING, 0f, message = "Checking status...")
                
                // 1. Check if update needed (mocking SHA check for now)
                val needsUpdate = true // Logic from provisioner.sh:60
                
                if (needsUpdate) {
                    updateStatus(model.name, ProvisioningStage.DOWNLOADING, 0.1f, message = "Downloading via hf...")
                    
                    // Execute hf download
                    val process = ProcessBuilder(
                        "hf", "download", model.repo, 
                        "--include", model.filePattern,
                        "--local-dir", "${projectRoot}/downloads/${model.name}",
                        "--max-workers", "8"
                    ).directory(projectRoot)
                    .redirectErrorStream(true)
                    .start()
                    
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            // Update status with the latest download line (contains speed/progress)
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
                    
                    // Generate Modelfile and run 'ollama create'
                    registerInOllama(model)
                    
                    updateStatus(model.name, ProvisioningStage.COMPLETED, 1.0f, message = "Ready.")
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

    private suspend fun registerInOllama(model: ModelManifest) {
        val optDir = File(projectRoot, "optimizations/${model.name}").apply { mkdirs() }
        val dlDir = File(projectRoot, "downloads/${model.name}")
        val ggufFile = dlDir.walkTopDown().find { it.extension == "gguf" } ?: throw Exception("GGUF not found")
        
        val threads = Runtime.getRuntime().availableProcessors() - 2
        val gpuLayers = calculateGpuLayers(model.name)
        
        val modelfile = """
            FROM ${ggufFile.absolutePath}
            PARAMETER num_gpu $gpuLayers
            PARAMETER num_thread $threads
            PARAMETER num_ctx 32768
            SYSTEM "You are the Master Architect, an elite Android Lead Engineer."
        """.trimIndent()
        
        File(optDir, "Modelfile").writeText(modelfile)
        
        val process = ProcessBuilder("ollama", "create", model.name, "-f", "${optDir}/Modelfile")
            .directory(projectRoot)
            .redirectErrorStream(true)
            .start()
        
        // Stream the build logs to the status message
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                // Filter out the noise and update progress
                if (line.isNotBlank()) {
                    updateStatus(model.name, ProvisioningStage.REGISTERING, 0.95f, message = line.trim())
                }
            }
        }
        
        val exitCode = process.waitFor()
        if (exitCode != 0) throw Exception("Ollama build failed with exit code $exitCode")
    }

    private fun calculateGpuLayers(name: String): Int {
        return when {
            name.contains("70b", ignoreCase = true) -> 20
            name.contains("8x22b", ignoreCase = true) -> 10
            else -> 99
        }
    }
}
