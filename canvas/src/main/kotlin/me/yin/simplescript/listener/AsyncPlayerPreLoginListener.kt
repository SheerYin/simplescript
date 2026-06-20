package me.yin.simplescript.listener

import me.yin.simplescript.SimpleScript
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent

class AsyncPlayerPreLoginListener(
    private val simpleScript: SimpleScript
) : Listener {
    @EventHandler
    fun onAsyncPlayerPreLogin(event: AsyncPlayerPreLoginEvent) {
        if (simpleScript.ready) {
            return
        }

        event.disallow(
            AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
            Component.text("服务器脚本仍在加载中，请稍后再试", NamedTextColor.RED)
        )
    }
}
