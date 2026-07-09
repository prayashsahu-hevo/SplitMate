package com.prayash.splitmate.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Posts rows to the Google Apps Script web app. The script routes on the "type" field
 * ("personal" | "shared") and appends to the matching tab.
 *
 * Apps Script answers a POST to /exec with a 302 redirect to script.googleusercontent.com;
 * the row is written during that POST, and the redirect carries the JSON result. Android's
 * HttpURLConnection is unreliable at following that redirect for a POST, so we follow it
 * manually with a GET. Failures are logged to the on-device debug log with the real reason.
 *
 * Call from a background thread.
 */
class SheetRepository(private val context: Context, scriptUrl: String) {

    // Auto-clean the URL: strip an account-specific "/u/N/" segment that breaks POSTs.
    private val url: String = scriptUrl.trim().replace(Regex("/macros/u/\\d+/s/"), "/macros/s/")

    fun postPersonal(
        dateStr: String, timeStr: String, vendor: String, amount: Double,
        reason: String, category: String, source: String
    ): Boolean = post(JSONObject().apply {
        put("type", "personal")
        put("date", dateStr); put("time", timeStr); put("vendor", vendor)
        put("amount", amount); put("reason", reason); put("category", category)
        put("source", source)
    })

    fun postShared(
        dateStr: String, timeStr: String, vendor: String, totalAmount: Double,
        reason: String, category: String, numPeople: Int, perPersonShare: Double,
        contacts: List<String>, unreachedNames: List<String>, yourShare: Double, source: String
    ): Boolean = post(JSONObject().apply {
        put("type", "shared")
        put("date", dateStr); put("time", timeStr); put("vendor", vendor)
        put("totalAmount", totalAmount); put("reason", reason); put("category", category)
        put("numPeople", numPeople); put("perPersonShare", perPersonShare)
        put("contacts", contacts.joinToString(", "))
        put("unreached", unreachedNames.joinToString(", "))
        put("yourShare", yourShare); put("source", source)
    })

    private fun post(body: JSONObject): Boolean {
        if (url.isBlank()) {
            NotifLog.event(context, "Sheet: URL not set")
            return false
        }
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                instanceFollowRedirects = false          // we follow manually
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            var code = conn.responseCode
            Log.d(TAG, "POST -> HTTP $code")

            // Follow the Apps Script redirect (to googleusercontent) with a GET.
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank()) {
                    NotifLog.event(context, "Sheet FAIL: $code redirect with no Location")
                    return false
                }
                val c2 = (URL(loc).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    instanceFollowRedirects = true
                }
                code = c2.responseCode
                val resp = runCatching { c2.inputStream.bufferedReader().readText() }.getOrDefault("")
                c2.disconnect()
                val ok = code in 200..299 && resp.contains("\"ok\":true")
                NotifLog.event(context, if (ok) "Sheet OK ($code)" else "Sheet FAIL after redirect: $code ${resp.take(120)}")
                return ok
            }

            val ok = code in 200..299
            val resp = runCatching {
                (if (ok) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText()
            }.getOrNull().orEmpty()
            conn.disconnect()
            NotifLog.event(context, if (ok) "Sheet OK ($code)" else "Sheet FAIL: $code ${resp.take(120)}")
            return ok
        } catch (e: Exception) {
            Log.e(TAG, "POST failed", e)
            NotifLog.event(context, "Sheet ERROR: ${e.javaClass.simpleName}: ${e.message}")
            return false
        }
    }

    companion object {
        private const val TAG = "SplitMateSheet"
    }
}
