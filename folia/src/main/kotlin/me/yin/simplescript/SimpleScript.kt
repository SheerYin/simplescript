package me.yin.simplescript

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.yin.simplescript.listener.AsyncPlayerPreLoginListener
import me.yin.simplescript.script.SimpleScriptCommand
import me.yin.simplescript.script.SimpleScriptManager
import me.yin.simplescript.script.SimpleScriptService
import org.bukkit.World
import org.bukkit.plugin.java.JavaPlugin
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.suspendCancellableCoroutine

class SimpleScript : JavaPlugin() {
    var scope: CoroutineScope? = null
    var scriptManager: SimpleScriptManager? = null

    @Volatile
    var shutdownTimeout = 10.seconds

    @Volatile
    var ready = false

    override fun onEnable() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val scriptService = SimpleScriptService(this)
        val scriptManager = SimpleScriptManager(this, scriptService, slF4JLogger)
        val prefix = pluginMeta.loggerPrefix ?: pluginMeta.name

        this.scope = scope
        this.scriptManager = scriptManager

        server.pluginManager.registerEvents(AsyncPlayerPreLoginListener(this), this)
        SimpleScriptCommand(this, scriptManager, scope, prefix).register()

        scope.launch {
            try {
                ready = false
                val count = scriptManager.reload()
                ready = true
                slF4JLogger.info("Loaded {} script(s)", count)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                slF4JLogger.error("Failed to load scripts", exception)
            }
        }

        slF4JLogger.info("Enabled {} {}", prefix, pluginMeta.version)
    }

    override fun onDisable() {
        ready = false

        try {
            runBlocking {
                withTimeout(shutdownTimeout) { scriptManager?.close() }
            }
        } catch (exception: TimeoutCancellationException) {
            slF4JLogger.error("Script shutdown timed out after {}", shutdownTimeout, exception)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            slF4JLogger.error("Failed to close scripts", exception)
        }

        scope?.cancel()
        scope = null
        scriptManager = null

        val prefix = pluginMeta.loggerPrefix ?: pluginMeta.name
        slF4JLogger.info("Disabled {} {}", prefix, pluginMeta.version)
    }

    suspend fun <T> runGlobalRegionAndWait(block: () -> T): T {
        if (server.isGlobalTickThread || !isEnabled) {
            return block()
        }
        return suspendCancellableCoroutine { continuation ->
            val scheduledTask = server.globalRegionScheduler.run(this) { _ ->
                try {
                    continuation.resume(block())
                } catch (exception: Throwable) {
                    continuation.resumeWithException(exception)
                }
            }
            continuation.invokeOnCancellation { scheduledTask.cancel() }
        }
    }

    suspend fun <T> runRegionAndWait(world: World, chunkX: Int, chunkZ: Int, block: () -> T): T {
        if (server.isOwnedByCurrentRegion(world, chunkX, chunkZ) || !isEnabled) {
            return block()
        }
        return suspendCancellableCoroutine { continuation ->
            val scheduledTask = server.regionScheduler.run(this, world, chunkX, chunkZ) { _ ->
                try {
                    continuation.resume(block())
                } catch (exception: Throwable) {
                    continuation.resumeWithException(exception)
                }
            }
            continuation.invokeOnCancellation { scheduledTask.cancel() }
        }
    }
}
