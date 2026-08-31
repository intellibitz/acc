package cc.thevar.acc.service

import cc.thevar.acc.protocol.WorkerState
import cc.thevar.acc.protocol.WorkerStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class SupervisorService(private val projectRoot: File) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val workers = ConcurrentHashMap<String, ManagedWorker>()
    
    private val _workerStates = MutableStateFlow<List<WorkerState>>(emptyList())
    val workerStates = _workerStates.asStateFlow()

    init {
        // Register core workers
        registerWorker("SYSTEM_BRIDGE", listOf("python3", "brain/system_bridge.py"), restartPolicy = true)
        registerWorker("FRONTEND_BUILDER", listOf("./gradlew", ":frontend:web:wasmJsBrowserDistribution", "--quiet"), restartPolicy = false)
        
        // Start monitoring loop
        scope.launch {
            while (isActive) {
                checkWorkers()
                delay(5000)
            }
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
        private var status = WorkerStatus.STOPPED
        private var restarts = 0
        private var lastError: String? = null

        fun start() {
            if (status == WorkerStatus.RUNNING || status == WorkerStatus.STARTING) return
            
            status = WorkerStatus.STARTING
            try {
                process = ProcessBuilder(command)
                    .directory(projectRoot)
                    .redirectErrorStream(true)
                    .start()
                status = WorkerStatus.RUNNING
            } catch (e: Exception) {
                status = WorkerStatus.CRASHED
                lastError = e.message
            }
        }

        fun stop() {
            process?.destroy()
            status = WorkerStatus.STOPPED
        }

        fun shouldRestart(): Boolean {
            return restartPolicy && status == WorkerStatus.CRASHED
        }

        fun updateState(): WorkerState {
            if (process != null && !process!!.isAlive) {
                if (process!!.exitValue() == 0) {
                    status = WorkerStatus.COMPLETED
                } else {
                    if (status != WorkerStatus.STOPPED) {
                        status = WorkerStatus.CRASHED
                        restarts++
                    }
                }
            }
            
            return WorkerState(
                name = name,
                status = status,
                pid = try { process?.pid()?.toInt() } catch (e: Exception) { null },
                restarts = restarts,
                error = lastError
            )
        }
    }
}
