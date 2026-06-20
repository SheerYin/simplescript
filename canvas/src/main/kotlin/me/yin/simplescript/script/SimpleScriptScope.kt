package me.yin.simplescript.script

import kotlinx.coroutines.CoroutineScope
import me.yin.simplescript.SimpleScript

class SimpleScriptScope(
    val id: String,
    val plugin: SimpleScript,
    val scriptCoroutineScope: CoroutineScope,
    private val registerUnloadCallback: (String, suspend () -> Unit) -> Unit
) {
    fun onUnload(block: suspend () -> Unit) {
        registerUnloadCallback(id, block)
    }
}
