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
import me.yin.simplescript.script.SimpleScriptModule
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.plugin.IllegalPluginAccessException
import org.bukkit.plugin.java.JavaPlugin
import kotlin.time.Duration.Companion.seconds

class SimpleScript : JavaPlugin() {
    var scope: CoroutineScope? = null
    var scriptModule: SimpleScriptModule? = null

    @Volatile
    var shutdownTimeout = 10.seconds

    @Volatile
    var ready = false

    override fun onEnable() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val scriptModule = SimpleScriptModule(this, scope)
        val prefix = pluginMeta.loggerPrefix ?: pluginMeta.name

        this.scope = scope
        this.scriptModule = scriptModule

        server.pluginManager.registerEvents(AsyncPlayerPreLoginListener(this), this)
        scriptModule.register()

        scope.launch {
            try {
                ready = false
                val scriptLoadSummary = scriptModule.load()
                ready = true
                if (scriptLoadSummary.failed.isEmpty()) {
                    slF4JLogger.info("Scripts loaded: {}", scriptLoadSummary.loaded)
                } else {
                    slF4JLogger.warn(
                        "Scripts loaded: {}, failed: {} ({})",
                        scriptLoadSummary.loaded,
                        scriptLoadSummary.failed.size,
                        scriptLoadSummary.failed.joinToString(", ")
                    )
                }
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
                withTimeout(shutdownTimeout) { scriptModule?.close() }
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
        scriptModule = null

        val prefix = pluginMeta.loggerPrefix ?: pluginMeta.name
        slF4JLogger.info("Disabled {} {}", prefix, pluginMeta.version)
    }

    fun globalRegionScheduler(block: () -> Unit): Boolean {
        if (!isEnabled) {
            return false
        }

        return try {
            server.globalRegionScheduler.execute(this) { block() }
            true
        } catch (_: IllegalPluginAccessException) {
            false
        }
    }

    fun globalRegionSchedulerOrRun(block: () -> Unit) {
        val scheduled = globalRegionScheduler(block)
        if (!scheduled) {
            block()
        }
    }

    fun regionScheduler(world: World, chunkX: Int, chunkZ: Int, block: () -> Unit) {
        if (!isEnabled) {
            return
        }

        server.regionScheduler.execute(this, world, chunkX, chunkZ) { block() }
    }

    fun entityScheduler(
        entity: Entity,
        block: () -> Unit,
        retired: (() -> Unit)? = null,
    ) {
        if (!isEnabled) {
            return
        }

        entity.scheduler.execute(this, { block() }, retired, 1L)
    }
}

