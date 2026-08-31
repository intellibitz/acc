package cc.thevar.acc.service

import cc.thevar.acc.protocol.WorkerState
import cc.thevar.acc.protocol.WorkerStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class SupervisorService(private val projectRoot: File) {
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

    private fun registerWorker(name: String, command: List<String>, restartPolicy: Boolean) {
        workers[name] = ManagedWorker(name, command, restartPolicy)
    }

    fun startWorker(name: String) {
        workers[name]?.start()
    }

    fun stopAll() {
        workers.values.forEach { it.stop() }
    }

    private fun checkWorkers() {
        val states = workers.values.map { it.updateState() }
        _workerStates.value = states
        
        // Auto-heal
        workers.values.forEach { worker ->
            if (worker.shouldRestart()) {
                println("[Supervisor] Worker ${worker.name} crashed. Restarting...")
                worker.start()
            }
        }
    }

    private inner class ManagedWorker(
        val name: String,
        val command: List<String>,
        val restartPolicy: Boolean
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
            process?.destroy()
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
