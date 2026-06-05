# SimpleScript

> 本文件仅供人类阅读。AI/agent 不要参考本文件作为项目协作依据；需要协作提示时请看 `agent-guide.markdown`，并以用户最新要求和当前项目实际状态为准。

SimpleScript 是一个面向 Minecraft 服务端的 Kotlin 脚本插件项目，提供 Paper/Folia 与 Velocity 两侧的脚本加载、卸载、重载和列表命令。

## 功能

- 启动时加载插件数据目录 `scripts` 下的 `.kts` 脚本。
- 支持通过命令加载、卸载、重载脚本。
- 脚本可以通过 `onClose { ... }` 注册关闭回调。
- 脚本未全部加载完成前，会阻止玩家进入服务器。
- 构建产物包含普通 jar 和 shadow jar。

## 脚本目录

脚本文件放在插件数据目录下：

```text
plugins/SimpleScript/scripts/*.kts
```

文件名去掉 `.kts` 后缀后作为脚本 id。例如：

```text
scripts/example.kts -> example
```

## 命令

```text
/simplescript reload
/simplescript reload <id>
/simplescript load <id>
/simplescript unload <id>
/simplescript list
```

## 脚本示例

最小脚本：

```kotlin
plugin.slF4JLogger.info("script {} loaded", id)

onClose {
    plugin.slF4JLogger.info("script {} closed", id)
}
```

Folia/Paper 脚本示例：

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

Velocity 脚本示例：

```kotlin
logger.info("Velocity has {} online player(s)", proxy.playerCount)

proxy.allPlayers.forEach { player ->
    player.sendMessage(net.kyori.adventure.text.Component.text("SimpleScript loaded: $id"))
}

onClose {
    logger.info("cleanup velocity script {}", id)
}
```

Folia/Paper 命令示例：

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

Velocity 命令示例：

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
    .plugin(plugin)
    .build()

proxy.commandManager.register(meta, command)

onClose {
    proxy.commandManager.unregister(meta)
}
```

Folia/Paper 事件示例：

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

Velocity 事件示例：

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

proxy.eventManager.register(plugin, listener)

onClose {
    proxy.eventManager.unregisterListener(plugin, listener)
}
```

## 构建

在项目根目录执行：

```powershell
.\gradlew.bat build
```

Linux/macOS：

```bash
./gradlew build
```

## 产物

```text
folia/build/libs/SimpleScript-folia-shadow.jar
velocity/build/libs/SimpleScript-velocity-shadow.jar
```

## 备注

项目使用 Kotlin、Gradle、Shadow、paperweight userdev、Paper/Folia API 与 Velocity API。配置、依赖和具体 API 使用方式以当前源码为准。
