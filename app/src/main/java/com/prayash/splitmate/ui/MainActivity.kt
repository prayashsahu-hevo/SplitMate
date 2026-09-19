package com.prayash.splitmate.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.prayash.splitmate.R
import com.prayash.splitmate.data.Payment
import com.prayash.splitmate.data.Prefs
import com.prayash.splitmate.databinding.ActivityMainBinding
import com.prayash.splitmate.service.PaymentNotificationListener
import com.prayash.splitmate.service.UpiUsageWatcher

/**
 * Setup dashboard: grant the accesses detection needs, and edit settings.
 *
 * Usage access is the only required one — it is what makes the prompt fire for every UPI app.
 * Notification access is optional and only improves the result, by filling in the amount.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        loadSettings()
        ensureNotificationPermission()

        binding.btnNotif.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        binding.btnUsage.setOnClickListener {
            Toast.makeText(this, R.string.usage_rationale, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        binding.btnContacts.setOnClickListener {
            if (!hasContacts()) requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 1)
            else Toast.makeText(this, R.string.already_granted, Toast.LENGTH_SHORT).show()
        }
        binding.btnSaveSettings.setOnClickListener { saveSettings() }
        binding.btnTest.setOnClickListener { launchTestPopup() }
        binding.btnDebugLog.setOnClickListener {
            startActivity(Intent(this, DebugLogActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatuses()
        // Start (or restart) the watcher as soon as usage access exists.
        if (UpiUsageWatcher.hasUsageAccess(this)) UpiUsageWatcher.start(this)
    }

    // -------------------------------------------------- settings

    private fun loadSettings() {
        binding.etScriptUrl.setText(prefs.scriptUrl)
        binding.etOwnName.setText(prefs.ownName)
        binding.etOwnWa.setText(prefs.ownWhatsApp)
        binding.etCc.setText(prefs.countryCode)
    }

    private fun saveSettings() {
        prefs.scriptUrl = binding.etScriptUrl.text?.toString().orEmpty()
        prefs.ownName = binding.etOwnName.text?.toString().orEmpty()
        prefs.ownWhatsApp = binding.etOwnWa.text?.toString().orEmpty()
        prefs.countryCode = binding.etCc.text?.toString()?.ifBlank { "91" } ?: "91"
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
    }

    // -------------------------------------------------- permission status

    /**
     * Every prompt is a notification now, so without this the app looks completely dead on
     * Android 13+, where the permission is denied by default.
     */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun refreshStatuses() {
        mark(binding.tvPostNotifStatus, R.string.perm_post_notif, canPostNotifications())
        mark(binding.tvUsageStatus, R.string.perm_usage, UpiUsageWatcher.hasUsageAccess(this))
        mark(binding.tvNotifStatus, R.string.perm_notifications, isNotifListenerEnabled())
        mark(binding.tvContactsStatus, R.string.perm_contacts, hasContacts())
    }

    private fun mark(view: android.widget.TextView, labelRes: Int, granted: Boolean) {
        val tick = if (granted) "✅" else "⬜"
        view.text = "$tick ${getString(labelRes)}"
    }

    private fun hasContacts() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    private fun isNotifListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val cn = ComponentName(this, PaymentNotificationListener::class.java)
        return flat?.split(":")?.any {
            ComponentName.unflattenFromString(it) == cn
        } == true
    }

    // -------------------------------------------------- test

    /** Fire a fake payment through the same prompt UI so you can rehearse without paying. */
    private fun launchTestPopup() {
        val demo = Payment(
            amount = 450.0,
            vendor = "Demo Cafe",
            source = "Google Pay",
            timestampMillis = System.currentTimeMillis(),
            rawText = "You paid ₹450 to Demo Cafe"
        )
        startActivity(
            Intent(this, PaymentPromptActivity::class.java)
                .putExtra(PaymentPromptActivity.EXTRA_PAYMENT, demo)
        )
    }
}
