package me.yin.simplescript.newscript

import kotlinx.coroutines.CoroutineScope
import me.yin.simplescript.SimpleScript
import org.slf4j.Logger

class SimpleScriptContext(
    val scriptId: String,
    val simpleScript: SimpleScript,
    val logger: Logger,
    val scope: CoroutineScope,
    private val registerUnloadCallback: (suspend () -> Unit) -> Unit
) {
    fun onUnload(block: suspend () -> Unit) {
        registerUnloadCallback(block)
    }
}
