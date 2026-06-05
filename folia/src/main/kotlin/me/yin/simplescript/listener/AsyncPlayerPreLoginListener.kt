package me.yin.simplescript.listener

import me.yin.simplescript.SimpleScript
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent

class AsyncPlayerPreLoginListener(
    private val plugin: SimpleScript
) : Listener {
    @EventHandler
    fun onAsyncPlayerPreLogin(event: AsyncPlayerPreLoginEvent) {
        if (plugin.ready) {
            return
        }

        event.disallow(
            AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
            Component.text("服务器脚本仍在加载中，请稍后再试", NamedTextColor.RED)
        )
    }
}
