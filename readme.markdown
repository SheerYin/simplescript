# SimpleScript

> This file is intended for human readers only. AI agents should not use this file as project guidance. For collaboration guidance, use `agent-guide.markdown`, the latest user instructions, and the current project state.

SimpleScript is a Kotlin scripting plugin project for Minecraft servers. It provides script loading, unloading, reloading, and listing commands for Paper/Folia and Velocity.

## Features

- Loads `.kts` scripts from the plugin data directory's `scripts` folder on startup.
- Supports loading, unloading, reloading, and listing scripts through commands.
- Lets scripts register cleanup callbacks with `onClose { ... }`.
- Blocks players from joining until the startup script load pass has finished.
- Keeps startup resilient: a broken script is reported and cleaned up without preventing other scripts from loading.
- Produces both normal jars and shadow jars.

## What Scripts Can Do

Scripts run on the plugin classpath, so they can use the same APIs and bundled libraries as the plugin. Typical use cases include:

- Dynamically registering and unregistering Paper/Folia or Velocity commands.
- Registering and unregistering Paper/Folia or Velocity event listeners.
- Sending Adventure `Component` messages to players and command sources.
- Reading and writing JSON with kotlinx.serialization.
- Storing local data with SQLite.
- Connecting to PostgreSQL with JDBC or HikariCP.
- Communicating with Redis through Lettuce.
- Running background work with Kotlin coroutines.
- Using Folia-aware scheduling helpers such as `runGlobalRegionAndWait` and `runRegionAndWait` on the Folia/Paper side.

Scripts should clean up anything they register or open by using `onClose { ... }`.

## Script Directory

Place script files under the plugin data directory:

```text
plugins/SimpleScript/scripts/*.kts
```

The script id is the file name without the `.kts` suffix. For example:

```text
scripts/example.kts -> example
```

## Commands

Folia/Paper:

```text
/simplescript reload
/simplescript reload <id>
/simplescript load <id>
/simplescript unload <id>
/simplescript list
```

Alias: `/ss`

Velocity:

```text
/simplescriptvelocity reload
/simplescriptvelocity reload <id>
/simplescriptvelocity load <id>
/simplescriptvelocity unload <id>
/simplescriptvelocity list
```

Alias: `/ssv`

`load` starts scripts that are not already loaded. During startup and full reloads, scripts are loaded independently: if one script fails, SimpleScript cleans up that script's partial state, logs the failure, and continues loading the remaining scripts. System-level load failures, such as being unable to create or scan the script directory, keep the plugin in its not-ready state so players continue to be rejected until the problem is fixed.

`reload` is a command-level convenience operation:

```text
reload      = unload all + load all
reload <id> = unload <id> + load <id>
```

If a full reload has failures, the command reports how many scripts loaded and lists the failed script ids.

## Script Examples

Minimal script:

```kotlin
plugin.slF4JLogger.info("script {} loaded", id)

onClose {
    plugin.slF4JLogger.info("script {} closed", id)
}
```

Folia/Paper script example:

```kotlin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

plugin.server.onlinePlayers.forEach { player ->
    player.sendMessage(Component.text("SimpleScript loaded: $id", NamedTextColor.GREEN))
}

onClose {
    plugin.slF4JLogger.info("cleanup folia script {}", id)
}
```

Velocity script example:

```kotlin
logger.info("Velocity has {} online player(s)", proxy.playerCount)

proxy.allPlayers.forEach { player ->
    player.sendMessage(net.kyori.adventure.text.Component.text("SimpleScript loaded: $id"))
}

onClose {
    logger.info("cleanup velocity script {}", id)
}
```

Folia/Paper command example:

```kotlin
import io.papermc.paper.command.brigadier.ApiMirrorRootNode
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.PaperCommands
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component

val commandName = "hello"
val dispatcher = PaperCommands.INSTANCE.dispatcherInternal
val root = dispatcher.root as ApiMirrorRootNode

fun refreshCommands() {
    plugin.server.onlinePlayers.forEach { player ->
        player.scheduler.run(plugin, { _ -> player.updateCommands() }, null)
    }
}

val commandNode = Commands.literal(commandName)
    .executes { context ->
        context.source.sender.sendMessage(Component.text("Hello from script: $id"))
        1
    }
    .build()

runBlocking {
    plugin.runGlobalRegionAndWait {
        root.removeCommand(commandName)
        root.addChild(commandNode)
        refreshCommands()
    }
}

onClose {
    runBlocking {
        plugin.runGlobalRegionAndWait {
            root.removeCommand(commandName)
            refreshCommands()
        }
    }
}
```

Velocity command example:

```kotlin
import com.velocitypowered.api.command.BrigadierCommand
import net.kyori.adventure.text.Component

val command = BrigadierCommand(
    BrigadierCommand.literalArgumentBuilder("vhello")
        .executes { context ->
            context.source.sendMessage(Component.text("Hello from script: $id"))
            1
        }
        .build()
)

val meta = proxy.commandManager.metaBuilder(command)
    .plugin(simpleScriptVelocity)
    .build()

proxy.commandManager.register(meta, command)

onClose {
    proxy.commandManager.unregister(meta)
}
```

Folia/Paper event example:

```kotlin
import net.kyori.adventure.text.Component
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

val listener = object : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        event.player.sendMessage(Component.text("Welcome from script: $id"))
    }
}

plugin.server.pluginManager.registerEvents(listener, plugin)

onClose {
    PlayerJoinEvent.getHandlerList().unregister(listener)
}
```

Velocity event example:

```kotlin
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PostLoginEvent
import net.kyori.adventure.text.Component

val listener = object {
    @Subscribe
    fun onPostLogin(event: PostLoginEvent) {
        event.player.sendMessage(Component.text("Welcome from script: $id"))
    }
}

proxy.eventManager.register(simpleScriptVelocity, listener)

onClose {
    proxy.eventManager.unregisterListener(simpleScriptVelocity, listener)
}
```

## Build

Run from the project root:

```powershell
.\gradlew.bat build
```

Linux/macOS:

```bash
./gradlew build
```

## Artifacts

```text
folia/build/libs/SimpleScript-folia-shadow.jar
velocity/build/libs/SimpleScript-velocity-shadow.jar
```

## Notes

This project uses Kotlin, Gradle, Shadow, paperweight userdev, the Paper/Folia API, and the Velocity API. Configuration, dependencies, and API usage should be checked against the current source code.
