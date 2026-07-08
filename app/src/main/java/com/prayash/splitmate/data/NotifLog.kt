package com.prayash.splitmate.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A tiny on-device ring-buffer log of captured notifications, so we can diagnose the payment
 * parser WITHOUT adb/Logcat. Every notification the listener sees is appended here; the app's
 * debug screen shows it newest-first and lets you copy it.
 */
object NotifLog {

    private const val FILE = "notif_log.txt"
    private const val MAX_LINES = 200
    private val lock = Any()

    fun add(context: Context, pkg: String, title: String?, text: String?, matched: Boolean) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val flag = if (matched) "✅MATCH" else "—"
        val line = "[$ts] $flag  pkg=$pkg\n    title=${title ?: ""}\n    text=${text ?: ""}"
        append(context, line)
    }

    fun event(context: Context, message: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        append(context, "[$ts] · $message")
    }

    private fun append(context: Context, entry: String) {
        synchronized(lock) {
            val f = File(context.filesDir, FILE)
            val lines = if (f.exists()) f.readText().split("\n\n").filter { it.isNotBlank() }.toMutableList()
            else mutableListOf()
            lines.add(entry)
            while (lines.size > MAX_LINES) lines.removeAt(0)
            f.writeText(lines.joinToString("\n\n"))
        }
    }

    /** Returns the log newest-first. */
    fun read(context: Context): String {
        synchronized(lock) {
            val f = File(context.filesDir, FILE)
            if (!f.exists()) return "(no notifications captured yet)"
            val entries = f.readText().split("\n\n").filter { it.isNotBlank() }
            return entries.asReversed().joinToString("\n\n").ifBlank { "(empty)" }
        }
    }

    fun clear(context: Context) {
        synchronized(lock) { File(context.filesDir, FILE).delete() }
    }
}
