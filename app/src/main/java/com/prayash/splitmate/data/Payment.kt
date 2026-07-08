package com.prayash.splitmate.data

import java.io.Serializable

/**
 * A parsed UPI payment, extracted from a notification.
 * All fields are best-effort; vendor/amount may be blank if parsing failed.
 */
data class Payment(
    val amount: Double,          // 0.0 if it could not be parsed
    val vendor: String,          // "" if it could not be parsed
    val source: String,          // "Google Pay" / "Paytm"
    val timestampMillis: Long,   // when the notification was posted
    val rawText: String          // original notification text (for debugging / manual fixup)
) : Serializable
