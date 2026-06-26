import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

context.logger.info("test script {} loaded", context.scriptId)

context.simpleScript.globalRegionScheduler {
    context.simpleScript.server.onlinePlayers.forEach { player ->
        player.sendMessage(
            Component.text("SimpleScript test loaded: ${context.scriptId}", NamedTextColor.GREEN)
        )
    }
}

context.onUnload {
    context.simpleScript.globalRegionSchedulerOrRun {
        context.logger.info("test script {} unloaded", context.scriptId)
    }
}
