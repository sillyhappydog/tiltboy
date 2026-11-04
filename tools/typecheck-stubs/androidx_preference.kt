// Sumire Pad: compile-time stub for tools/typecheck-app.sh only (never packaged).
// Signatures mirror the real library for the members the pad code uses.
@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.preference

abstract class Preference(val context: android.content.Context) {
    var key: String? = null
    var title: CharSequence? = null
    var summary: CharSequence? = null
    var isIconSpaceReserved: Boolean = true
    var isVisible: Boolean = true
    var summaryProvider: SummaryProvider<*>? = null
    fun setDefaultValue(defaultValue: Any?) {}
    fun setOnPreferenceClickListener(l: OnPreferenceClickListener?) {}
    fun setOnPreferenceChangeListener(l: OnPreferenceChangeListener?) {}
    fun interface OnPreferenceClickListener { fun onPreferenceClick(preference: Preference): Boolean }
    fun interface OnPreferenceChangeListener { fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean }
    fun interface SummaryProvider<T : Preference> { fun provideSummary(preference: T): CharSequence? }
}
open class PreferenceGroup(context: android.content.Context) : Preference(context) {
    val preferenceCount: Int get() = 0
    fun addPreference(preference: Preference): Boolean = true
    fun removeAll() {}
}
class PreferenceCategory(context: android.content.Context) : PreferenceGroup(context)
class ListPreference(context: android.content.Context) : Preference(context) {
    var entries: Array<out CharSequence>? = null
    var entryValues: Array<out CharSequence>? = null
    var value: String? = null
    val entry: CharSequence? get() = null
    class SimpleSummaryProvider : SummaryProvider<ListPreference> {
        override fun provideSummary(preference: ListPreference): CharSequence? = null
        companion object { @JvmStatic fun getInstance(): SimpleSummaryProvider = SimpleSummaryProvider() }
    }
}
class MultiSelectListPreference(context: android.content.Context) : Preference(context) {
    var entries: Array<out CharSequence>? = null
    var entryValues: Array<out CharSequence>? = null
    var values: Set<String> = emptySet()
}
class PreferenceManager(val context: android.content.Context) {
    companion object {
        @JvmStatic fun getDefaultSharedPreferences(context: android.content.Context): android.content.SharedPreferences = throw UnsupportedOperationException()
    }
}
