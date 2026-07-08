package com.prayash.splitmate.data

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Posts rows to the Google Apps Script web app. The script routes on the "type" field
 * ("personal" | "shared") and appends to the matching tab.
 *
 * Call [postPersonal] / [postShared] from a background thread.
 */
class SheetRepository(private val scriptUrl: String) {

    /** @return true on HTTP 2xx. */
    fun postPersonal(
        dateStr: String, timeStr: String, vendor: String, amount: Double,
        reason: String, category: String, source: String
    ): Boolean {
        val body = JSONObject().apply {
            put("type", "personal")
            put("date", dateStr)
            put("time", timeStr)
            put("vendor", vendor)
            put("amount", amount)
            put("reason", reason)
            put("category", category)
            put("source", source)
        }
        return post(body)
    }

    fun postShared(
        dateStr: String, timeStr: String, vendor: String, totalAmount: Double,
        reason: String, category: String, numPeople: Int, perPersonShare: Double,
        contacts: List<String>, unreachedNames: List<String>, yourShare: Double, source: String
    ): Boolean {
        val body = JSONObject().apply {
            put("type", "shared")
            put("date", dateStr)
            put("time", timeStr)
            put("vendor", vendor)
            put("totalAmount", totalAmount)
            put("reason", reason)
            put("category", category)
            put("numPeople", numPeople)
            put("perPersonShare", perPersonShare)
            put("contacts", contacts.joinToString(", "))
            put("unreached", unreachedNames.joinToString(", "))
            put("yourShare", yourShare)
            put("source", source)
        }
        return post(body)
    }

    private fun post(body: JSONObject): Boolean {
        if (scriptUrl.isBlank()) {
            Log.w(TAG, "Script URL not configured; skipping upload")
            return false
        }
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(scriptUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                instanceFollowRedirects = true            // Apps Script redirects to googleusercontent
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            Log.d(TAG, "Sheet POST -> HTTP $code")
            code in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Sheet POST failed", e)
            false
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        private const val TAG = "SplitMateSheet"
    }
}
