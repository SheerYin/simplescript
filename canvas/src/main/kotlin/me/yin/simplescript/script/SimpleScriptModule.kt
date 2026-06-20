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

    private val parentJob = parentCoroutineScope.coroutineContext[Job]
    private val coroutineScope = CoroutineScope(parentCoroutineScope.coroutineContext + SupervisorJob(parentJob))

    val scriptService: SimpleScriptService = SimpleScriptService(simpleScript)

    val scriptManager: SimpleScriptManager = SimpleScriptManager(
        simpleScript = simpleScript,
        scriptService = scriptService,
        coroutineScope = coroutineScope,
        logger = simpleScript.slF4JLogger
    )

    val command: SimpleScriptCommand = SimpleScriptCommand(
        simpleScript = simpleScript,
        scriptManager = scriptManager,
        coroutineScope = coroutineScope,
        prefix = prefix
    )

    fun register() {
        command.register()
    }

    suspend fun load(): LoadSummary {
        return scriptManager.load()
    }

    suspend fun close() {
        try {
            scriptManager.close()
        } finally {
            coroutineScope.cancel()
        }
    }
}
