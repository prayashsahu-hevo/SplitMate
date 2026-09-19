package com.prayash.splitmate.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * On-device ring-buffer logs of captured notifications, so the payment parser can be
 * diagnosed WITHOUT adb/Logcat.
 *
 * Two buffers are kept:
 *  - [FILE]      every notification the listener sees. High volume; chat apps evict things fast.
 *  - [MONEY_FILE] only notifications that look money-related (see [looksFinancial]). Low volume,
 *                 so a real payment survives long enough to be exported even hours later.
 *
 * The money buffer is what the detection-coverage survey reads: it shows which UPI apps and
 * which bank apps actually post an outgoing-payment notification, and in what wording.
 */
object NotifLog {

    private const val FILE = "notif_log.txt"
    private const val MONEY_FILE = "money_log.txt"
    private const val MAX_LINES = 200
    private const val MAX_MONEY_LINES = 400
    private val lock = Any()

    /** A currency amount: ₹500 / Rs.500 / Rs 500 / INR 500. */
    private val CURRENCY_REGEX =
        Regex("""(?:₹|rs\.?|inr)\s*[0-9]""", RegexOption.IGNORE_CASE)

    /** Money-movement words, used with a bare number when no currency token is present. */
    private val MONEY_WORDS = listOf(
        "paid", "sent", "debited", "credited", "received", "spent", "payment",
        "transferred", "transaction", "txn", "upi", "a/c", "account", "balance"
    )

    private val HAS_DIGIT = Regex("""[0-9]""")

    /**
     * Broad on purpose: during the survey we would rather over-capture than miss the one
     * notification that proves a silent UPI app is covered by its bank.
     */
    fun looksFinancial(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val lower = text.lowercase()
        if (CURRENCY_REGEX.containsMatchIn(lower)) return true
        return HAS_DIGIT.containsMatchIn(lower) && MONEY_WORDS.any { lower.contains(it) }
    }

    /**
     * Record one captured notification. Always appended to the full log; also appended to the
     * money log when it looks financial.
     *
     * @param label human-readable app name, so a bank package is identifiable at a glance.
     */
    fun add(
        context: Context,
        pkg: String,
        title: String?,
        text: String?,
        matched: Boolean,
        label: String? = null
    ) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val flag = if (matched) "✅MATCH" else "—"
        val who = if (label.isNullOrBlank()) pkg else "$label  [$pkg]"
        val line = "[$ts] $flag  $who\n    title=${title ?: ""}\n    text=${text ?: ""}"

        append(context, FILE, line, MAX_LINES)
        if (looksFinancial(listOfNotNull(title, text).joinToString(" "))) {
            append(context, MONEY_FILE, line, MAX_MONEY_LINES)
        }
    }

    /**
     * @param alsoMoney mirror into the money log. Detection events belong there: it is the
     *                  view the debug screen opens on, and it is not evicted by chat traffic.
     */
    fun event(context: Context, message: String, alsoMoney: Boolean = false) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "[$ts] · $message"
        append(context, FILE, line, MAX_LINES)
        if (alsoMoney) append(context, MONEY_FILE, line, MAX_MONEY_LINES)
    }

    private fun append(context: Context, fileName: String, entry: String, maxLines: Int) {
        synchronized(lock) {
            val f = File(context.filesDir, fileName)
            val lines = if (f.exists())
                f.readText().split("\n\n").filter { it.isNotBlank() }.toMutableList()
            else mutableListOf()
            lines.add(entry)
            while (lines.size > maxLines) lines.removeAt(0)
            f.writeText(lines.joinToString("\n\n"))
        }
    }

    /** Full log, newest-first. */
    fun read(context: Context): String = readFile(context, FILE, "(no notifications captured yet)")

    /** Money-related log only, newest-first. */
    fun readMoney(context: Context): String =
        readFile(context, MONEY_FILE, "(no money-related notifications captured yet)")

    private fun readFile(context: Context, fileName: String, emptyMessage: String): String {
        synchronized(lock) {
            val f = File(context.filesDir, fileName)
            if (!f.exists()) return emptyMessage
            val entries = f.readText().split("\n\n").filter { it.isNotBlank() }
            return entries.asReversed().joinToString("\n\n").ifBlank { emptyMessage }
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            File(context.filesDir, FILE).delete()
            File(context.filesDir, MONEY_FILE).delete()
        }
    }
}
