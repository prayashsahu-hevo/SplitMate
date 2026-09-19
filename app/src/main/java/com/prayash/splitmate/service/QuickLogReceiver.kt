package com.prayash.splitmate.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.prayash.splitmate.R
import com.prayash.splitmate.data.NotifLog
import com.prayash.splitmate.data.Payment
import com.prayash.splitmate.data.Prefs
import com.prayash.splitmate.data.SheetRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Logs a personal spend straight from the notification's [Personal] action — no screen change.
 *
 * Only offered when the amount is already known, since there is nothing left to ask.
 */
class QuickLogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val payment = intent.getSerializableExtra(EXTRA_PAYMENT) as? Payment ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        if (notifId >= 0) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .cancel(notifId)
        }

        val prefs = Prefs(context)
        val appContext = context.applicationContext
        val pending = goAsync()

        thread {
            try {
                val when_ = Date(payment.timestampMillis)
                val ok = SheetRepository(appContext, prefs.scriptUrl).postPersonal(
                    dateStr = DATE_FMT.format(when_),
                    timeStr = TIME_FMT.format(when_),
                    vendor = payment.vendor,
                    amount = payment.amount,
                    reason = "",
                    category = "",
                    source = payment.source
                )
                NotifLog.event(appContext, if (ok) "Quick-logged personal ₹${payment.amount}"
                                           else "Quick-log FAILED ₹${payment.amount}")
            } finally {
                pending.finish()
            }
        }

        Toast.makeText(context, R.string.logged_personal, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_PAYMENT = "payment"
        const val EXTRA_NOTIF_ID = "notif_id"

        private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        private val TIME_FMT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        fun pendingIntent(context: Context, payment: Payment, notifId: Int): PendingIntent {
            val intent = Intent(context, QuickLogReceiver::class.java).apply {
                putExtra(EXTRA_PAYMENT, payment)
                putExtra(EXTRA_NOTIF_ID, notifId)
            }
            return PendingIntent.getBroadcast(
                context, notifId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
