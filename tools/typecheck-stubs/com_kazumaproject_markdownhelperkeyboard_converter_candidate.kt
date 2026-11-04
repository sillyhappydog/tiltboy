// Sumire Pad: compile-time stub for tools/typecheck-app.sh only (never packaged).
// Only the members the pad code uses (the real class has many more fields).
@file:Suppress("unused")
package com.kazumaproject.markdownhelperkeyboard.converter.candidate

data class Candidate(val string: String, val type: Byte = 1, val score: Int = 0)
