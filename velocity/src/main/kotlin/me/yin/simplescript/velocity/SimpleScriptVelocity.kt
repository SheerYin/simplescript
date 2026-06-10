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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.yin.simplescript.velocity.listener.PreLoginListener
import me.yin.simplescript.velocity.script.SimpleScriptModule
import org.slf4j.Logger
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

class SimpleScriptVelocity @Inject constructor(
    private val logger: Logger,
    private val proxy: ProxyServer,
    @param:DataDirectory private val dataDirectory: Path
) {
    var scope: CoroutineScope? = null
    var scriptModule: SimpleScriptModule? = null

    @Volatile
    var shutdownTimeout = 10.seconds

    @Volatile
    var ready = false

    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val scriptModule = SimpleScriptModule(this, proxy, dataDirectory, logger, scope)

        this.scope = scope
        this.scriptModule = scriptModule

        proxy.eventManager.register(this, PreLoginListener(this))
        scriptModule.register()

        scope.launch {
            try {
                ready = false
                val scriptLoadSummary = scriptModule.load()
                ready = true
                if (scriptLoadSummary.failed.isEmpty()) {
                    logger.info("Scripts loaded: {}", scriptLoadSummary.loaded)
                } else {
                    logger.warn(
                        "Scripts loaded: {}, failed: {} ({})",
                        scriptLoadSummary.loaded,
                        scriptLoadSummary.failed.size,
                        scriptLoadSummary.failed.joinToString(", ")
                    )
                }
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
                withTimeout(shutdownTimeout) { scriptModule?.close() }
            }
        } catch (exception: TimeoutCancellationException) {
            logger.error("Script shutdown timed out after {}", shutdownTimeout, exception)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.error("Failed to close scripts", exception)
        }

        scope?.cancel()
        scope = null
        scriptModule = null

        logger.info("SimpleScript velocity disabled")
    }
}
