package me.yin.simplescript.velocity

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.yin.simplescript.velocity.listener.PreLoginListener
import me.yin.simplescript.velocity.script.SimpleScriptCommand
import me.yin.simplescript.velocity.script.SimpleScriptManager
import me.yin.simplescript.velocity.script.SimpleScriptService
import org.slf4j.Logger
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

class SimpleScriptVelocity @Inject constructor(
    private val logger: Logger,
    private val proxy: ProxyServer,
    @param:DataDirectory private val dataDirectory: Path
) {
    private var scope: CoroutineScope? = null
    private var scriptManager: SimpleScriptManager? = null

    @Volatile
    var shutdownTimeout = 10.seconds

    @Volatile
    var ready = false

    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val scriptService = SimpleScriptService(this)
        val scriptManager = SimpleScriptManager(this, proxy, dataDirectory, scriptService, logger)

        this.scope = scope
        this.scriptManager = scriptManager

        proxy.eventManager.register(this, PreLoginListener(this))
        SimpleScriptCommand(this, proxy, logger, scriptManager, scope, "简单脚本").register()

        scope.launch {
            try {
                ready = false
                val count = scriptManager.reload()
                ready = true
                logger.info("Loaded {} script(s)", count)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                logger.error("Failed to load scripts", exception)
            }
        }

        logger.info("SimpleScript velocity enabled")
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        ready = false

        try {
            runBlocking {
                withTimeout(shutdownTimeout) { scriptManager?.close() }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.error("Failed to close scripts", exception)
        }

        scope?.cancel()
        scope = null
        scriptManager = null

        logger.info("SimpleScript velocity disabled")
    }
}
