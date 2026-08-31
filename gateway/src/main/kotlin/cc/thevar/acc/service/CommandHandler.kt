package cc.thevar.acc.service

import cc.thevar.acc.protocol.ConsoleLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class CommandHandler(
    private val projectRoot: File,
    private val provisioningService: ProvisioningService
) {
    private val allowedExternalCommands = setOf("up", "tune-hw", "sync", "auto-scale", "benchmark", "prune", "stop", "update", "refresh", "setup")

    fun handleCommand(commandLine: String): Flow<ConsoleLine> = flow {
        val parts = commandLine.trim().split(" ")
        val baseCommand = parts.firstOrNull() ?: return@flow
        val args = parts.drop(1)

        emit(ConsoleLine("$ acc $commandLine", "COMMAND"))

        when (baseCommand) {
            "prune" -> {
                provisioningService.pruneFleet()
                emit(ConsoleLine("Fleet pruning initiated.", "INFO"))
                emit(ConsoleLine("Task Finished", "SUCCESS"))
            }
            "backup" -> {
                provisioningService.backupConfig()
                emit(ConsoleLine("Configuration backup initiated.", "INFO"))
                emit(ConsoleLine("Task Finished", "SUCCESS"))
            }
            "auto-scale" -> {
                provisioningService.autoScale()
                emit(ConsoleLine("Hardware-aware auto-scaling initiated.", "INFO"))
                emit(ConsoleLine("Task Finished", "SUCCESS"))
            }
            "sync" -> {
                provisioningService.provisionAll()
                emit(ConsoleLine("Fleet synchronization started.", "INFO"))
                emit(ConsoleLine("Task Finished", "SUCCESS"))
            }
            in allowedExternalCommands -> {
                executeExternalCommand(baseCommand, args).collect { emit(it) }
            }
            else -> {
                emit(ConsoleLine("Error: Command '$baseCommand' is not allowed or unrecognized.", "ERROR"))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun executeExternalCommand(command: String, args: List<String>): Flow<ConsoleLine> = flow {
        try {
            val cmdList = mutableListOf("python3", "acc.py", command)
            cmdList.addAll(args)
            
            val process = ProcessBuilder(cmdList)
                .directory(projectRoot)
                .redirectErrorStream(true)
                .start()

            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    emit(ConsoleLine(line, "INFO"))
                }
            }
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                emit(ConsoleLine("Task Finished", "SUCCESS"))
            } else {
                emit(ConsoleLine("Command failed with exit code $exitCode", "ERROR"))
            }
        } catch (e: Exception) {
            emit(ConsoleLine("Execution Error: ${e.message}", "ERROR"))
        }
    }
}
