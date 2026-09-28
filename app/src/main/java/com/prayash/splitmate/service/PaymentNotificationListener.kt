package com.prayash.splitmate.service

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.prayash.splitmate.data.NotifLog
import com.prayash.splitmate.data.RecentPayments
import com.prayash.splitmate.util.PromptNotifier

/**
 * Reads notifications to recover the *amount* of a payment.
 *
 * A notification naming an amount is already proof a payment happened, so this prompts on it
 * directly rather than waiting for a UPI session to end. The bank counts too: Truecaller and
 * banking apps surface the debit within seconds, which covers UPI apps that announce nothing.
 *
 * [UpiUsageWatcher] remains the fallback for payments nothing announces at all, and stays
 * quiet about anything already raised here.
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

        if (payment == null) return

        // Duplicate posts of one payment are common (Truecaller posts the same debit
        // three times), so only the first becomes a prompt.
        if (!RecentPayments.recordIfNew(payment)) {
            NotifLog.event(this, "Duplicate of ₹${payment.amount} ignored")
            return
        }

        NotifLog.event(
            this,
            "Payment announced by ${payment.source}: ₹${payment.amount} → prompting",
            alsoMoney = true
        )
        RecentPayments.markPrompted(payment)
        PromptNotifier.promptForPayment(
            context = this,
            appLabel = payment.source,
            known = payment,
            sessionEndedAt = payment.timestampMillis
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) { /* no-op */ }

    /** Human-readable app name, used as the payment's source and to identify bank apps. */
    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }
}
