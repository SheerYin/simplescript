package me.yin.simplescript.script

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.yin.simplescript.SimpleScript
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

class SimpleScriptCommand(
    private val plugin: SimpleScript,
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
        plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(
                rootCommand(),
                "SimpleScript commands",
                COMMAND_ALIASES
            )
        }
    }

    private fun rootCommand() = Commands.literal(MAIN_COMMAND)
        .requires { source -> hasPermission(source, basePermission) }
        .executes { context ->
            sendHelp(context.source.sender)
            return@executes 1
        }
        .then(
            Commands.literal("reload")
                .requires { source -> hasPermission(source, permissionScriptReload) }
                .executes { context ->
                    reload(context.source.sender)
                    return@executes 1
                }
                .then(
                    Commands.argument("id", StringArgumentType.greedyString())
                        .suggests { _, builder ->
                            suggestLoadedSync(builder)
                            builder.buildFuture()
                        }
                        .executes { context ->
                            reload(
                                context.source.sender,
                                StringArgumentType.getString(context, "id")
                            )
                            return@executes 1
                        }
                )
        )
        .then(
            Commands.literal("load")
                .requires { source -> hasPermission(source, permissionScriptLoad) }
                .then(
                    Commands.argument("id", StringArgumentType.greedyString())
                        .suggests { _, builder -> suggestUnloadedAsync(builder) }
                        .executes { context ->
                            load(
                                context.source.sender,
                                StringArgumentType.getString(context, "id")
                            )
                            return@executes 1
                        }
                )
        )
        .then(
            Commands.literal("unload")
                .requires { source -> hasPermission(source, permissionScriptUnload) }
                .then(
                    Commands.argument("id", StringArgumentType.greedyString())
                        .suggests { _, builder ->
                            suggestLoadedSync(builder)
                            builder.buildFuture()
                        }
                        .executes { context ->
                            unload(
                                context.source.sender,
                                StringArgumentType.getString(context, "id")
                            )
                            return@executes 1
                        }
                )
        )
        .then(
            Commands.literal("list")
                .requires { source -> hasPermission(source, permissionScriptList) }
                .executes { context ->
                    list(context.source.sender)
                    return@executes 1
                }
        )
        .build()

    private fun reload(sender: CommandSender) {
        coroutineScope.launch {
            try {
                plugin.ready = false
                val summary = scriptManager.reloadScripts()
                plugin.ready = true
                sendLoadSummary(sender, "Reloaded", summary)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                plugin.slF4JLogger.error("Failed to reload scripts", exception)
                sender.sendMessage(prefixMessage().append(Component.text("Failed to reload scripts: ${exception.message ?: exception.javaClass.simpleName}", NamedTextColor.RED)))
            }
        }
    }

    private fun reload(sender: CommandSender, scriptId: String) {
        coroutineScope.launch {
            try {
                when (scriptManager.reloadScript(scriptId)) {
                    ReloadResult.RELOADED -> sender.sendMessage(prefixMessage("Reloaded script $scriptId"))
                    ReloadResult.NOT_LOADED -> sender.sendMessage(prefixMessage().append(Component.text("Script not loaded: $scriptId", NamedTextColor.RED)))
                    ReloadResult.ALREADY_LOADED -> sender.sendMessage(prefixMessage().append(Component.text("Script already loaded: $scriptId", NamedTextColor.RED)))
                    ReloadResult.NOT_FOUND -> sender.sendMessage(prefixMessage().append(Component.text("Script file missing: $scriptId", NamedTextColor.RED)))
                    ReloadResult.UNLOAD_FAILED -> sender.sendMessage(prefixMessage().append(Component.text("Failed to unload script $scriptId; see console for details", NamedTextColor.RED)))
                    ReloadResult.LOAD_FAILED -> sender.sendMessage(prefixMessage().append(Component.text("Failed to load script $scriptId; see console for details", NamedTextColor.RED)))
                }
            } catch (exception: CancellationException) {
                throw exception
            }
        }
    }

    private fun load(sender: CommandSender, scriptId: String) {
        coroutineScope.launch {
            try {
                when (scriptManager.loadScript(scriptId)) {
                    LoadResult.LOADED -> sender.sendMessage(prefixMessage("Loaded script $scriptId"))
                    LoadResult.ALREADY_LOADED -> sender.sendMessage(prefixMessage().append(Component.text("Script already loaded: $scriptId", NamedTextColor.RED)))
                    LoadResult.NOT_FOUND -> sender.sendMessage(prefixMessage().append(Component.text("Script not found: $scriptId", NamedTextColor.RED)))
                    LoadResult.FAILED -> sender.sendMessage(prefixMessage().append(Component.text("Failed to load script $scriptId; see console for details", NamedTextColor.RED)))
                }
            } catch (exception: CancellationException) {
                throw exception
            }
        }
    }

    private fun unload(sender: CommandSender, scriptId: String) {
        coroutineScope.launch {
            try {
                when (scriptManager.unloadScript(scriptId)) {
                    UnloadResult.UNLOADED -> sender.sendMessage(prefixMessage("Unloaded script $scriptId"))
                    UnloadResult.NOT_LOADED -> sender.sendMessage(prefixMessage().append(Component.text("Script not loaded: $scriptId", NamedTextColor.RED)))
                    UnloadResult.FAILED -> sender.sendMessage(prefixMessage().append(Component.text("Failed to unload script $scriptId; see console for details", NamedTextColor.RED)))
                }
            } catch (exception: CancellationException) {
                throw exception
            }
        }
    }

    private fun list(sender: CommandSender) {
        val ids = scriptManager.unloadCallbacksByScriptId.keys.sorted()
        if (ids.isEmpty()) {
            sender.sendMessage(prefixMessage("No scripts loaded"))
            return
        }
        sender.sendMessage(prefixMessage("Loaded ${ids.size} script(s):"))
        for (id in ids) {
            sender.sendMessage(prefixMessage("  - $id"))
        }
    }

    private fun sendLoadSummary(sender: CommandSender, action: String, summary: LoadSummary) {
        if (summary.failed.isEmpty()) {
            sender.sendMessage(prefixMessage("$action ${summary.loaded} script(s)"))
            return
        }

        sender.sendMessage(
            prefixMessage()
                .append(Component.text("$action ${summary.loaded} script(s), failed ${summary.failed.size} script(s)", NamedTextColor.RED))
        )
        for (id in summary.failed.sorted()) {
            sender.sendMessage(
                prefixMessage()
                    .append(Component.text("  - $id", NamedTextColor.RED))
            )
        }
    }

    private fun suggestLoadedSync(builder: SuggestionsBuilder) {
        val remaining = builder.remainingLowerCase
        for (id in scriptManager.unloadCallbacksByScriptId.keys.sorted()) {
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
                    val loaded = scriptManager.unloadCallbacksByScriptId.keys
                    val unloaded = scriptManager.availableScriptIds()
                        .filter { it !in loaded }
                        .sorted()
                    for (id in unloaded) {
                        if (id.contains(lowercasePrefix, ignoreCase = true)) {
                            builder.suggest(id)
                        }
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                plugin.slF4JLogger.warn("Failed to suggest unloaded scripts for prefix {}", prefix, exception)
            }

            builder.build()
        }
    }

    private fun hasPermission(source: CommandSourceStack, permission: String): Boolean {
        val sender = source.sender
        return sender !is Player || sender.hasPermission(permission)
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(prefixMessage("/$MAIN_COMMAND reload"))
        sender.sendMessage(prefixMessage("/$MAIN_COMMAND reload <id>"))
        sender.sendMessage(prefixMessage("/$MAIN_COMMAND load <id>"))
        sender.sendMessage(prefixMessage("/$MAIN_COMMAND unload <id>"))
        sender.sendMessage(prefixMessage("/$MAIN_COMMAND list"))
        sender.sendMessage(prefixMessage("Aliases: ${COMMAND_ALIASES.joinToString { "/$it" }}"))
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
        val COMMAND_ALIASES = listOf("ss")
    }
}
