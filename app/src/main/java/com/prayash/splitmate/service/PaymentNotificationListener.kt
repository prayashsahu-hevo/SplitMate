package com.prayash.splitmate.service

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.prayash.splitmate.data.NotifLog
import com.prayash.splitmate.data.RecentPayments

/**
 * Reads notifications to recover the *amount* of a payment.
 *
 * This is no longer the trigger — [UpiUsageWatcher] is, because it fires for every UPI app
 * including ones that post nothing. This service's only job is to parse an amount when some
 * app does bother to announce the payment (the UPI app itself, or the user's bank) and park
 * it in [RecentPayments] for the watcher to claim.
 *
 * It never sees the screen: the OS hands it notification objects only.
 */
class PaymentNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotifLog.event(this, "Notification listener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return   // ignore our own notifications

        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val big = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val body = big ?: text

        val payment = PaymentParser.parse(appLabel(pkg), title, body, sbn.postTime)

        NotifLog.add(this, pkg, title, body, matched = payment != null, label = appLabel(pkg))

        if (payment != null) {
            // Park it; the usage watcher claims it when the UPI session ends.
            RecentPayments.record(payment)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) { /* no-op */ }

    /** Human-readable app name, used as the payment's source and to identify bank apps. */
    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }
}
