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
import java.nio.file.Path
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic

class SimpleScriptManager(
    private val simpleScriptVelocity: SimpleScriptVelocity,
    private val proxy: ProxyServer,
    private val dataDirectory: Path,
    private val runtime: SimpleScriptRuntime,
    private val coroutineScope: CoroutineScope,
    private val logger: Logger
) {
    val scriptRepository: SimpleScriptRepository = SimpleScriptRepository(
        directory = dataDirectory.resolve("script/main"),
        logger = logger
    )

    private val lifecycleMutex: Mutex = Mutex()
    private val loadedScriptsById: MutableMap<String, LoadedScript> = linkedMapOf()

    suspend fun load(): LoadSummary {
        return loadScripts()
    }

    suspend fun unload() {
        unloadScripts()
    }

    suspend fun loadScripts(): LoadSummary = lifecycleMutex.withLock {
        var loaded: Int = 0
        val failed: MutableList<String> = mutableListOf()

        for (scriptFile: ScriptFile in scriptRepository.list()) {
            if (loadedScriptsById.containsKey(scriptFile.id)) {
                continue
            }

            when (doLoadScript(scriptFile)) {
                LoadResult.LOADED -> loaded += 1
                LoadResult.ALREADY_LOADED -> Unit
                LoadResult.NOT_FOUND,
                LoadResult.FAILED -> failed += scriptFile.id
            }
        }

        LoadSummary(
            loaded = loaded,
            failed = failed
        )
    }

    suspend fun loadScript(scriptId: String): LoadResult = lifecycleMutex.withLock {
        val scriptFile: ScriptFile = scriptRepository.resolveScriptFile(scriptId) ?: return@withLock LoadResult.NOT_FOUND
        doLoadScript(scriptFile)
    }

    suspend fun reloadScripts(): LoadSummary = lifecycleMutex.withLock {
        doUnloadScripts()

        var loaded: Int = 0
        val failed: MutableList<String> = mutableListOf()

        for (scriptFile: ScriptFile in scriptRepository.list()) {
            when (doLoadScript(scriptFile)) {
                LoadResult.LOADED -> loaded += 1
                LoadResult.ALREADY_LOADED -> Unit
                LoadResult.NOT_FOUND,
                LoadResult.FAILED -> failed += scriptFile.id
            }
        }

        LoadSummary(
            loaded = loaded,
            failed = failed
        )
    }

    suspend fun reloadScript(scriptId: String): ReloadResult = lifecycleMutex.withLock {
        val scriptFile: ScriptFile = scriptRepository.resolveScriptFile(scriptId) ?: return@withLock ReloadResult.NOT_FOUND

        when (doUnloadScript(scriptId)) {
            UnloadResult.UNLOADED -> Unit
            UnloadResult.NOT_LOADED -> return@withLock ReloadResult.NOT_LOADED
            UnloadResult.FAILED -> return@withLock ReloadResult.UNLOAD_FAILED
        }

        return@withLock when (doLoadScript(scriptFile)) {
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
        doUnloadScript(scriptId)
    }

    fun loadedScriptIds(): List<String> {
        return loadedScriptsById.keys.sorted()
    }

    fun availableScriptIds(): List<String> {
        return scriptRepository.listIfExists().map { scriptFile: ScriptFile -> scriptFile.id }
    }

    private suspend fun doLoadScript(scriptFile: ScriptFile): LoadResult {
        if (loadedScriptsById.containsKey(scriptFile.id)) {
            return LoadResult.ALREADY_LOADED
        }

        val parentJob: Job? = coroutineScope.coroutineContext[Job]
        val scriptCoroutineScope: CoroutineScope = CoroutineScope(coroutineScope.coroutineContext + SupervisorJob(parentJob))
        var unloadCallback: (suspend () -> Unit)? = null

        val context: SimpleScriptContext = SimpleScriptContext(
            scriptId = scriptFile.id,
            simpleScriptVelocity = simpleScriptVelocity,
            proxy = proxy,
            dataDirectory = dataDirectory,
            logger = logger,
            scope = scriptCoroutineScope,
            registerUnloadCallback = { block: suspend () -> Unit ->
                if (unloadCallback != null) {
                    throw IllegalStateException("Script ${scriptFile.id} already registered onUnload")
                }
                unloadCallback = block
            }
        )

        try {
            val compileResult: ResultWithDiagnostics<CompiledScript> = runtime.compile(scriptFile.path)
            logReports(compileResult.reports)
            val compiledScript: CompiledScript = when (compileResult) {
                is ResultWithDiagnostics.Success -> compileResult.value
                is ResultWithDiagnostics.Failure -> {
                    scriptCoroutineScope.cancel()
                    return LoadResult.FAILED
                }
            }

            val evaluationResult: ResultWithDiagnostics<EvaluationResult> = runtime.evaluate(compiledScript, context)
            logReports(evaluationResult.reports)
            when (evaluationResult) {
                is ResultWithDiagnostics.Success -> Unit
                is ResultWithDiagnostics.Failure -> {
                    scriptCoroutineScope.cancel()
                    return LoadResult.FAILED
                }
            }

            loadedScriptsById[scriptFile.id] = LoadedScript(
                id = scriptFile.id,
                file = scriptFile,
                compiledScript = compiledScript,
                scope = scriptCoroutineScope,
                unload = unloadCallback
            )

            logger.info("Started script {} from {}", scriptFile.id, scriptFile.path)
            return LoadResult.LOADED
        } catch (exception: CancellationException) {
            scriptCoroutineScope.cancel()
            throw exception
        } catch (exception: Exception) {
            scriptCoroutineScope.cancel()
            logger.error("Failed to start script {} from {}", scriptFile.id, scriptFile.path, exception)
            return LoadResult.FAILED
        }
    }

    private suspend fun doUnloadScripts() {
        while (true) {
            val scriptId: String = loadedScriptsById.keys.firstOrNull() ?: return
            doUnloadScript(scriptId)
        }
    }

    private suspend fun doUnloadScript(scriptId: String): UnloadResult {
        val loadedScript: LoadedScript = loadedScriptsById.remove(scriptId) ?: return UnloadResult.NOT_LOADED

        try {
            loadedScript.unload?.invoke()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.error("Failed to unload script {}", scriptId, exception)
            return UnloadResult.FAILED
        } finally {
            loadedScript.scope.cancel()
        }

        return UnloadResult.UNLOADED
    }

    private fun logReports(reports: List<ScriptDiagnostic>) {
        for (report: ScriptDiagnostic in reports) {
            val message: String = "[${report.severity}] ${report.location ?: ""} ${report.message}"
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

data class LoadedScript(
    val id: String,
    val file: ScriptFile,
    val compiledScript: CompiledScript,
    val scope: CoroutineScope,
    val unload: (suspend () -> Unit)?
)

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
