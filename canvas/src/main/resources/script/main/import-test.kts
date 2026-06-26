@file:Import("shared/messages.kts")

context.logger.info("import test script {} loaded", context.scriptId)

val message = greenMessage("SimpleScript import test loaded: ${context.scriptId}")

context.simpleScript.globalRegionScheduler {
    context.simpleScript.server.onlinePlayers.forEach { player ->
        player.sendMessage(message)
    }
}
