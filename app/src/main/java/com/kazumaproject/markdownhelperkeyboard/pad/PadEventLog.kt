package com.kazumaproject.markdownhelperkeyboard.pad

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * In-memory log of controller events and what the keyboard decided. Shared between the
 * IME service and the diagnostics screen (same process), shown live there and saved to a
 * file the user can share. Holds the whole diagnostics session (up to [MAX_LINES] lines).
 */
object PadEventLog {
    const val MAX_LINES = 6000

    fun interface Listener { fun onLine(line: String) }

    private val lines = ArrayDeque<String>(MAX_LINES)
    private val listeners = mutableListOf<Listener>()
    private val main = Handler(Looper.getMainLooper())
    private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** Total lines ever added (survives trimming), used for session marks. */
    @Volatile var totalAdded: Long = 0L
        private set

    @Synchronized
    fun add(source: String, text: String) {
        val line = "${stamp.format(Date())} [$source] $text"
        if (lines.size >= MAX_LINES) lines.removeFirst()
        lines.addLast(line)
        totalAdded++
        if (listeners.isNotEmpty()) {
            val copy = listeners.toList()
            main.post { copy.forEach { it.onLine(line) } }
        }
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    /** Lines added since a session mark (a [totalAdded] value taken earlier). */
    @Synchronized
    fun since(mark: Long): List<String> {
        val all = lines.toList()
        val available = all.size.toLong()
        val wanted = (totalAdded - mark).coerceIn(0L, available).toInt()
        return all.takeLast(wanted)
    }

    @Synchronized
    fun clear() = lines.clear()

    @Synchronized
    fun addListener(l: Listener) { listeners += l }

    @Synchronized
    fun removeListener(l: Listener) { listeners -= l }

    /** Writes `<header>` + the given lines to `<external files>/pad_logs/pad-YYYYMMDD-HHmmss.txt` (visible in the Files app). */
    fun save(context: Context, header: String, body: List<String> = snapshot()): File {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "pad_logs").apply { mkdirs() }
        val name = "pad-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        val file = File(dir, name)
        file.writeText(header + "\n\n" + body.joinToString("\n") + "\n")
        return file
    }
}
