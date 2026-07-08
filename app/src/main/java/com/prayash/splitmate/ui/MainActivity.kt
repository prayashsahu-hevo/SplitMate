package com.prayash.splitmate.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import com.prayash.splitmate.service.PaymentAccessibilityService
import com.prayash.splitmate.service.PaymentNotificationListener

/**
 * Setup dashboard: check/grant the three permissions the app needs and edit settings.
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

        binding.btnNotif.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        binding.btnOverlay.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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

    private fun refreshStatuses() {
        mark(binding.tvNotifStatus, R.string.perm_notif, isNotifListenerEnabled())
        mark(binding.tvOverlayStatus, R.string.perm_overlay, Settings.canDrawOverlays(this))
        mark(binding.tvAccessibilityStatus, R.string.perm_accessibility, isAccessibilityEnabled())
        mark(binding.tvContactsStatus, R.string.perm_contacts, hasContacts())
    }

    private fun isAccessibilityEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val cn = ComponentName(this, PaymentAccessibilityService::class.java)
        return flat.split(":").any { ComponentName.unflattenFromString(it) == cn }
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
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.grant_overlay, Toast.LENGTH_LONG).show()
            return
        }
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
