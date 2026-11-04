// Sumire Pad: compile-time stub for tools/typecheck-app.sh only (never packaged).
// Signatures mirror the real library for the members the pad code uses.
@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.test.platform.app

object InstrumentationRegistry {
    @JvmStatic fun getInstrumentation(): android.app.Instrumentation = throw UnsupportedOperationException()
}
