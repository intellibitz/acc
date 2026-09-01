package cc.thevar.acc.service

import cc.thevar.acc.protocol.WorkerState
import cc.thevar.acc.protocol.WorkerStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class SupervisorService(private val projectRoot: File) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val workers = ConcurrentHashMap<String, ManagedWorker>()
    
    private val _workerStates = MutableStateFlow<List<WorkerState>>(emptyList())
    val workerStates = _workerStates.asStateFlow()

    init {
        val venvPython = File(projectRoot, ".venv/bin/python3").absolutePath
        val pythonCmd = if (File(venvPython).exists()) venvPython else "python3"

        // Register core workers
        registerWorker("SYSTEM_BRIDGE", listOf(pythonCmd, "brain/system_bridge.py"), restartPolicy = true)
        registerWorker("FRONTEND_BUILDER", listOf("./gradlew", ":frontend:web:wasmJsBrowserDistribution", "--quiet"), restartPolicy = false)
        
        // Start core workers automatically
        startWorker("SYSTEM_BRIDGE")

        // Start monitoring loop
        scope.launch {
            while (isActive) {
                checkWorkers()
                delay(2000)
            }
        }
    }

    fun getWorkerOutput(name: String): Flow<String> = flow {
        val worker = workers[name] ?: return@flow
        var lastEmitted = ""
        while (currentCoroutineContext().isActive) {
            val msg = worker.lastMsg
            if (msg.isNotEmpty() && msg != lastEmitted) {
                emit(msg)
                lastEmitted = msg
            }
            delay(100)
        }
    }

    fun registerWorker(name: String, command: List<String>, restartPolicy: Boolean, env: Map<String, String> = emptyMap()) {
        workers[name] = ManagedWorker(name, command, restartPolicy, env)
    }

    fun spawnAgent(agentName: String, model: String, apiBase: String? = null) {
        val venvPython = File(projectRoot, ".venv/bin/python3").absolutePath
        val pythonCmd = if (File(venvPython).exists()) venvPython else "python3"
        
        val env = mutableMapOf("ACC_MODEL" to model)
        if (apiBase != null) env["ACC_API_BASE"] = apiBase
        
        registerWorker("AGENT_$agentName", listOf(pythonCmd, "brain/agent_bridge.py"), restartPolicy = true, env = env)
        startWorker("AGENT_$agentName")
    }

    fun startEngine(provider: String) {
        val useLocal = System.getenv("ACC_USE_LOCAL_ENGINE") == "true"
        if (useLocal && provider == "ollama") {
            logger.info("Using local Ollama engine as requested (ACC_USE_LOCAL_ENGINE=true)")
            return
        }

        scope.launch {
            try {
                logger.info("Starting engine provider: {}", provider)
                val process = ProcessBuilder("docker", "compose", "up", "-d", provider)
                    .directory(projectRoot)
                    .redirectErrorStream(true)
                    .start()
                
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { logger.info("[Docker] {}", it) }
                }
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    logger.info("Engine provider {} started.", provider)
                } else {
                    logger.warn("Engine provider {} failed with exit code {}", provider, exitCode)
                }
            } catch (e: Exception) {
                logger.error("Failed to start engine {}: {}", provider, e.message, e)
            }
        }
    }

    fun startWorker(name: String) {
        workers[name]?.start()
    }

    fun stopAll() {
        workers.values.forEach { it.stop() }
    }

    override fun close() {
        logger.info("Closing SupervisorService, stopping all workers...")
        stopAll()
        scope.cancel("SupervisorService closing")
    }

    private fun checkWorkers() {
        val states = workers.values.map { it.updateState() }
        _workerStates.value = states
        
        // Auto-heal
        workers.values.forEach { worker ->
            if (worker.shouldRestart()) {
                logger.warn("Worker {} crashed. Restarting...", worker.name)
                worker.start()
            }
        }
    }

    private inner class ManagedWorker(
        val name: String,
        val command: List<String>,
        val restartPolicy: Boolean,
        val env: Map<String, String> = emptyMap()
    ) {
        private var process: Process? = null
        var status = WorkerStatus.STOPPED
        var restarts = 0
        var lastMsg = ""
        var lastError: String? = null
        private var logJob: Job? = null

        fun start() {
            if (status == WorkerStatus.RUNNING || status == WorkerStatus.STARTING) return
            
            status = WorkerStatus.STARTING
            lastError = null
            
            try {
                val pb = ProcessBuilder(command).directory(projectRoot).redirectErrorStream(true)
                pb.environment().putAll(env)
                process = pb.start()
                status = WorkerStatus.RUNNING
                
                // Capture logs
                logJob?.cancel()
                logJob = scope.launch {
                    process?.inputStream?.bufferedReader()?.useLines { lines ->
                        lines.forEach { line ->
                            lastMsg = line.trim()
                            if (lastMsg.length > 100) lastMsg = lastMsg.take(97) + "..."
                        }
                    }
                }
            } catch (e: Exception) {
                status = WorkerStatus.CRASHED
                lastError = e.message
            }
        }

        fun stop() {
            logJob?.cancel()
            process?.let {
                logger.info("Stopping worker {}: Sending SIGTERM...", name)
                it.destroy()
                if (!it.waitFor(5, TimeUnit.SECONDS)) {
                    logger.warn("Worker {} failed to stop gracefully. Sending SIGKILL...", name)
                    it.destroyForcibly()
                }
            }
            status = WorkerStatus.STOPPED
        }

        fun shouldRestart(): Boolean {
            return restartPolicy && status == WorkerStatus.CRASHED
        }

        fun updateState(): WorkerState {
            val p = process
            if (p != null && !p.isAlive) {
                val exitCode = p.exitValue()
                if (exitCode == 0) {
                    status = WorkerStatus.COMPLETED
                } else {
                    if (status != WorkerStatus.STOPPED && status != WorkerStatus.COMPLETED) {
                        status = WorkerStatus.CRASHED
                        restarts++
                        lastError = "Exit Code $exitCode"
                    }
                }
            }
            
            return WorkerState(
                name = name,
                status = status,
                pid = try { p?.pid()?.toInt() } catch (e: Exception) { null },
                restarts = restarts,
                lastMsg = if (status == WorkerStatus.RUNNING) lastMsg else (lastError ?: ""),
                error = lastError
            )
        }
    }
}
