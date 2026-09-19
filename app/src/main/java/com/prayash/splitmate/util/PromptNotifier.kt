package com.prayash.splitmate.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.prayash.splitmate.R
import com.prayash.splitmate.data.Payment
import com.prayash.splitmate.service.QuickLogReceiver
import com.prayash.splitmate.ui.PaymentPromptActivity

/**
 * Posts the "log this payment" prompt as a heads-up notification with action buttons.
 *
 * This replaces the old full-screen overlay activity, which needed SYSTEM_ALERT_WINDOW to be
 * launched from the background. A notification needs no such permission, and it is also less
 * intrusive: when the amount is already known, logging a personal spend is a single tap on
 * [Personal] with no screen change at all.
 */
object PromptNotifier {

    private const val CHANNEL_PROMPT = "payment_prompt"
    private const val CHANNEL_WATCHER = "watcher"

    /** Base id for prompts; each prompt gets a unique id so several can queue up. */
    private var nextPromptId = 5000

    /** The quiet, permanent notification that keeps the usage watcher alive. */
    fun watcherNotification(context: Context): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_WATCHER)
            .setContentTitle(context.getString(R.string.watcher_title))
            .setContentText(context.getString(R.string.watcher_text))
            .setSmallIcon(R.drawable.ic_wallet)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(openApp(context))
            .build()
    }

    /**
     * Prompt the user to log a payment they just made in [appLabel].
     *
     * @param known the payment if some app posted a parseable notification; null means the
     *              UPI app stayed silent (Paytm) and the user must type the amount.
     */
    fun promptForPayment(
        context: Context,
        appLabel: String,
        known: Payment?,
        sessionEndedAt: Long
    ) {
        ensureChannels(context)
        val id = nextPromptId++

        val payment = known ?: Payment(
            amount = 0.0,                 // 0.0 = unknown; the prompt asks for it
            vendor = "",
            source = appLabel,
            timestampMillis = sessionEndedAt,
            rawText = ""
        )

        val title: String
        val text: String
        if (known != null) {
            title = context.getString(R.string.prompt_known_title, format(known.amount))
            text = if (known.vendor.isBlank()) appLabel
            else context.getString(R.string.prompt_known_text, known.vendor, appLabel)
        } else {
            title = context.getString(R.string.prompt_unknown_title, appLabel)
            text = context.getString(R.string.prompt_unknown_text)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_PROMPT)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_wallet)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPrompt(context, payment, id))

        if (known != null) {
            // Amount is known, so a personal spend can be logged without opening anything.
            builder.addAction(
                R.drawable.ic_person,
                context.getString(R.string.action_personal),
                QuickLogReceiver.pendingIntent(context, payment, id)
            )
            builder.addAction(
                R.drawable.ic_group,
                context.getString(R.string.action_split),
                openPrompt(context, payment, id)
            )
        } else {
            builder.addAction(
                R.drawable.ic_wallet,
                context.getString(R.string.action_log_it),
                openPrompt(context, payment, id)
            )
        }

        NotificationManagerCompatSafe.notify(context, id, builder.build())
    }

    fun cancel(context: Context, id: Int) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id)
    }

    private fun openPrompt(context: Context, payment: Payment, id: Int): PendingIntent {
        val intent = Intent(context, PaymentPromptActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(PaymentPromptActivity.EXTRA_PAYMENT, payment)
            putExtra(PaymentPromptActivity.EXTRA_NOTIF_ID, id)
        }
        return PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, PaymentPromptActivity::class.java)
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun format(amount: Double): String =
        if (amount % 1.0 == 0.0) "₹${amount.toLong()}" else "₹%.2f".format(amount)

    private fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_PROMPT) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_PROMPT,
                    context.getString(R.string.channel_prompt),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = context.getString(R.string.channel_prompt_desc) }
            )
        }
        if (nm.getNotificationChannel(CHANNEL_WATCHER) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_WATCHER,
                    context.getString(R.string.channel_watcher),
                    NotificationManager.IMPORTANCE_MIN
                ).apply { description = context.getString(R.string.channel_watcher_desc) }
            )
        }
    }
}

/** Posting a notification is a no-op rather than a crash when the user denied the permission. */
private object NotificationManagerCompatSafe {
    fun notify(context: Context, id: Int, notification: Notification) {
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(id, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted — nothing we can do from here.
        }
    }
}
