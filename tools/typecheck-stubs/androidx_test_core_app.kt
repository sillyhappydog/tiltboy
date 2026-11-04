// Sumire Pad: compile-time stub for tools/typecheck-app.sh only (never packaged).
// Signatures mirror the real library for the members the pad code uses.
@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.test.core.app

class ActivityScenario<A : android.app.Activity> {
    fun onActivity(action: (A) -> Unit) {}
    fun close() {}
    companion object {
        @JvmStatic fun <A : android.app.Activity> launch(activityClass: Class<A>): ActivityScenario<A> = ActivityScenario()
    }
}

object ApplicationProvider {
    @JvmStatic fun <T : android.content.Context> getApplicationContext(): T = throw UnsupportedOperationException()
}
