# SimpleScript

> This file is intended for human readers only. AI agents should not use this file as project guidance. For collaboration guidance, use `agent-guide.markdown`, the latest user instructions, and the current project state.

SimpleScript is a Kotlin scripting plugin project for Minecraft servers. It provides script loading, unloading, reloading, and listing commands for Canvas and Velocity.

## Features

- Loads `.kts` scripts from the plugin data directory's `scripts` folder on startup.
- Supports loading, unloading, reloading, and listing scripts through commands.
- Lets scripts register suspend cleanup callbacks with `onUnload { ... }`.
- Gives each script its own `scriptCoroutineScope`, which is cancelled after unload cleanup finishes.
- Blocks players from joining until the startup script load pass has finished.
- Keeps startup resilient: a broken script is reported and cleaned up without preventing other scripts from loading.
- Produces both normal jars and shadow jars.

## What Scripts Can Do

Scripts run on the plugin classpath, so they can use the same APIs and bundled libraries as the plugin. Typical use cases include:

- Dynamically registering and unregistering Canvas or Velocity commands.
- Registering and unregistering Canvas or Velocity event listeners.
- Sending Adventure `Component` messages to players and command sources.
- Reading and writing JSON with kotlinx.serialization.
- Storing local data with SQLite.
- Connecting to PostgreSQL with JDBC or HikariCP.
- Communicating with Redis through Lettuce.
- Running background work with Kotlin coroutines through `scriptCoroutineScope`.
- Scheduling Bukkit, Paper, and Canvas API access through `globalRegionScheduler`, `regionScheduler`, and `entityScheduler` on the Canvas side.

On the Canvas side, scripts are evaluated by SimpleScript and can schedule Bukkit, Paper, and Canvas API access through the plugin helpers:

```kotlin
plugin.globalRegionScheduler { ... }
plugin.regionScheduler(world, chunkX, chunkZ) { ... }
plugin.entityScheduler(entity) { ... }
```

These helpers are the recommended default because they keep scripts concise and centralize shutdown behavior. If a coroutine resumes after `delay`, or an `onUnload` cleanup runs while the plugin/server is shutting down, submitting a new task with a disabled plugin can throw. `globalRegionScheduler` runs `block()` immediately when the plugin is already disabled so global cleanup can still finish; region and entity tasks are discarded when they can no longer be scheduled safely. Scripts that need finer control over submission, dropping work, retired callbacks, exception handling, or exact thread semantics can still use the native Canvas schedulers directly.

`onUnload { ... }` still runs while the plugin is being disabled. Synchronous cleanup such as unregistering listeners, removing command nodes, closing files, closing database connections, closing Redis clients, or cancelling coroutine jobs can still run. For Bukkit, Paper, or Canvas scheduled work, scripts can call the plugin scheduler helpers directly.

Scripts should clean up anything they register or open by using `onUnload { ... }`. Each script may register at most one unload callback; if a script does not register one, SimpleScript still tracks it and cancels its `scriptCoroutineScope` on unload.

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

Canvas:

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

onUnload {
    plugin.slF4JLogger.info("script {} closed", id)
}
```

Canvas script example:

```kotlin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

plugin.globalRegionScheduler {
    plugin.server.onlinePlayers.forEach { player ->
        player.sendMessage(Component.text("SimpleScript loaded: $id", NamedTextColor.GREEN))
    }
}

onUnload {
    plugin.globalRegionScheduler {
        plugin.slF4JLogger.info("cleanup canvas script {}", id)
    }
}
```

Velocity script example:

```kotlin
logger.info("Velocity has {} online player(s)", proxy.playerCount)

proxy.allPlayers.forEach { player ->
    player.sendMessage(net.kyori.adventure.text.Component.text("SimpleScript loaded: $id"))
}

onUnload {
    logger.info("cleanup velocity script {}", id)
}
```

Canvas command example:

```kotlin
import io.papermc.paper.command.brigadier.ApiMirrorRootNode
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.PaperCommands
import net.kyori.adventure.text.Component

val commandName = "hello"
val dispatcher = PaperCommands.INSTANCE.dispatcherInternal
val root = dispatcher.root as ApiMirrorRootNode

fun refreshCommands() {
    plugin.server.onlinePlayers.forEach { player ->
        plugin.entityScheduler(player) { player.updateCommands() }
    }
}

val commandNode = Commands.literal(commandName)
    .executes { context ->
        context.source.sender.sendMessage(Component.text("Hello from script: $id"))
        1
    }
    .build()

plugin.globalRegionScheduler {
    root.removeCommand(commandName)
    root.addChild(commandNode)
    refreshCommands()
}

onUnload {
    plugin.globalRegionScheduler {
        root.removeCommand(commandName)
        refreshCommands()
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

onUnload {
    proxy.commandManager.unregister(meta)
}
```

Canvas event example:

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

plugin.globalRegionScheduler {
    plugin.server.pluginManager.registerEvents(listener, plugin)
}

onUnload {
    plugin.globalRegionScheduler {
        PlayerJoinEvent.getHandlerList().unregister(listener)
    }
}
```

Velocity event example:

```kotlin
import com.velocitypowered.api.event.EventHandler
import com.velocitypowered.api.event.connection.PostLoginEvent
import net.kyori.adventure.text.Component

val handler = EventHandler<PostLoginEvent> { event ->
    event.player.sendMessage(Component.text("Welcome from script: $id"))
}

proxy.eventManager.register(simpleScriptVelocity, PostLoginEvent::class.java, Short.MAX_VALUE, handler)

onUnload {
    proxy.eventManager.unregister(simpleScriptVelocity, handler)
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
canvas/build/libs/SimpleScript-canvas-shadow.jar
velocity/build/libs/SimpleScript-velocity-shadow.jar
```

## Notes

This project uses Kotlin, Gradle, Shadow, Canvas Weaver userdev, the Canvas/Paper API, and the Velocity API. Configuration, dependencies, and API usage should be checked against the current source code.
