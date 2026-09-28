package com.prayash.splitmate.util

import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.prayash.splitmate.data.UpiApps
import com.prayash.splitmate.service.PaymentNotificationListener
import com.prayash.splitmate.service.UpiUsageWatcher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A one-screen answer to "why did nothing happen?".
 *
 * Detection is a chain — usage access, a live watcher, usage events actually arriving, a UPI
 * app being watched, and a notification that can post — and a break anywhere looks identical
 * from the outside. This walks the chain and names the first broken link, so diagnosing does
 * not mean exporting logs and reading them somewhere else.
 */
object Diagnostics {

    private val TIME = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun report(context: Context): String = buildString {
        appendLine("SPLITMATE SELF-TEST")
        appendLine("run at ${TIME.format(Date())}")
        appendLine()

        // 1. Usage access — without it nothing downstream can work.
        val usageOk = UpiUsageWatcher.hasUsageAccess(context)
        appendLine("${tick(usageOk)} 1. Usage access granted")
        if (!usageOk) appendLine("      → Settings ▸ Apps ▸ Special app access ▸ Usage access")

        // 2. Is the watcher actually alive? OEMs kill foreground services silently.
        appendLine("${tick(UpiUsageWatcher.running)} 2. Watcher service running")
        if (!UpiUsageWatcher.running) {
            appendLine("      → open SplitMate once; if it keeps dying, exempt it from")
            appendLine("        battery optimisation and lock it in the recents screen")
        }

        // 3. Are usage events reaching us at all? This is the OEM-killer tell.
        val events = recentEvents(context)
        appendLine("${tick(events.isNotEmpty())} 3. Usage events flowing (${events.size} in last 5 min)")
        if (events.isEmpty() && usageOk) {
            appendLine("      → access is granted but the OS is returning nothing;")
            appendLine("        this phone's OEM is likely throttling usage stats")
        }
        events.take(6).forEach { appendLine("      · $it") }

        // 4. Which UPI apps are being watched.
        val apps = UpiApps.installed(context)
        appendLine("${tick(apps.isNotEmpty())} 4. UPI apps watched (${apps.size})")
        apps.forEach { appendLine("      · ${it.label}  [${it.packageName}]") }
        if (apps.isEmpty()) appendLine("      → nothing to watch; no payment can ever trigger")

        // 5. Can we post the prompt at all?
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifOk = nm.areNotificationsEnabled()
        appendLine("${tick(notifOk)} 5. Notifications allowed")
        val channel = nm.getNotificationChannel("payment_prompt")
        val channelOk = channel == null || channel.importance >= NotificationManager.IMPORTANCE_DEFAULT
        appendLine("${tick(channelOk)} 6. Prompt channel importance (${channel?.importance ?: "not created yet"})")
        if (!channelOk) appendLine("      → the prompt channel was silenced; re-enable it in app notification settings")

        // 6. Amount auto-fill is optional, but say so plainly.
        appendLine("${tick(listenerEnabled(context))} 7. Notification access (optional — fills the amount)")

        appendLine()
        appendLine("Session threshold: ${UpiUsageWatcher.MIN_DWELL_MS / 1000}s minimum in a UPI app")
        appendLine()
        appendLine(verdict(usageOk, events, apps.isNotEmpty(), notifOk))
    }

    private fun verdict(
        usageOk: Boolean,
        events: List<String>,
        hasApps: Boolean,
        notifOk: Boolean
    ): String = when {
        !usageOk -> "VERDICT: usage access is off — that alone stops everything."
        !UpiUsageWatcher.running -> "VERDICT: the watcher is not running."
        events.isEmpty() -> "VERDICT: no usage events are reaching the app. The OS is not " +
            "reporting app switches, so a payment can never be noticed."
        !hasApps -> "VERDICT: no UPI apps are being watched."
        !notifOk -> "VERDICT: notifications are blocked, so a detected payment cannot show."
        else -> "VERDICT: every stage looks healthy. Make a payment, stay in the app more " +
            "than ${UpiUsageWatcher.MIN_DWELL_MS / 1000}s, then re-run this and check the log below."
    }

    /** Recent foreground switches, newest last — proof that usage stats are live. */
    private fun recentEvents(context: Context): List<String> {
        if (!UpiUsageWatcher.hasUsageAccess(context)) return emptyList()
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val it = usm.queryEvents(now - 5 * 60_000L, now)
            val out = mutableListOf<String>()
            val e = UsageEvents.Event()
            while (it.hasNextEvent()) {
                it.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    out.add("${TIME.format(Date(e.timeStamp))}  ${e.packageName}")
                }
            }
            out.reversed()
        } catch (e: Exception) {
            listOf("error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun listenerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        val cn = ComponentName(context, PaymentNotificationListener::class.java)
        return flat?.split(":")?.any { ComponentName.unflattenFromString(it) == cn } == true
    }

    private fun tick(ok: Boolean) = if (ok) "✅" else "❌"

    @Suppress("unused")
    private fun appOps(context: Context) =
        context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
}
