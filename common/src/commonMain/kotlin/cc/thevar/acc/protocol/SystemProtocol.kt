package cc.thevar.acc.protocol

import kotlinx.serialization.Serializable

@Serializable
data class GpuStats(
    val utilization: Float,
    val memoryUsed: Float,
    val memoryTotal: Float,
    val temperature: Float,
    val power: Float,
    val active: Boolean
)

@Serializable
data class SystemStats(
    val cpuUtilization: Float,
    val ramUsed: Float,
    val ramTotal: Float,
    val gpu: GpuStats,
    val diskUsage: String
)

@Serializable
data class ModelStatus(
    val name: String,
    val isInstalled: Boolean,
    val isRunning: Boolean,
    val type: String // "PRIV" or "COMM"
)

@Serializable
data class ModelManifest(
    val name: String,
    val provider: String = "ollama",
    val repo: String,
    val filePattern: String,
    val tier: String = "FAST",
    val quant: String = "Q4_K_M",
    val superpower: String = "General",
    val isPrivate: Boolean = false
)

@Serializable
enum class ProvisioningStage {
    IDLE, SCANNING, DOWNLOADING, MERGING, REGISTERING, COMPLETED, ERROR
}

@Serializable
data class ProvisioningUpdate(
    val modelName: String,
    val stage: ProvisioningStage,
    val progress: Float = 0f,
    val speed: String = "",
    val message: String = ""
)

@Serializable
data class SystemState(
    val stats: SystemStats,
    val fleet: List<ModelStatus>,
    val provisioning: List<ProvisioningUpdate> = emptyList(),
    val partialDownloads: List<String> = emptyList(),
    val proxyOnline: Boolean,
    val statusMsg: String
)

@Serializable
data class ConsoleLine(
    val text: String,
    val type: String = "INFO" // "INFO", "ERROR", "SUCCESS", "COMMAND"
)
