package com.prayash.splitmate.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.prayash.splitmate.data.NotifLog
import com.prayash.splitmate.ui.PaymentPromptActivity

/**
 * Reads the on-screen text of Paytm / Google Pay in real time. When their payment-success
 * screen appears, we extract the amount + payee and launch the prompt — the moment of payment,
 * with no dependency on notifications or SMS.
 *
 * Scoped (via accessibility_service_config.xml) to only the two UPI packages, so it can't see
 * any other app's screen.
 */
class PaymentAccessibilityService : AccessibilityService() {

    private var lastKey: String? = null
    private var lastAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        NotifLog.event(this, "Accessibility connected — watching UPI screens")
    }

    override fun onInterrupt() { /* no-op */ }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in PaymentParser.SUPPORTED_PACKAGES) return

        val root = rootInActiveWindow ?: return
        val sb = StringBuilder()
        collectText(root, sb)
        val text = sb.toString().trim()
        if (text.isEmpty()) return

        val payment = PaymentParser.parseScreen(pkg, text, System.currentTimeMillis())

        // Log screens that mention an amount (for tuning the parser on-device).
        val hasAmount = text.contains("₹") || Regex("(?i)\\b(rs|inr)\\b").containsMatchIn(text)
        if (hasAmount || payment != null) {
            NotifLog.add(this, "SCREEN:$pkg", null, text.take(300), matched = payment != null)
        }

        if (payment == null) return

        // Debounce: a success screen emits many events with the same content.
        val key = "$pkg|${payment.amount}|${payment.vendor}"
        val now = System.currentTimeMillis()
        if (key == lastKey && now - lastAt < 20_000) return
        lastKey = key
        lastAt = now

        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission missing; cannot show prompt")
            return
        }

        startActivity(
            Intent(this, PaymentPromptActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(PaymentPromptActivity.EXTRA_PAYMENT, payment)
            }
        )
    }

    /** Depth-first collect visible text + content descriptions from the node tree. */
    private fun collectText(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        node.text?.let { if (it.isNotBlank()) sb.append(it).append(" | ") }
        node.contentDescription?.let { if (it.isNotBlank()) sb.append(it).append(" | ") }
        for (i in 0 until node.childCount) {
            collectText(node.getChild(i), sb)
        }
    }

    companion object {
        private const val TAG = "SplitMateA11y"
    }
}
