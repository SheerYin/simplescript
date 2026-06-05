package me.yin.simplescript.velocity.listener

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PreLoginEvent
import me.yin.simplescript.velocity.SimpleScriptVelocity
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class PreLoginListener(
    private val simpleScriptVelocity: SimpleScriptVelocity
) {
    @Subscribe
    fun onPreLogin(event: PreLoginEvent) {
        if (simpleScriptVelocity.ready) {
            return
        }

        event.result = PreLoginEvent.PreLoginComponentResult.denied(
            Component.text("服务器脚本仍在加载中，请稍后再试", NamedTextColor.RED)
        )
    }
}
