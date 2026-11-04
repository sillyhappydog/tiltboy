// Sumire Pad: compile-time stub for tools/typecheck-app.sh only (never packaged).
// Signatures mirror the real library for the members the pad code uses.
@file:Suppress("unused", "UNUSED_PARAMETER")
package org.robolectric.annotation

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION) annotation class Config(val sdk: IntArray = [], val qualifiers: String = "")
