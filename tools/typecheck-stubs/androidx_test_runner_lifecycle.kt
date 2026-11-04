// Sumire Pad: compile-time stub for tools/typecheck-app.sh only (never packaged).
@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.test.runner.lifecycle

enum class Stage { PRE_ON_CREATE, CREATED, STARTED, RESUMED, PAUSED, STOPPED, RESTARTED, DESTROYED }
interface ActivityLifecycleMonitor {
    fun getActivitiesInStage(stage: Stage): Collection<android.app.Activity>
}
object ActivityLifecycleMonitorRegistry {
    @JvmStatic fun getInstance(): ActivityLifecycleMonitor = throw UnsupportedOperationException()
}
