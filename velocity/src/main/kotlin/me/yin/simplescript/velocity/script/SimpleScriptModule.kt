package me.yin.simplescript.velocity.script

import com.velocitypowered.api.proxy.ProxyServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.yin.simplescript.velocity.SimpleScriptVelocity
import me.yin.simplescript.velocity.script.permission.SimpleScriptPermissions
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
    val permissions: SimpleScriptPermissions = SimpleScriptPermissions()

    private val parentJob = parentCoroutineScope.coroutineContext[Job]
    private val coroutineScope = CoroutineScope(parentCoroutineScope.coroutineContext + SupervisorJob(parentJob))

    val runtime: SimpleScriptRuntime = SimpleScriptRuntime(
        simpleScriptVelocity = simpleScriptVelocity,
        logger = logger
    )

    val manager: SimpleScriptManager = SimpleScriptManager(
        simpleScriptVelocity = simpleScriptVelocity,
        proxy = proxy,
        dataDirectory = dataDirectory,
        runtime = runtime,
        coroutineScope = coroutineScope,
        logger = logger
    )

    val command: SimpleScriptCommand = SimpleScriptCommand(
        simpleScriptVelocity = simpleScriptVelocity,
        proxy = proxy,
        logger = logger,
        manager = manager,
        coroutineScope = coroutineScope,
        permissions = permissions,
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
