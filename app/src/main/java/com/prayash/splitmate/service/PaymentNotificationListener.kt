package com.prayash.splitmate.service

import android.app.Notification
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.prayash.splitmate.data.NotifLog
import com.prayash.splitmate.ui.PaymentPromptActivity

/**
 * Listens to every posted notification and, when one looks like an outgoing UPI payment
 * from a supported app, launches [PaymentPromptActivity] on top of whatever is on screen.
 *
 * Launching an Activity from the background requires the "Draw over other apps"
 * (SYSTEM_ALERT_WINDOW) permission on modern Android — the app asks for it during setup.
 */
class PaymentNotificationListener : NotificationListenerService() {

    // Debounce: UPI apps often post/update the same notification several times.
    private var lastKey: String? = null
    private var lastAt = 0L

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotifLog.event(this, "Listener connected — receiving notifications")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return   // ignore our own notifications

        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val big = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

        val payment = PaymentParser.parse(pkg, title, big ?: text, sbn.postTime)

        // Debug: record EVERY notification so we can see exactly what Paytm/GPay post.
        NotifLog.add(this, pkg, title, big ?: text, matched = payment != null)

        if (payment == null) return

        // Debounce repeated posts of the same logical payment within 8 seconds.
        val key = "${pkg}|${payment.amount}|${payment.vendor}"
        val now = System.currentTimeMillis()
        if (key == lastKey && now - lastAt < 8_000) return
        lastKey = key
        lastAt = now

        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission missing; cannot show prompt for $payment")
            return
        }

        Log.d(TAG, "Detected payment: $payment")
        val intent = Intent(this, PaymentPromptActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(PaymentPromptActivity.EXTRA_PAYMENT, payment)
        }
        startActivity(intent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) { /* no-op */ }

    companion object {
        private const val TAG = "SplitMateListener"
    }
}
