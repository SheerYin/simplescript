package me.yin.simplescript.script

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private val simpleScript: SimpleScript,
    private val scriptService: SimpleScriptService,
    private val coroutineScope: CoroutineScope,
    private val logger: Logger
) {
    val scriptDirectory: Path = simpleScript.dataPath.resolve("scripts").normalize()

    private val lifecycleMutex = Mutex()
    private val scriptDirectoryAccess = ScriptDirectory(scriptDirectory, logger)
    val unloadCallbacksByScriptId = ConcurrentHashMap<String, suspend () -> Unit>()

    suspend fun load(): LoadSummary {
        return loadScripts()
    }

    suspend fun close() {
        unloadScripts()
    }

    suspend fun loadScripts(): LoadSummary = lifecycleMutex.withLock {
        doLoadScripts()
    }

    suspend fun loadScript(scriptId: String): LoadResult = lifecycleMutex.withLock {
        val script = scriptDirectoryAccess.resolveScriptFile(scriptId) ?: return@withLock LoadResult.NOT_FOUND
        doLoadScript(script)
    }

    suspend fun reloadScripts(): LoadSummary = lifecycleMutex.withLock {
        doUnloadScripts()
        doLoadScripts()
    }

    suspend fun reloadScript(scriptId: String): ReloadResult = lifecycleMutex.withLock {
        val script = scriptDirectoryAccess.resolveScriptFile(scriptId) ?: return@withLock ReloadResult.NOT_FOUND
        when (doUnloadScript(script.id)) {
            UnloadResult.UNLOADED -> Unit
            UnloadResult.NOT_LOADED -> return@withLock ReloadResult.NOT_LOADED
            UnloadResult.FAILED -> return@withLock ReloadResult.UNLOAD_FAILED
        }

        when (doLoadScript(script)) {
            LoadResult.LOADED -> ReloadResult.RELOADED
            LoadResult.ALREADY_LOADED -> ReloadResult.ALREADY_LOADED
            LoadResult.NOT_FOUND -> ReloadResult.NOT_FOUND
            LoadResult.FAILED -> ReloadResult.LOAD_FAILED
        }
    }

    suspend fun unloadScripts() {
        lifecycleMutex.withLock {
            doUnloadScripts()
        }
    }

    suspend fun unloadScript(scriptId: String): UnloadResult = lifecycleMutex.withLock {
        val script = scriptDirectoryAccess.resolveScriptFile(scriptId) ?: return@withLock UnloadResult.NOT_LOADED
        doUnloadScript(script.id)
    }

    fun availableScriptIds(): List<String> {
        if (!Files.isDirectory(scriptDirectory)) {
            return emptyList()
        }
        return scriptDirectoryAccess.list().map { it.id }
    }

    private suspend fun doLoadScripts(): LoadSummary {
        Files.createDirectories(scriptDirectory)

        val scripts = scriptDirectoryAccess.list()
        var loaded = 0
        val failed = mutableListOf<String>()
        for (script in scripts) {
            try {
                if (unloadCallbacksByScriptId.containsKey(script.id)) {
                    continue
                }
                evaluateScript(script)
                loaded += 1
                logger.info("Started script {} from {}", script.id, script.path)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                failed += script.id
                doUnloadScript(script.id)
                logger.error("Failed to start script {} from {}", script.id, script.path, exception)
            }
        }

        return LoadSummary(
            loaded = loaded,
            failed = failed
        )
    }

    private suspend fun doLoadScript(script: ScriptFile): LoadResult {
        if (unloadCallbacksByScriptId.containsKey(script.id)) {
            return LoadResult.ALREADY_LOADED
        }
        if (!Files.isRegularFile(script.path)) {
            return LoadResult.NOT_FOUND
        }

        try {
            evaluateScript(script)
            logger.info("Started script {} from {}", script.id, script.path)
            return LoadResult.LOADED
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            doUnloadScript(script.id)
            logger.error("Failed to start script {} from {}", script.id, script.path, exception)
            return LoadResult.FAILED
        }
    }

    private suspend fun doUnloadScripts() {
        while (true) {
            val scriptId = unloadCallbacksByScriptId.keys.firstOrNull() ?: return
            doUnloadScript(scriptId)
        }
    }

    private suspend fun doUnloadScript(scriptId: String): UnloadResult {
        val unloadCallback = unloadCallbacksByScriptId.remove(scriptId) ?: return UnloadResult.NOT_LOADED
        try {
            unloadCallback()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.error("Failed to unload script {}", scriptId, exception)
            return UnloadResult.FAILED
        }
        return UnloadResult.UNLOADED
    }

    private fun evaluateScript(script: ScriptFile) {
        val parentJob = coroutineScope.coroutineContext[Job]
        val scriptCoroutineScope = CoroutineScope(coroutineScope.coroutineContext + SupervisorJob(parentJob))
        val context = SimpleScriptContext(
            scriptId = script.id,
            simpleScript = simpleScript,
            logger = logger,
            scope = scriptCoroutineScope,
            registerUnloadCallback = { id, block ->
                val unloadCallback: suspend () -> Unit = {
                    try {
                        block()
                    } finally {
                        scriptCoroutineScope.cancel()
                    }
                }
                if (unloadCallbacksByScriptId.putIfAbsent(id, unloadCallback) != null) {
                    throw IllegalStateException("Script $id already registered onUnload")
                }
            }
        )

        val result = try {
            scriptService.evaluate(script.path, context)
        } catch (exception: CancellationException) {
            if (!unloadCallbacksByScriptId.containsKey(script.id)) {
                scriptCoroutineScope.cancel()
            }
            throw exception
        } catch (exception: Exception) {
            if (!unloadCallbacksByScriptId.containsKey(script.id)) {
                scriptCoroutineScope.cancel()
            }
            throw exception
        }
        when (result) {
            is ResultWithDiagnostics.Success -> {
                logReports(result.reports)
                unloadCallbacksByScriptId.putIfAbsent(script.id) {
                    scriptCoroutineScope.cancel()
                }
            }

            is ResultWithDiagnostics.Failure -> {
                logReports(result.reports)
                if (!unloadCallbacksByScriptId.containsKey(script.id)) {
                    scriptCoroutineScope.cancel()
                }
                throw IllegalStateException("Failed to evaluate script ${script.path}")
            }
        }
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

}

private data class ScriptFile(
    val id: String,
    val path: Path
)

private class ScriptDirectory(
    private val scriptDirectory: Path,
    private val logger: Logger
) {
    private val scriptIdPattern = Regex("""[\p{L}\p{N}_.-]+(?:/[\p{L}\p{N}_.-]+)*""")

    fun list(): List<ScriptFile> {
        return Files.walk(scriptDirectory).use { paths ->
            paths
                .iterator()
                .asSequence()
                .filter { path -> Files.isRegularFile(path) }
                .filter { path -> path.fileName.toString().endsWith(".$SIMPLE_SCRIPT_EXTENSION") }
                .mapNotNull { path -> createScriptFile(path) }
                .sortedBy { script -> script.id }
                .toList()
        }
    }

    fun resolveScriptFile(scriptId: String): ScriptFile? {
        if (!isValidScriptId(scriptId)) {
            return null
        }

        return ScriptFile(
            id = scriptId,
            path = scriptDirectory.resolve("$scriptId.$SIMPLE_SCRIPT_EXTENSION")
        )
    }

    private fun createScriptFile(scriptPath: Path): ScriptFile? {
        val scriptId = scriptDirectory
            .relativize(scriptPath)
            .joinToString("/") { path -> path.toString() }
            .removeSuffix(".$SIMPLE_SCRIPT_EXTENSION")

        if (!isValidScriptId(scriptId)) {
            logger.warn("Ignored script with invalid id {} from {}", scriptId, scriptPath)
            return null
        }

        return ScriptFile(
            id = scriptId,
            path = scriptPath
        )
    }

    private fun isValidScriptId(scriptId: String): Boolean {
        return scriptIdPattern.matches(scriptId) &&
            scriptId.split('/').none { path -> path == "." || path == ".." }
    }
}

enum class LoadResult {
    LOADED,
    ALREADY_LOADED,
    NOT_FOUND,
    FAILED
}

enum class ReloadResult {
    RELOADED,
    NOT_LOADED,
    ALREADY_LOADED,
    NOT_FOUND,
    UNLOAD_FAILED,
    LOAD_FAILED
}

enum class UnloadResult {
    UNLOADED,
    NOT_LOADED,
    FAILED
}

data class LoadSummary(
    val loaded: Int,
    val failed: List<String>
)
