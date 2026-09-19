package com.prayash.splitmate.service

import com.prayash.splitmate.data.Payment

/**
 * Turns a notification into a [Payment] — used only to recover the *amount*, since
 * [UpiUsageWatcher] is what decides a payment happened.
 *
 * Deliberately not restricted to a list of UPI packages. The notification that names the
 * amount often comes from the user's *bank* rather than the UPI app, especially for apps
 * that post nothing for outgoing payments. So any app's notification is fair game, and the
 * safety comes from the wording checks below rather than from the sender.
 */
object PaymentParser {

    private const val NUM = """([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    private const val CUR = """(?:₹|rs\.?|inr)"""

    /**
     * The number must start a token. Without this, "A/c XX1234 debited by 750" reads the
     * masked account number as the amount — Indian bank messages put one right before the verb.
     */
    private const val TOKEN_START = """(?<![A-Za-z0-9.])"""

    /** "₹500 debited" / "Rs 500 has been paid" — amount before the verb (bank wording). */
    private val AMOUNT_BEFORE_VERB =
        Regex("""$TOKEN_START(?:$CUR\s*)?$NUM\s+(?:has been\s+|is\s+|was\s+)?(?:debited|paid|sent|spent|transferred)""",
            RegexOption.IGNORE_CASE)

    /** "paid ₹500" / "debited by 500.00" — amount after the verb (UPI app wording). */
    private val AMOUNT_AFTER_VERB =
        Regex("""(?:paid|sent|debited|spent|transferred|payment of)\s+(?:by|of|for)?\s*$TOKEN_START(?:$CUR\s*)?$NUM""",
            RegexOption.IGNORE_CASE)

    /** Last resort: the first currency-tagged amount anywhere in the text. */
    private val AMOUNT_ANY = Regex("""$CUR\s*$NUM""", RegexOption.IGNORE_CASE)

    /** "to John Doe" up to a stop word, punctuation, or a "|" node separator. */
    private val VENDOR_REGEX =
        Regex("""\bto\s+([A-Za-z0-9][A-Za-z0-9 &.'@_-]{0,48}?)(?=\s+(?:on|via|using|for|ref|upi|txn|successfully|is|has|from)\b|[.!,\n|]|$)""",
            RegexOption.IGNORE_CASE)

    /** Bank refs like "UPI/P2P/512345678901/RAHUL KUMAR" carry the payee in the last segment. */
    private val UPI_REF_PAYEE =
        Regex("""UPI/[A-Z0-9]+/[0-9]+/([A-Za-z][A-Za-z ]{1,40})""", RegexOption.IGNORE_CASE)

    /** Money left the account. */
    private val OUTGOING_HINTS =
        listOf("paid", "sent", "debited", "spent", "payment of", "transferred", "you paid")

    /** Money arrived, or it is not a completed payment at all. */
    private val INCOMING_HINTS = listOf(
        "received", "credited", "added to", "request", "requesting", "cashback",
        "refund", "reminder", "will be", "failed", "declined", "pending", "cancelled"
    )

    /**
     * @param source human-readable name of the app that posted the notification, kept on the
     *               [Payment] so the watcher can prefer the UPI app's own wording over the bank's.
     * @return a [Payment] when this reads as a completed outgoing payment, else null.
     */
    fun parse(source: String, title: String?, text: String?, whenMillis: Long): Payment? {
        val combined = listOfNotNull(title, text).joinToString(" ").trim()
        if (combined.isEmpty()) return null

        val lower = combined.lowercase()

        // Must read as outgoing...
        if (OUTGOING_HINTS.none { lower.contains(it) }) return null
        // ...and must not read as incoming, a request, or a failure.
        if (INCOMING_HINTS.any { lower.contains(it) }) return null

        val amount = findAmount(combined) ?: return null

        val vendor = VENDOR_REGEX.find(combined)?.groupValues?.get(1)?.trim()
            ?: UPI_REF_PAYEE.find(combined)?.groupValues?.get(1)?.trim()
            ?: ""

        return Payment(
            amount = amount,
            vendor = vendor.takeIf { it.isNotBlank() }.orEmpty(),
            source = source,
            timestampMillis = whenMillis,
            rawText = combined
        )
    }

    /**
     * Most specific pattern wins, so a trailing "Bal Rs 12,340" in a bank message is never
     * mistaken for the amount paid.
     */
    private fun findAmount(text: String): Double? {
        val match = AMOUNT_BEFORE_VERB.find(text)
            ?: AMOUNT_AFTER_VERB.find(text)
            ?: AMOUNT_ANY.find(text)
        return match?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
    }
}
