// Sumire Pad: compile-time stub for tools/typecheck-app.sh only (never packaged).
// Signatures mirror the real library for the members the pad code uses.
@file:Suppress("unused", "UNUSED_PARAMETER")
package com.kazumaproject.markdownhelperkeyboard

class FastInputHostActivity : android.app.Activity() {
    lateinit var editText: android.widget.EditText
    fun restartEditorInput(clearText: Boolean) {}
    fun requestImeForEditor() {}
}
