package me.yin.simplescript.script

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.yin.simplescript.SimpleScript
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic

class SimpleScriptManager(
    private val plugin: SimpleScript,
    private val scriptService: SimpleScriptService,
    private val logger: Logger
) {
    private val scriptDirectory: Path = plugin.dataPath.resolve(SCRIPT_DIRECTORY).normalize()
    val closeHandlers = ConcurrentHashMap<String, CopyOnWriteArrayList<() -> Unit>>()
    private val mutex = Mutex()

    suspend fun load(): LoadSummary = mutex.withLock {
        Files.createDirectories(scriptDirectory)

        val scripts = listScriptFiles()
        var loaded = 0
        val failed = mutableListOf<String>()
        for (scriptPath in scripts) {
            val scriptId = scriptId(scriptPath)

            try {
                if (closeHandlers.putIfAbsent(scriptId, CopyOnWriteArrayList()) != null) {
                    continue
                }
                evaluateScript(scriptId, scriptPath)
                loaded += 1
                logger.info("Started script {} from {}", scriptId, scriptPath)
            } catch (exception: Exception) {
                failed += scriptId
                closeScript(scriptId)
                logger.error("Failed to start script {} from {}", scriptId, scriptPath, exception)
            }
        }

        LoadSummary(
            loaded = loaded,
            failed = failed
        )
    }

    suspend fun load(scriptId: String): LoadResult = mutex.withLock {
        if (closeHandlers.containsKey(scriptId)) {
            return@withLock LoadResult.ALREADY_LOADED
        }
        val scriptPath = scriptDirectory.resolve("$scriptId.$SIMPLE_SCRIPT_EXTENSION")
        if (!Files.isRegularFile(scriptPath)) {
            return@withLock LoadResult.NOT_FOUND
        }

        try {
            if (closeHandlers.putIfAbsent(scriptId, CopyOnWriteArrayList()) != null) {
                return@withLock LoadResult.ALREADY_LOADED
            }
            evaluateScript(scriptId, scriptPath)
            logger.info("Started script {} from {}", scriptId, scriptPath)
            LoadResult.LOADED
        } catch (exception: Exception) {
            closeScript(scriptId)
            throw exception
        }
    }

    suspend fun unload() {
        mutex.withLock {
            closeRunningScripts()
        }
    }

    suspend fun unload(scriptId: String): Boolean = mutex.withLock {
        closeScript(scriptId)
    }

    fun loadedScriptIds(): Set<String> = closeHandlers.keys.toSet()

    fun availableScriptIds(): List<String> {
        if (!Files.isDirectory(scriptDirectory)) {
            return emptyList()
        }
        return listScriptFiles().map { scriptId(it) }
    }

    suspend fun close() {
        unload()
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
        while (true) {
            val scriptId = closeHandlers.keys.firstOrNull() ?: return
            closeScript(scriptId)
        }
    }

    private fun closeScript(scriptId: String): Boolean {
        val handlers = closeHandlers.remove(scriptId)?.asReversed()?.toList() ?: return false
        for (closeHandler in handlers) {
            try {
                closeHandler()
            } catch (exception: Exception) {
                logger.error("Failed to close script {}", scriptId, exception)
            }
        }
        return true
    }

    private fun onClose(scriptId: String, block: () -> Unit) {
        closeHandlers[scriptId]?.add(block)
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

data class LoadSummary(
    val loaded: Int,
    val failed: List<String>
)
