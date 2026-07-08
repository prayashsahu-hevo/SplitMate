package com.prayash.splitmate.service

import com.prayash.splitmate.data.Payment

/**
 * Turns a raw UPI notification (title + text) into a [Payment].
 *
 * UPI apps word their notifications differently and change them over time, so this is
 * deliberately forgiving: we look for an amount token and a "to <vendor>" clause, and we
 * only treat a notification as an *outgoing* payment when it clearly reads as one.
 */
object PaymentParser {

    // Packages we care about -> friendly source name.
    val SUPPORTED_PACKAGES = mapOf(
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "net.one97.paytm" to "Paytm"
    )

    // ₹500 / Rs.500 / Rs 500 / INR 500 / 500.00  (captures the number)
    private val AMOUNT_REGEX =
        Regex("""(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)

    // "to John Doe" / "to Big Bazaar" up to a stop word or punctuation.
    private val VENDOR_REGEX =
        Regex("""\bto\s+([A-Za-z0-9][A-Za-z0-9 &.'@_-]{0,48}?)(?=\s+(?:on|via|using|for|ref|upi|txn|successfully|is|has)\b|[.!,\n]|$)""",
            RegexOption.IGNORE_CASE)

    // Words that indicate money LEFT your account.
    private val OUTGOING_HINTS = listOf("paid", "sent", "debited", "you paid", "payment of", "spent")

    // Words that indicate an incoming / non-payment notification we must ignore.
    private val INCOMING_HINTS = listOf("received", "credited", "added to", "request", "requesting", "cashback", "refund")

    /**
     * @return a [Payment] if this looks like an outgoing UPI payment, else null.
     */
    fun parse(pkg: String, title: String?, text: String?, whenMillis: Long): Payment? {
        val source = SUPPORTED_PACKAGES[pkg] ?: return null
        val combined = listOfNotNull(title, text).joinToString(" ").trim()
        if (combined.isEmpty()) return null

        val lower = combined.lowercase()

        // Skip clearly-incoming notifications.
        if (INCOMING_HINTS.any { lower.contains(it) } && OUTGOING_HINTS.none { lower.contains(it) }) {
            return null
        }
        // Require some sign this is an outgoing payment.
        if (OUTGOING_HINTS.none { lower.contains(it) }) return null

        val amount = AMOUNT_REGEX.find(combined)
            ?.groupValues?.get(1)
            ?.replace(",", "")
            ?.toDoubleOrNull()
            ?: return null            // no amount => not useful, bail out

        val vendor = VENDOR_REGEX.find(combined)
            ?.groupValues?.get(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: (title?.trim().orEmpty())   // fall back to the title

        return Payment(
            amount = amount,
            vendor = vendor,
            source = source,
            timestampMillis = whenMillis,
            rawText = combined
        )
    }
}
