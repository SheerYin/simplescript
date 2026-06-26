@file:Import("shared/messages.kts")

import net.kyori.adventure.text.Component

context.logger.info("velocity import test script {} loaded", context.scriptId)

val message: Component = greenMessage("SimpleScript velocity import test loaded: ${context.scriptId}")
context.proxy.allPlayers.forEach { player ->
    player.sendMessage(message)
}
