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
import me.yin.simplescript.velocity.script.permission.SimpleScriptPermissions
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture

class SimpleScriptCommand(
    private val simpleScriptVelocity: SimpleScriptVelocity,
    private val proxy: ProxyServer,
    private val logger: Logger,
    private val manager: SimpleScriptManager,
    private val coroutineScope: CoroutineScope,
    private val permissions: SimpleScriptPermissions,
    private val prefix: String
) {
    @Volatile
    var scriptSuggestionSemaphore = Semaphore(2)

    fun register() {
        val command = BrigadierCommand(simpleScriptNode())
        val meta = proxy.commandManager.metaBuilder(command)
            .aliases(*COMMAND_ALIASES.toTypedArray())
            .plugin(simpleScriptVelocity)
            .build()
        proxy.commandManager.register(meta, command)
    }

    fun simpleScriptNode(): LiteralArgumentBuilder<CommandSource> {
        val root = BrigadierCommand.literalArgumentBuilder(MAIN_COMMAND)
            .requires { source -> source.hasPermission(permissions.simpleScriptCommand) }
            .then(reloadBuilder())
            .then(loadBuilder())
            .then(unloadBuilder())
            .then(listBuilder())

        return root
    }

    fun reloadBuilder(name: String = "reload"): LiteralArgumentBuilder<CommandSource> {
        return BrigadierCommand.literalArgumentBuilder(name)
            .requires { source -> source.hasPermission(permissions.reloadCommand) }
            .executes { context ->
                reload(context.source)
                return@executes 1
            }
            .then(
                BrigadierCommand.requiredArgumentBuilder("id", StringArgumentType.greedyString())
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
    }

    fun loadBuilder(name: String = "load"): LiteralArgumentBuilder<CommandSource> {
        return BrigadierCommand.literalArgumentBuilder(name)
            .requires { source -> source.hasPermission(permissions.loadCommand) }
            .then(
                BrigadierCommand.requiredArgumentBuilder("id", StringArgumentType.greedyString())
                    .suggests { _, builder -> suggestUnloadedAsync(builder) }
                    .executes { context ->
                        load(
                            context.source,
                            StringArgumentType.getString(context, "id")
                        )
                        return@executes 1
                    }
            )
    }

    fun unloadBuilder(name: String = "unload"): LiteralArgumentBuilder<CommandSource> {
        return BrigadierCommand.literalArgumentBuilder(name)
            .requires { source -> source.hasPermission(permissions.unloadCommand) }
            .then(
                BrigadierCommand.requiredArgumentBuilder("id", StringArgumentType.greedyString())
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
    }

    fun listBuilder(name: String = "list"): LiteralArgumentBuilder<CommandSource> {
        return BrigadierCommand.literalArgumentBuilder(name)
            .requires { source -> source.hasPermission(permissions.listCommand) }
            .executes { context ->
                list(context.source)
                return@executes 1
            }
    }

    private fun reload(source: CommandSource) {
        coroutineScope.launch {
            try {
                simpleScriptVelocity.ready = false
                val summary: LoadSummary = manager.reloadScripts()
                simpleScriptVelocity.ready = true
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
                when (manager.reloadScript(scriptId)) {
                    ReloadResult.RELOADED -> source.sendMessage(prefixMessage("Reloaded script $scriptId"))
                    ReloadResult.NOT_LOADED -> source.sendMessage(prefixMessage().append(Component.text("Script not loaded: $scriptId", NamedTextColor.RED)))
                    ReloadResult.ALREADY_LOADED -> source.sendMessage(prefixMessage().append(Component.text("Script already loaded: $scriptId", NamedTextColor.RED)))
                    ReloadResult.NOT_FOUND -> source.sendMessage(prefixMessage().append(Component.text("Script file missing: $scriptId", NamedTextColor.RED)))
                    ReloadResult.UNLOAD_FAILED -> source.sendMessage(prefixMessage().append(Component.text("Failed to unload script $scriptId; see console for details", NamedTextColor.RED)))
                    ReloadResult.LOAD_FAILED -> source.sendMessage(prefixMessage().append(Component.text("Failed to load script $scriptId; see console for details", NamedTextColor.RED)))
                }
            } catch (exception: CancellationException) {
                throw exception
            }
        }
    }

    private fun load(source: CommandSource, scriptId: String) {
        coroutineScope.launch {
            try {
                when (manager.loadScript(scriptId)) {
                    LoadResult.LOADED -> source.sendMessage(prefixMessage("Loaded script $scriptId"))
                    LoadResult.ALREADY_LOADED -> source.sendMessage(prefixMessage().append(Component.text("Script already loaded: $scriptId", NamedTextColor.RED)))
                    LoadResult.NOT_FOUND -> source.sendMessage(prefixMessage().append(Component.text("Script not found: $scriptId", NamedTextColor.RED)))
                    LoadResult.FAILED -> source.sendMessage(prefixMessage().append(Component.text("Failed to load script $scriptId; see console for details", NamedTextColor.RED)))
                }
            } catch (exception: CancellationException) {
                throw exception
            }
        }
    }

    private fun unload(source: CommandSource, scriptId: String) {
        coroutineScope.launch {
            try {
                when (manager.unloadScript(scriptId)) {
                    UnloadResult.UNLOADED -> source.sendMessage(prefixMessage("Unloaded script $scriptId"))
                    UnloadResult.NOT_LOADED -> source.sendMessage(prefixMessage().append(Component.text("Script not loaded: $scriptId", NamedTextColor.RED)))
                    UnloadResult.FAILED -> source.sendMessage(prefixMessage().append(Component.text("Failed to unload script $scriptId; see console for details", NamedTextColor.RED)))
                }
            } catch (exception: CancellationException) {
                throw exception
            }
        }
    }

    private fun list(source: CommandSource) {
        val ids: List<String> = manager.loadedScriptIds()
        if (ids.isEmpty()) {
            source.sendMessage(prefixMessage("No scripts loaded"))
            return
        }
        source.sendMessage(prefixMessage("Loaded ${ids.size} script(s):"))
        for (id: String in ids) {
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
        for (id: String in summary.failed.sorted()) {
            source.sendMessage(
                prefixMessage()
                    .append(Component.text("  - $id", NamedTextColor.RED))
            )
        }
    }

    private fun suggestLoadedSync(builder: SuggestionsBuilder) {
        val remaining: String = builder.remainingLowerCase
        for (id: String in manager.loadedScriptIds()) {
            if (id.contains(remaining, ignoreCase = true)) {
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
                    val loaded: Set<String> = manager.loadedScriptIds().toSet()
                    val unloaded: List<String> = manager.availableScriptIds()
                        .filter { id: String -> id !in loaded }
                        .sorted()
                    for (id: String in unloaded) {
                        if (id.contains(lowercasePrefix, ignoreCase = true)) {
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

    private fun prefixMessage(message: String = ""): Component {
        return Component.text()
            .append(Component.text("[", NamedTextColor.WHITE))
            .append(Component.text(prefix, NamedTextColor.GREEN))
            .append(Component.text("] ", NamedTextColor.WHITE))
            .append(Component.text(message))
            .build()
    }

    companion object {
        const val MAIN_COMMAND = "simplescriptvelocity"
        val COMMAND_ALIASES = listOf("ssv")
    }
}
