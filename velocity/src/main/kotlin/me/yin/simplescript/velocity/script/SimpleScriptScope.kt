package me.yin.simplescript.velocity.script

import com.velocitypowered.api.proxy.ProxyServer
import me.yin.simplescript.velocity.SimpleScriptVelocity
import org.slf4j.Logger
import java.nio.file.Path

class SimpleScriptScope(
    val id: String,
    val simpleScriptVelocity: SimpleScriptVelocity,
    val proxy: ProxyServer,
    val dataDirectory: Path,
    val logger: Logger,
    private val closeHandler: (String, () -> Unit) -> Unit
) {
    fun onClose(block: () -> Unit) {
        closeHandler(id, block)
    }
}
