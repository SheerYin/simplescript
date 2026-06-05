package me.yin.simplescript.script

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.yin.simplescript.SimpleScript
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic

class SimpleScriptManager(
    private val plugin: SimpleScript,
    private val scriptService: SimpleScriptService,
    private val logger: Logger
) {
    private val scriptDirectory: Path = plugin.dataPath.resolve(SCRIPT_DIRECTORY).normalize()
    private val closeMap = ConcurrentHashMap<String, MutableList<() -> Unit>>()
    private val mutex = Mutex()

    suspend fun reload(): Int = mutex.withLock {
        closeRunningScripts()
        Files.createDirectories(scriptDirectory)

        val scripts = listScriptFiles()
        try {
            for (scriptPath in scripts) {
                val scriptId = scriptId(scriptPath)
                closeScript(scriptId)
                evaluateScript(scriptId, scriptPath)
                logger.info("Started script {} from {}", scriptId, scriptPath)
            }
        } catch (exception: Exception) {
            closeRunningScripts()
            throw exception
        }

        scripts.size
    }

    suspend fun reloadOne(scriptId: String): ReloadResult = mutex.withLock {
        if (!closeMap.containsKey(scriptId)) {
            return@withLock ReloadResult.NOT_LOADED
        }
        val scriptPath = scriptDirectory.resolve("$scriptId.$SIMPLE_SCRIPT_EXTENSION")
        if (!Files.isRegularFile(scriptPath)) {
            return@withLock ReloadResult.FILE_MISSING
        }

        closeScript(scriptId)
        evaluateScript(scriptId, scriptPath)
        logger.info("Started script {} from {}", scriptId, scriptPath)
        ReloadResult.RELOADED
    }

    suspend fun loadOne(scriptId: String): LoadResult = mutex.withLock {
        if (closeMap.containsKey(scriptId)) {
            return@withLock LoadResult.ALREADY_LOADED
        }
        val scriptPath = scriptDirectory.resolve("$scriptId.$SIMPLE_SCRIPT_EXTENSION")
        if (!Files.isRegularFile(scriptPath)) {
            return@withLock LoadResult.NOT_FOUND
        }

        evaluateScript(scriptId, scriptPath)
        logger.info("Started script {} from {}", scriptId, scriptPath)
        LoadResult.LOADED
    }

    suspend fun unloadOne(scriptId: String): Boolean = mutex.withLock {
        if (!closeMap.containsKey(scriptId)) {
            return@withLock false
        }
        closeScript(scriptId)
        true
    }

    fun loadedScriptIds(): Set<String> = closeMap.keys.toSet()

    fun availableScriptIds(): List<String> {
        if (!Files.isDirectory(scriptDirectory)) {
            return emptyList()
        }
        return listScriptFiles().map { scriptId(it) }
    }

    suspend fun close() {
        mutex.withLock {
            closeRunningScripts()
        }
    }

    private fun listScriptFiles(): List<Path> {
        return Files.list(scriptDirectory).use { paths ->
            paths
                .filter { path -> Files.isRegularFile(path) }
                .filter { path -> path.fileName.toString().endsWith(".$SIMPLE_SCRIPT_EXTENSION") }
                .sorted()
                .toList()
        }
    }

    private fun evaluateScript(scriptId: String, scriptPath: Path) {
        val scope = SimpleScriptScope(
            id = scriptId,
            plugin = plugin,
            closeHandler = ::onClose
        )

        val result = scriptService.evaluate(scriptPath, scope)
        when (result) {
            is ResultWithDiagnostics.Success -> logReports(result.reports)
            is ResultWithDiagnostics.Failure -> {
                logReports(result.reports)
                throw IllegalStateException("Failed to evaluate script $scriptPath")
            }
        }
    }

    private fun closeRunningScripts() {
        val scriptIds = closeMap.keys.toList().asReversed()
        for (scriptId in scriptIds) {
            closeScript(scriptId)
        }
    }

    private fun closeScript(scriptId: String) {
        val closeHandlers = closeMap.remove(scriptId)?.asReversed()?.toList() ?: return
        for (closeHandler in closeHandlers) {
            try {
                closeHandler()
            } catch (exception: Exception) {
                logger.error("Failed to close script {}", scriptId, exception)
            }
        }
    }

    private fun onClose(scriptId: String, block: () -> Unit) {
        closeMap.getOrPut(scriptId) { mutableListOf() } += block
    }

    private fun scriptId(scriptPath: Path): String {
        return scriptPath.fileName.toString().removeSuffix(".$SIMPLE_SCRIPT_EXTENSION")
    }

    private fun logReports(reports: List<ScriptDiagnostic>) {
        for (report in reports) {
            val message = "[${report.severity}] ${report.location ?: ""} ${report.message}"
            when (report.severity) {
                ScriptDiagnostic.Severity.ERROR,
                ScriptDiagnostic.Severity.FATAL -> logger.error(message)

                ScriptDiagnostic.Severity.WARNING -> logger.warn(message)

                ScriptDiagnostic.Severity.INFO -> logger.info(message)

                ScriptDiagnostic.Severity.DEBUG -> logger.debug(message)
            }
        }
    }

    companion object {
        const val SCRIPT_DIRECTORY = "scripts"
    }
}

enum class LoadResult {
    LOADED,
    ALREADY_LOADED,
    NOT_FOUND
}

enum class ReloadResult {
    RELOADED,
    NOT_LOADED,
    FILE_MISSING
}
