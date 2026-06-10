package me.yin.simplescript.velocity.script

import com.velocitypowered.api.proxy.ProxyServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.yin.simplescript.velocity.SimpleScriptVelocity
import org.slf4j.Logger
import java.nio.file.Path

class SimpleScriptModule(
    private val simpleScriptVelocity: SimpleScriptVelocity,
    private val proxy: ProxyServer,
    private val dataDirectory: Path,
    private val logger: Logger,
    parentCoroutineScope: CoroutineScope
) {
    val prefix: String = "简单脚本"

    private val parentJob = parentCoroutineScope.coroutineContext[Job]
    private val coroutineScope = CoroutineScope(parentCoroutineScope.coroutineContext + SupervisorJob(parentJob))

    val scriptService: SimpleScriptService = SimpleScriptService(simpleScriptVelocity)

    val scriptManager: SimpleScriptManager = SimpleScriptManager(
        simpleScriptVelocity = simpleScriptVelocity,
        proxy = proxy,
        dataDirectory = dataDirectory,
        scriptService = scriptService,
        coroutineScope = coroutineScope,
        logger = logger
    )

    val command: SimpleScriptCommand = SimpleScriptCommand(
        simpleScriptVelocity = simpleScriptVelocity,
        proxy = proxy,
        logger = logger,
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
