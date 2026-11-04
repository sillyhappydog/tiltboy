// Sumire Pad: compile-time stub for tools/typecheck-app.sh only (never packaged).
// Signatures mirror the real library for the members the pad code uses.
@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.fragment.app

open class FragmentActivity : android.app.Activity()
open class Fragment {
    open fun onCreate(savedInstanceState: android.os.Bundle?) {}
    open fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: android.os.Bundle?): android.view.View? = null
    open fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {}
    open fun onStart() {}
    open fun onStop() {}
    open fun onResume() {}
    open fun onPause() {}
    fun requireContext(): android.content.Context = throw UnsupportedOperationException()
    val isAdded: Boolean get() = true
    val view: android.view.View? get() = null
    val activity: FragmentActivity? get() = null
    val resources: android.content.res.Resources get() = throw UnsupportedOperationException()
    fun getString(resId: Int): String = ""
    fun getString(resId: Int, vararg formatArgs: Any?): String = ""
    fun startActivity(intent: android.content.Intent) {}
}
