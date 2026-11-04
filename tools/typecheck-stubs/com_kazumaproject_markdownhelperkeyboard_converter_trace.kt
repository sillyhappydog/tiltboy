// Sumire Pad: compile-time stub for tools/typecheck-app.sh only (never packaged).
// Signatures mirror the real library for the members the pad code uses.
@file:Suppress("unused", "UNUSED_PARAMETER")
package com.kazumaproject.markdownhelperkeyboard.converter.trace

data class CandidateTrace(val candidate: String, val yomi: String, val totalCost: Int, val path: List<String>)
data class ConversionTrace(val input: String, val finalCandidates: List<CandidateTrace>)
