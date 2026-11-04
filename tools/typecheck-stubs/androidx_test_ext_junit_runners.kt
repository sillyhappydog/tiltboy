// Sumire Pad: compile-time stub for tools/typecheck-app.sh only (never packaged).
// Signatures mirror the real library for the members the pad code uses.
@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.test.ext.junit.runners

class AndroidJUnit4(klass: Class<*>) : org.junit.runner.Runner() {
    override fun getDescription(): org.junit.runner.Description = org.junit.runner.Description.EMPTY
    override fun run(notifier: org.junit.runner.notification.RunNotifier) {}
}
