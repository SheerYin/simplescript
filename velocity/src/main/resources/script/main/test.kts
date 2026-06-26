import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

context.logger.info("velocity test script {} loaded", context.scriptId)

context.proxy.allPlayers.forEach { player ->
    player.sendMessage(
        Component.text("SimpleScript velocity test loaded: ${context.scriptId}", NamedTextColor.GREEN)
    )
}

context.onUnload {
    context.logger.info("velocity test script {} unloaded", context.scriptId)
}

