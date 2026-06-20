package me.yin.simplescript.velocity.script

import com.velocitypowered.api.proxy.ProxyServer
import kotlinx.coroutines.CoroutineScope
import me.yin.simplescript.velocity.SimpleScriptVelocity
import org.slf4j.Logger
import java.nio.file.Path

class SimpleScriptContext(
    val scriptId: String,
    val simpleScriptVelocity: SimpleScriptVelocity,
    val proxy: ProxyServer,
    val dataDirectory: Path,
    val logger: Logger,
    val scope: CoroutineScope,
    private val registerUnloadCallback: (String, suspend () -> Unit) -> Unit
) {
    fun onUnload(block: suspend () -> Unit) {
        registerUnloadCallback(scriptId, block)
    }
}
