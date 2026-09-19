package com.prayash.splitmate.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Brings the usage watcher back after a reboot, so detection survives without user action. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            UpiUsageWatcher.start(context)
        }
    }
}
