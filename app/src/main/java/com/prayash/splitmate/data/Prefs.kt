package com.prayash.splitmate.data

import android.content.Context

/**
 * Thin wrapper over SharedPreferences for the handful of settings the user configures.
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("splitmate_prefs", Context.MODE_PRIVATE)

    /** The Google Apps Script web-app URL (…/exec) that rows are POSTed to. */
    var scriptUrl: String
        get() = sp.getString(KEY_SCRIPT_URL, "") ?: ""
        set(value) = sp.edit().putString(KEY_SCRIPT_URL, value.trim()).apply()

    /** Your own display name, used inside the split messages. */
    var ownName: String
        get() = sp.getString(KEY_OWN_NAME, "") ?: ""
        set(value) = sp.edit().putString(KEY_OWN_NAME, value.trim()).apply()

    /**
     * Your own WhatsApp number (digits only, with country code, e.g. 9198XXXXXXXX).
     * Used to send yourself the "chase these people" reminder note.
     */
    var ownWhatsApp: String
        get() = sp.getString(KEY_OWN_WA, "") ?: ""
        set(value) = sp.edit().putString(KEY_OWN_WA, value.filter { it.isDigit() }).apply()

    /** Default country code prepended to 10-digit contact numbers. Default: 91 (India). */
    var countryCode: String
        get() = sp.getString(KEY_CC, "91") ?: "91"
        set(value) = sp.edit().putString(KEY_CC, value.filter { it.isDigit() }).apply()

    companion object {
        private const val KEY_SCRIPT_URL = "script_url"
        private const val KEY_OWN_NAME = "own_name"
        private const val KEY_OWN_WA = "own_whatsapp"
        private const val KEY_CC = "country_code"
    }
}
