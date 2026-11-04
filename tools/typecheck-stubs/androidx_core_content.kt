// Sumire Pad: compile-time stub for tools/typecheck-app.sh only (never packaged).
// Signatures mirror the real library for the members the pad code uses.
@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.core.content

class FileProvider {
    companion object {
        @JvmStatic fun getUriForFile(context: android.content.Context, authority: String, file: java.io.File): android.net.Uri = throw UnsupportedOperationException()
    }
}
