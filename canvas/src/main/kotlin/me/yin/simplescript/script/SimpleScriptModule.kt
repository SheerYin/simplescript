package me.yin.simplescript.script

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.yin.simplescript.SimpleScript

class SimpleScriptModule(
    private val plugin: SimpleScript,
    parentCoroutineScope: CoroutineScope
) {
    val prefix: String = plugin.pluginMeta.loggerPrefix ?: plugin.pluginMeta.name

    private val parentJob = parentCoroutineScope.coroutineContext[Job]
    private val coroutineScope = CoroutineScope(parentCoroutineScope.coroutineContext + SupervisorJob(parentJob))

    val scriptService: SimpleScriptService = SimpleScriptService(plugin)

    val scriptManager: SimpleScriptManager = SimpleScriptManager(
        plugin = plugin,
        scriptService = scriptService,
        coroutineScope = coroutineScope,
        logger = plugin.slF4JLogger
    )

    val command: SimpleScriptCommand = SimpleScriptCommand(
        plugin = plugin,
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
