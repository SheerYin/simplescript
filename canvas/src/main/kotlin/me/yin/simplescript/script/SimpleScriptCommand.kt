package me.yin.simplescript.script

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
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
import me.yin.simplescript.script.permission.SimpleScriptPermissions
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import java.util.concurrent.CompletableFuture

class SimpleScriptCommand(
    private val simpleScript: SimpleScript,
    private val manager: SimpleScriptManager,
    private val coroutineScope: CoroutineScope,
    private val permissions: SimpleScriptPermissions,
    private val prefix: String
) {
    @Volatile
    var scriptSuggestionSemaphore: Semaphore = Semaphore(2)

    fun register() {
        simpleScript.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(simpleScriptNode(), COMMAND_ALIASES)
        }
    }

    fun simpleScriptNode(): LiteralCommandNode<CommandSourceStack> {
        val root = Commands.literal(MAIN_COMMAND)
            .requires { source -> source.sender.hasPermission(permissions.simpleScriptCommand) }
            .then(reloadBuilder())
            .then(loadBuilder())
            .then(unloadBuilder())
            .then(listBuilder())

        return root.build()
    }

    fun reloadBuilder(name: String = "reload"): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal(name)
            .requires { source -> source.sender.hasPermission(permissions.reloadCommand) }
            .executes { context ->
                reload(context.source.sender)
                return@executes 1
            }
            .then(
                Commands.argument("id", StringArgumentType.greedyString())
                    .suggests { _, builder: SuggestionsBuilder ->
                        suggestLoadedSync(builder)
                        builder.buildFuture()
                    }
                    .executes { context ->
                        reload(
                            sender = context.source.sender,
                            scriptId = StringArgumentType.getString(context, "id")
                        )
                        return@executes 1
                    }
            )
    }

    fun loadBuilder(name: String = "load"): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal(name)
            .requires { source -> source.sender.hasPermission(permissions.loadCommand) }
            .then(
                Commands.argument("id", StringArgumentType.greedyString())
                    .suggests { _, builder: SuggestionsBuilder -> suggestUnloadedAsync(builder) }
                    .executes { context ->
                        load(
                            sender = context.source.sender,
                            scriptId = StringArgumentType.getString(context, "id")
                        )
                        return@executes 1
                    }
            )
    }

    fun unloadBuilder(name: String = "unload"): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal(name)
            .requires { source -> source.sender.hasPermission(permissions.unloadCommand) }
            .then(
                Commands.argument("id", StringArgumentType.greedyString())
                    .suggests { _, builder: SuggestionsBuilder ->
                        suggestLoadedSync(builder)
                        builder.buildFuture()
                    }
                    .executes { context ->
                        unload(
                            sender = context.source.sender,
                            scriptId = StringArgumentType.getString(context, "id")
                        )
                        return@executes 1
                    }
            )
    }

    fun listBuilder(name: String = "list"): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal(name)
            .requires { source -> source.sender.hasPermission(permissions.listCommand) }
            .executes { context ->
                list(context.source.sender)
                return@executes 1
            }
    }

    private fun reload(sender: CommandSender) {
        coroutineScope.launch {
            try {
                simpleScript.ready = false
                val summary: LoadSummary = manager.reloadScripts()
                simpleScript.ready = true
                sendLoadSummary(sender, "Reloaded", summary)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                simpleScript.ready = true
                simpleScript.slF4JLogger.error("Failed to reload scripts", exception)
                sender.sendMessage(errorMessage("Failed to reload scripts: ${exception.message ?: exception.javaClass.simpleName}"))
            }
        }
    }

    private fun reload(sender: CommandSender, scriptId: String) {
        coroutineScope.launch {
            try {
                when (manager.reloadScript(scriptId)) {
                    ReloadResult.RELOADED -> sender.sendMessage(prefixMessage("Reloaded script $scriptId"))
                    ReloadResult.NOT_LOADED -> sender.sendMessage(errorMessage("Script not loaded: $scriptId"))
                    ReloadResult.ALREADY_LOADED -> sender.sendMessage(errorMessage("Script already loaded: $scriptId"))
                    ReloadResult.NOT_FOUND -> sender.sendMessage(errorMessage("Script file missing: $scriptId"))
                    ReloadResult.UNLOAD_FAILED -> sender.sendMessage(errorMessage("Failed to unload script $scriptId; see console for details"))
                    ReloadResult.LOAD_FAILED -> sender.sendMessage(errorMessage("Failed to load script $scriptId; see console for details"))
                }
            } catch (exception: CancellationException) {
                throw exception
            }
        }
    }

    private fun load(sender: CommandSender, scriptId: String) {
        coroutineScope.launch {
            try {
                when (manager.loadScript(scriptId)) {
                    LoadResult.LOADED -> sender.sendMessage(prefixMessage("Loaded script $scriptId"))
                    LoadResult.ALREADY_LOADED -> sender.sendMessage(errorMessage("Script already loaded: $scriptId"))
                    LoadResult.NOT_FOUND -> sender.sendMessage(errorMessage("Script not found: $scriptId"))
                    LoadResult.FAILED -> sender.sendMessage(errorMessage("Failed to load script $scriptId; see console for details"))
                }
            } catch (exception: CancellationException) {
                throw exception
            }
        }
    }

    private fun unload(sender: CommandSender, scriptId: String) {
        coroutineScope.launch {
            try {
                when (manager.unloadScript(scriptId)) {
                    UnloadResult.UNLOADED -> sender.sendMessage(prefixMessage("Unloaded script $scriptId"))
                    UnloadResult.NOT_LOADED -> sender.sendMessage(errorMessage("Script not loaded: $scriptId"))
                    UnloadResult.FAILED -> sender.sendMessage(errorMessage("Failed to unload script $scriptId; see console for details"))
                }
            } catch (exception: CancellationException) {
                throw exception
            }
        }
    }

    private fun list(sender: CommandSender) {
        val ids: List<String> = manager.loadedScriptIds()
        if (ids.isEmpty()) {
            sender.sendMessage(prefixMessage("No scripts loaded"))
            return
        }

        sender.sendMessage(prefixMessage("Loaded ${ids.size} script(s):"))
        for (id: String in ids) {
            sender.sendMessage(prefixMessage("  - $id"))
        }
    }

    private fun sendLoadSummary(sender: CommandSender, action: String, summary: LoadSummary) {
        if (summary.failed.isEmpty()) {
            sender.sendMessage(prefixMessage("$action ${summary.loaded} script(s)"))
            return
        }

        sender.sendMessage(errorMessage("$action ${summary.loaded} script(s), failed ${summary.failed.size} script(s)"))
        for (id: String in summary.failed.sorted()) {
            sender.sendMessage(errorMessage("  - $id"))
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
        val prefix: String = builder.remaining
        val lowercasePrefix: String = builder.remainingLowerCase

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
                simpleScript.slF4JLogger.warn("Failed to suggest unloaded scripts for prefix {}", prefix, exception)
            }

            builder.build()
        }
    }

    private fun errorMessage(message: String): Component {
        return prefixMessage().append(Component.text(message, NamedTextColor.RED))
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
        const val MAIN_COMMAND: String = "simplescript"
        val COMMAND_ALIASES: List<String> = listOf("ss")
    }
}

