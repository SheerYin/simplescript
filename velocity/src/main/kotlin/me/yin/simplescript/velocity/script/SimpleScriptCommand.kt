package me.yin.simplescript.velocity.script

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.ProxyServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.yin.simplescript.velocity.SimpleScriptVelocity
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture

class SimpleScriptCommand(
    private val plugin: Any,
    private val proxy: ProxyServer,
    private val logger: Logger,
    private val scriptManager: SimpleScriptManager,
    private val coroutineScope: CoroutineScope,
    private val prefix: String
) {
    @Volatile
    var scriptSuggestionSemaphore = Semaphore(2)

    @Volatile
    var basePermission = "simplescript.command"

    @Volatile
    var permissionScriptReload = "simplescript.command.reload"

    @Volatile
    var permissionScriptLoad = "simplescript.command.load"

    @Volatile
    var permissionScriptUnload = "simplescript.command.unload"

    @Volatile
    var permissionScriptList = "simplescript.command.list"

    fun register() {
        val command = BrigadierCommand(rootCommand())
        val meta = proxy.commandManager.metaBuilder(command)
            .plugin(plugin)
            .build()
        proxy.commandManager.register(meta, command)
    }

    private fun rootCommand(): LiteralArgumentBuilder<CommandSource> = BrigadierCommand.literalArgumentBuilder(MAIN_COMMAND)
        .requires { source -> source.hasPermission(basePermission) }
        .executes { context ->
            sendHelp(context.source)
            return@executes 1
        }
        .then(
            BrigadierCommand.literalArgumentBuilder("reload")
                .requires { source -> source.hasPermission(permissionScriptReload) }
                .executes { context ->
                    reload(context.source)
                    return@executes 1
                }
                .then(
                    BrigadierCommand.requiredArgumentBuilder("id", StringArgumentType.word())
                        .suggests { _, builder ->
                            suggestLoadedSync(builder)
                            builder.buildFuture()
                        }
                        .executes { context ->
                            reload(
                                context.source,
                                StringArgumentType.getString(context, "id")
                            )
                            return@executes 1
                        }
                )
        )
        .then(
            BrigadierCommand.literalArgumentBuilder("load")
                .requires { source -> source.hasPermission(permissionScriptLoad) }
                .then(
                    BrigadierCommand.requiredArgumentBuilder("id", StringArgumentType.word())
                        .suggests { _, builder -> suggestUnloadedAsync(builder) }
                        .executes { context ->
                            load(
                                context.source,
                                StringArgumentType.getString(context, "id")
                            )
                            return@executes 1
                        }
                )
        )
        .then(
            BrigadierCommand.literalArgumentBuilder("unload")
                .requires { source -> source.hasPermission(permissionScriptUnload) }
                .then(
                    BrigadierCommand.requiredArgumentBuilder("id", StringArgumentType.word())
                        .suggests { _, builder ->
                            suggestLoadedSync(builder)
                            builder.buildFuture()
                        }
                        .executes { context ->
                            unload(
                                context.source,
                                StringArgumentType.getString(context, "id")
                            )
                            return@executes 1
                        }
                )
        )
        .then(
            BrigadierCommand.literalArgumentBuilder("list")
                .requires { source -> source.hasPermission(permissionScriptList) }
                .executes { context ->
                    list(context.source)
                    return@executes 1
                }
        )

    private fun reload(source: CommandSource) {
        coroutineScope.launch {
            try {
                (plugin as? SimpleScriptVelocity)?.ready = false
                scriptManager.unload()
                val summary = scriptManager.load()
                (plugin as? SimpleScriptVelocity)?.ready = true
                sendLoadSummary(source, "Reloaded", summary)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                logger.error("Failed to reload scripts", exception)
                source.sendMessage(prefixMessage().append(Component.text("Failed to reload scripts: ${exception.message ?: exception.javaClass.simpleName}", NamedTextColor.RED)))
            }
        }
    }

    private fun reload(source: CommandSource, scriptId: String) {
        coroutineScope.launch {
            try {
                if (!scriptManager.unload(scriptId)) {
                    source.sendMessage(prefixMessage().append(Component.text("Script not loaded: $scriptId", NamedTextColor.RED)))
                    return@launch
                }

                when (scriptManager.load(scriptId)) {
                    LoadResult.LOADED -> source.sendMessage(prefixMessage("Reloaded script $scriptId"))
                    LoadResult.ALREADY_LOADED -> source.sendMessage(prefixMessage().append(Component.text("Script already loaded: $scriptId", NamedTextColor.RED)))
                    LoadResult.NOT_FOUND -> source.sendMessage(prefixMessage().append(Component.text("Script file missing: $scriptId", NamedTextColor.RED)))
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                logger.error("Failed to reload script {}", scriptId, exception)
                source.sendMessage(prefixMessage().append(Component.text("Failed to reload script $scriptId: ${exception.message ?: exception.javaClass.simpleName}", NamedTextColor.RED)))
            }
        }
    }

    private fun load(source: CommandSource, scriptId: String) {
        coroutineScope.launch {
            try {
                when (scriptManager.load(scriptId)) {
                    LoadResult.LOADED -> source.sendMessage(prefixMessage("Loaded script $scriptId"))
                    LoadResult.ALREADY_LOADED -> source.sendMessage(prefixMessage().append(Component.text("Script already loaded: $scriptId", NamedTextColor.RED)))
                    LoadResult.NOT_FOUND -> source.sendMessage(prefixMessage().append(Component.text("Script not found: $scriptId", NamedTextColor.RED)))
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                logger.error("Failed to load script {}", scriptId, exception)
                source.sendMessage(prefixMessage().append(Component.text("Failed to load script $scriptId: ${exception.message ?: exception.javaClass.simpleName}", NamedTextColor.RED)))
            }
        }
    }

    private fun unload(source: CommandSource, scriptId: String) {
        coroutineScope.launch {
            try {
                val unloaded = scriptManager.unload(scriptId)
                if (!unloaded) {
                    source.sendMessage(prefixMessage().append(Component.text("Script not loaded: $scriptId", NamedTextColor.RED)))
                    return@launch
                }
                source.sendMessage(prefixMessage("Unloaded script $scriptId"))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                logger.error("Failed to unload script {}", scriptId, exception)
                source.sendMessage(prefixMessage().append(Component.text("Failed to unload script $scriptId: ${exception.message ?: exception.javaClass.simpleName}", NamedTextColor.RED)))
            }
        }
    }

    private fun list(source: CommandSource) {
        val ids = scriptManager.loadedScriptIds().sorted()
        if (ids.isEmpty()) {
            source.sendMessage(prefixMessage("No scripts loaded"))
            return
        }
        source.sendMessage(prefixMessage("Loaded ${ids.size} script(s):"))
        for (id in ids) {
            source.sendMessage(prefixMessage("  - $id"))
        }
    }

    private fun sendLoadSummary(source: CommandSource, action: String, summary: LoadSummary) {
        if (summary.failed.isEmpty()) {
            source.sendMessage(prefixMessage("$action ${summary.loaded} script(s)"))
            return
        }

        source.sendMessage(
            prefixMessage()
                .append(Component.text("$action ${summary.loaded} script(s), failed ${summary.failed.size} script(s)", NamedTextColor.RED))
        )
        for (id in summary.failed.sorted()) {
            source.sendMessage(
                prefixMessage()
                    .append(Component.text("  - $id", NamedTextColor.RED))
            )
        }
    }

    private fun suggestLoadedSync(builder: SuggestionsBuilder) {
        val remaining = builder.remainingLowerCase
        for (id in scriptManager.loadedScriptIds().sorted()) {
            if (id.startsWith(remaining, ignoreCase = true)) {
                builder.suggest(id)
            }
        }
    }

    private fun suggestUnloadedAsync(builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        val prefix = builder.remaining
        val lowercasePrefix = builder.remainingLowerCase

        return coroutineScope.future {
            try {
                scriptSuggestionSemaphore.withPermit {
                    val loaded = scriptManager.loadedScriptIds()
                    val unloaded = scriptManager.availableScriptIds()
                        .filter { it !in loaded }
                        .sorted()
                    for (id in unloaded) {
                        if (id.startsWith(lowercasePrefix, ignoreCase = true)) {
                            builder.suggest(id)
                        }
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                logger.warn("Failed to suggest unloaded scripts for prefix {}", prefix, exception)
            }

            builder.build()
        }
    }

    private fun sendHelp(source: CommandSource) {
        source.sendMessage(prefixMessage("/$MAIN_COMMAND reload"))
        source.sendMessage(prefixMessage("/$MAIN_COMMAND reload <id>"))
        source.sendMessage(prefixMessage("/$MAIN_COMMAND load <id>"))
        source.sendMessage(prefixMessage("/$MAIN_COMMAND unload <id>"))
        source.sendMessage(prefixMessage("/$MAIN_COMMAND list"))
    }

    private fun prefixMessage(message: String = ""): Component {
        return Component.text()
            .append(Component.text("[", NamedTextColor.WHITE))
            .append(Component.text(prefix, NamedTextColor.GREEN))
            .append(Component.text("] ", NamedTextColor.WHITE))
            .append(Component.text(message))
            .build()
    }

    companion object {
        const val MAIN_COMMAND = "simplescript"
    }
}
