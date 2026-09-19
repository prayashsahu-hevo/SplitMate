package com.prayash.splitmate.service

import android.app.AppOpsManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import com.prayash.splitmate.data.NotifLog
import com.prayash.splitmate.data.Payment
import com.prayash.splitmate.data.RecentPayments
import com.prayash.splitmate.data.UpiApps
import com.prayash.splitmate.util.PromptNotifier

/**
 * Watches for the user finishing a session in a UPI app, and fires the logging prompt.
 *
 * This is the trigger that works everywhere. Usage stats report only *which app was in the
 * foreground and when* — never screen content — and, critically, an app cannot detect or
 * block being observed this way. That is what makes Paytm work: it refuses to transact while
 * an accessibility service is enabled, and posts no notification for outgoing payments, but
 * it cannot hide the fact that you used it.
 *
 * The amount comes from [RecentPayments] when some app did notify; otherwise the user types it.
 */
class UpiUsageWatcher : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var usage: UsageStatsManager

    private var upiPackages: Set<String> = emptySet()
    private var lastQueryAt = 0L

    /** The UPI app currently in the foreground, and when it came up. */
    private var currentPkg: String? = null
    private var sessionStart = 0L

    /** Set when the UPI app is paused; a session only ends if it stays paused. */
    private var pendingEndAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            poll()
            handler.postDelayed(this, if (currentPkg != null) ACTIVE_INTERVAL_MS else IDLE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        usage = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        upiPackages = UpiApps.installedPackages(this)
        lastQueryAt = System.currentTimeMillis()

        startForeground(NOTIF_ID, PromptNotifier.watcherNotification(this))
        NotifLog.event(this, "Usage watcher started — watching ${upiPackages.size} UPI app(s)")
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Re-discover in case the user installed a new UPI app since we started.
        upiPackages = UpiApps.installedPackages(this)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        NotifLog.event(this, "Usage watcher stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun poll() {
        val now = System.currentTimeMillis()
        // Overlap the window slightly so an event landing between polls is not missed.
        val events = try {
            usage.queryEvents(lastQueryAt - QUERY_OVERLAP_MS, now)
        } catch (e: SecurityException) {
            NotifLog.event(this, "Usage access revoked — watcher idle")
            return
        }
        lastQueryAt = now

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> onResumed(pkg, event.timeStamp)
                UsageEvents.Event.ACTIVITY_PAUSED -> onPaused(pkg, event.timeStamp)
            }
        }

        // A pause that was never followed by a resume (user left, or screen went off)
        // settles into a finished session.
        val pending = pendingEndAt
        if (pending > 0 && now - pending >= END_GRACE_MS) {
            finishSession(pending)
        }
    }

    private fun onResumed(pkg: String, at: Long) {
        if (pkg in upiPackages) {
            if (pkg != currentPkg) {
                currentPkg = pkg
                sessionStart = at
            }
            pendingEndAt = 0L      // came back to the UPI app; not leaving after all
        } else if (pkg != packageName && currentPkg != null) {
            // Moved to a different app entirely — the UPI session is over.
            finishSession(at)
        }
    }

    private fun onPaused(pkg: String, at: Long) {
        if (pkg == currentPkg && pendingEndAt == 0L) pendingEndAt = at
    }

    private fun finishSession(endedAt: Long) {
        val pkg = currentPkg ?: run { pendingEndAt = 0L; return }
        val start = sessionStart
        currentPkg = null
        pendingEndAt = 0L

        val dwell = endedAt - start
        if (dwell < MIN_DWELL_MS) {
            // Too short to have been a payment — a glance at the balance, a misfire.
            NotifLog.event(this, "Ignored ${UpiApps.labelFor(this, pkg)} session (${dwell / 1000}s)")
            return
        }

        val label = UpiApps.labelFor(this, pkg)
        // Claim an amount if any app — the UPI app itself, or the bank — posted one.
        val known: Payment? = RecentPayments.claim(
            fromMillis = start - CLAIM_LEAD_MS,
            toMillis = endedAt + CLAIM_TRAIL_MS,
            pkgLabel = label
        )

        NotifLog.event(
            this,
            "Session end: $label (${dwell / 1000}s) → " +
                if (known != null) "amount ₹${known.amount} from notification" else "amount unknown, asking"
        )

        PromptNotifier.promptForPayment(
            context = this,
            appLabel = label,
            known = known,
            sessionEndedAt = endedAt
        )
    }

    companion object {
        private const val NOTIF_ID = 4711

        /** Poll faster while a UPI app is open so the prompt lands promptly after leaving it. */
        private const val ACTIVE_INTERVAL_MS = 2_000L
        private const val IDLE_INTERVAL_MS = 6_000L
        private const val QUERY_OVERLAP_MS = 2_000L

        /** A pause must persist this long before the session counts as finished. */
        private const val END_GRACE_MS = 3_000L

        /**
         * A real UPI payment needs at least this long (open, pick payee, enter amount, PIN).
         * Shorter sessions are balance checks and are ignored — this is the main false-trigger control.
         */
        private const val MIN_DWELL_MS = 10_000L

        private const val CLAIM_LEAD_MS = 10_000L
        private const val CLAIM_TRAIL_MS = 20_000L

        /** True when the user has granted Usage access in Settings. */
        fun hasUsageAccess(context: Context): Boolean {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
                )
            }
            return mode == AppOpsManager.MODE_ALLOWED
        }

        fun start(context: Context) {
            if (!hasUsageAccess(context)) return
            val intent = Intent(context, UpiUsageWatcher::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, UpiUsageWatcher::class.java))
        }
    }
}
