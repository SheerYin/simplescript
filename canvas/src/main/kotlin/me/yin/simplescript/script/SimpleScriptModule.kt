package me.yin.simplescript.script

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.yin.simplescript.SimpleScript

class SimpleScriptModule(
    private val simpleScript: SimpleScript,
    parentCoroutineScope: CoroutineScope
) {
    val prefix: String = simpleScript.pluginMeta.loggerPrefix ?: simpleScript.pluginMeta.name

    private val parentJob: Job? = parentCoroutineScope.coroutineContext[Job]
    private val coroutineScope: CoroutineScope = CoroutineScope(parentCoroutineScope.coroutineContext + SupervisorJob(parentJob))

    val runtime: SimpleScriptRuntime = SimpleScriptRuntime(simpleScript)

    val manager: SimpleScriptManager = SimpleScriptManager(
        simpleScript = simpleScript,
        runtime = runtime,
        coroutineScope = coroutineScope,
        logger = simpleScript.slF4JLogger
    )

    val command: SimpleScriptCommand = SimpleScriptCommand(
        simpleScript = simpleScript,
        manager = manager,
        coroutineScope = coroutineScope,
        prefix = prefix
    )

    fun register() {
        command.register()
    }

    suspend fun load(): LoadSummary {
        return manager.load()
    }

    suspend fun close() {
        try {
            manager.unload()
        } finally {
            coroutineScope.cancel()
        }
    }
}

