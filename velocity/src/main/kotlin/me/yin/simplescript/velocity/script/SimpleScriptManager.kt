package me.yin.simplescript.velocity.script

import com.velocitypowered.api.proxy.ProxyServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.yin.simplescript.velocity.SimpleScriptVelocity
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic

class SimpleScriptManager(
    private val simpleScriptVelocity: SimpleScriptVelocity,
    private val proxy: ProxyServer,
    private val dataDirectory: Path,
    private val scriptService: SimpleScriptService,
    private val coroutineScope: CoroutineScope,
    private val logger: Logger
) {
    val scriptDirectory: Path = dataDirectory.resolve(SCRIPT_DIRECTORY).normalize()

    private val lifecycleMutex = Mutex()
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
        val normalizedScriptId = normalizeScriptId(scriptId) ?: return@withLock LoadResult.NOT_FOUND
        doLoadScript(normalizedScriptId)
    }

    suspend fun reloadScripts(): LoadSummary = lifecycleMutex.withLock {
        doUnloadScripts()
        doLoadScripts()
    }

    suspend fun reloadScript(scriptId: String): ReloadResult = lifecycleMutex.withLock {
        val normalizedScriptId = normalizeScriptId(scriptId) ?: return@withLock ReloadResult.NOT_FOUND
        when (doUnloadScript(normalizedScriptId)) {
            UnloadResult.UNLOADED -> Unit
            UnloadResult.NOT_LOADED -> return@withLock ReloadResult.NOT_LOADED
            UnloadResult.FAILED -> return@withLock ReloadResult.UNLOAD_FAILED
        }

        when (doLoadScript(normalizedScriptId)) {
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
        val normalizedScriptId = normalizeScriptId(scriptId) ?: return@withLock UnloadResult.NOT_LOADED
        doUnloadScript(normalizedScriptId)
    }

    fun availableScriptIds(): List<String> {
        if (!Files.isDirectory(scriptDirectory)) {
            return emptyList()
        }
        return listScriptFiles().map { scriptId(it) }
    }

    private suspend fun doLoadScripts(): LoadSummary {
        Files.createDirectories(scriptDirectory)

        val scripts = listScriptFiles()
        var loaded = 0
        val failed = mutableListOf<String>()
        for (scriptPath in scripts) {
            val scriptId = scriptId(scriptPath)

            try {
                if (unloadCallbacksByScriptId.containsKey(scriptId)) {
                    continue
                }
                evaluateScript(scriptId, scriptPath)
                loaded += 1
                logger.info("Started script {} from {}", scriptId, scriptPath)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                failed += scriptId
                doUnloadScript(scriptId)
                logger.error("Failed to start script {} from {}", scriptId, scriptPath, exception)
            }
        }

        return LoadSummary(
            loaded = loaded,
            failed = failed
        )
    }

    private suspend fun doLoadScript(scriptId: String): LoadResult {
        if (unloadCallbacksByScriptId.containsKey(scriptId)) {
            return LoadResult.ALREADY_LOADED
        }
        val scriptPath = scriptPath(scriptId) ?: return LoadResult.NOT_FOUND
        if (!Files.isRegularFile(scriptPath)) {
            return LoadResult.NOT_FOUND
        }

        try {
            evaluateScript(scriptId, scriptPath)
            logger.info("Started script {} from {}", scriptId, scriptPath)
            return LoadResult.LOADED
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            doUnloadScript(scriptId)
            logger.error("Failed to start script {} from {}", scriptId, scriptPath, exception)
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

    private fun listScriptFiles(): List<Path> {
        return Files.walk(scriptDirectory).use { paths ->
            paths
                .filter { path -> Files.isRegularFile(path) }
                .filter { path -> path.fileName.toString().endsWith(".$SIMPLE_SCRIPT_EXTENSION") }
                .sorted(compareBy { path -> scriptId(path) })
                .toList()
        }
    }

    private fun evaluateScript(scriptId: String, scriptPath: Path) {
        val parentJob = coroutineScope.coroutineContext[Job]
        val scriptCoroutineScope = CoroutineScope(coroutineScope.coroutineContext + SupervisorJob(parentJob))
        val scope = SimpleScriptScope(
            id = scriptId,
            simpleScriptVelocity = simpleScriptVelocity,
            proxy = proxy,
            dataDirectory = dataDirectory,
            logger = logger,
            scriptCoroutineScope = scriptCoroutineScope,
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
            scriptService.evaluate(scriptPath, scope)
        } catch (exception: CancellationException) {
            if (!unloadCallbacksByScriptId.containsKey(scriptId)) {
                scriptCoroutineScope.cancel()
            }
            throw exception
        } catch (exception: Exception) {
            if (!unloadCallbacksByScriptId.containsKey(scriptId)) {
                scriptCoroutineScope.cancel()
            }
            throw exception
        }
        when (result) {
            is ResultWithDiagnostics.Success -> {
                logReports(result.reports)
                unloadCallbacksByScriptId.putIfAbsent(scriptId) {
                    scriptCoroutineScope.cancel()
                }
            }

            is ResultWithDiagnostics.Failure -> {
                logReports(result.reports)
                if (!unloadCallbacksByScriptId.containsKey(scriptId)) {
                    scriptCoroutineScope.cancel()
                }
                throw IllegalStateException("Failed to evaluate script $scriptPath")
            }
        }
    }

    private fun scriptId(scriptPath: Path): String {
        return scriptDirectory
            .relativize(scriptPath)
            .toString()
            .replace('\\', '/')
            .removeSuffix(".$SIMPLE_SCRIPT_EXTENSION")
    }

    private fun scriptPath(scriptId: String): Path? {
        val normalizedScriptId = normalizeScriptId(scriptId) ?: return null
        return scriptDirectory.resolve("$normalizedScriptId.$SIMPLE_SCRIPT_EXTENSION").normalize()
    }

    private fun normalizeScriptId(scriptId: String): String? {
        val portableScriptId = scriptId.replace('\\', '/').trim('/')
        if (portableScriptId.isEmpty()) {
            return null
        }

        return try {
            val relativePath = Path.of(portableScriptId).normalize()
            if (relativePath.isAbsolute || relativePath.startsWith("..")) {
                return null
            }

            relativePath
                .toString()
                .replace('\\', '/')
                .takeIf { it.isNotEmpty() && it != "." }
        } catch (exception: InvalidPathException) {
            logger.warn("Rejected invalid script id: {}", scriptId, exception)
            null
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

    companion object {
        const val SCRIPT_DIRECTORY = "scripts"
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
