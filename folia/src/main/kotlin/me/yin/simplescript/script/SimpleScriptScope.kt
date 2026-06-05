package me.yin.simplescript.script

import me.yin.simplescript.SimpleScript

class SimpleScriptScope(
    val id: String,
    val plugin: SimpleScript,
    private val closeHandler: (String, () -> Unit) -> Unit
) {
    fun onClose(block: () -> Unit) {
        closeHandler(id, block)
    }
}
