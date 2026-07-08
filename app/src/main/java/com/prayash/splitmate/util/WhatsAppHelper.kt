package com.prayash.splitmate.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Opens WhatsApp chats with a pre-filled message (the user taps Send). This is the only
 * ban-safe way to message arbitrary contacts — there is no official personal-account send API.
 */
object WhatsAppHelper {

    /**
     * Normalise a raw phone number to the digits WhatsApp expects (country code + number, no +).
     * 10-digit numbers get [countryCode] prepended; anything already carrying a code is kept.
     */
    fun normalize(raw: String, countryCode: String): String {
        var digits = raw.filter { it.isDigit() }
        // Strip a leading 0 (common local prefix) before applying country code.
        if (digits.length == 11 && digits.startsWith("0")) digits = digits.drop(1)
        if (digits.length == 10) digits = countryCode + digits
        return digits
    }

    /** Open a chat with [phoneDigits] (already normalised) pre-filled with [message]. */
    fun openChat(context: Context, phoneDigits: String, message: String): Boolean {
        val url = "https://wa.me/$phoneDigits?text=${Uri.encode(message)}"
        return launch(context, url)
    }

    /** Open WhatsApp's "share to a contact" sheet with [message] (used when we have no number). */
    fun openShare(context: Context, message: String): Boolean {
        val url = "https://wa.me/?text=${Uri.encode(message)}"
        return launch(context, url)
    }

    private fun launch(context: Context, url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.whatsapp")
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // WhatsApp not installed / business variant — retry without a fixed package.
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } catch (e2: Exception) {
                Toast.makeText(context, "Couldn't open WhatsApp", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }
}
