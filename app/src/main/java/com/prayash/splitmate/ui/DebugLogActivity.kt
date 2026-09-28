package com.prayash.splitmate.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.prayash.splitmate.R
import com.prayash.splitmate.data.NotifLog
import com.prayash.splitmate.util.Diagnostics
import com.prayash.splitmate.databinding.ActivityDebugLogBinding

/**
 * Shows the captured-notification log so the payment parser can be diagnosed on-device.
 *
 * Defaults to the money-only view: during the detection-coverage survey that is the signal,
 * and it stays readable even after a day of chat notifications.
 */
class DebugLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDebugLogBinding

    /** Money-only view is the default; toggle shows every captured notification. */
    private var moneyOnly = true

    /** Set while the self-test report is on screen instead of the log. */
    private var showingSelfTest = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRefresh.setOnClickListener { showingSelfTest = false; render() }
        binding.btnSelfTest.setOnClickListener {
            showingSelfTest = true
            render()
        }
        binding.btnCopy.setOnClickListener { copy() }
        binding.btnShare.setOnClickListener { share() }
        binding.btnToggle.setOnClickListener {
            moneyOnly = !moneyOnly
            showingSelfTest = false
            render()
        }
        binding.btnClear.setOnClickListener {
            NotifLog.clear(this)
            render()
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (!showingSelfTest) render()
    }

    private fun render() {
        binding.tvLog.text = when {
            showingSelfTest -> Diagnostics.report(this) + "\n\n--- recent activity ---\n\n" +
                NotifLog.readMoney(this)
            moneyOnly -> NotifLog.readMoney(this)
            else -> NotifLog.read(this)
        }
        binding.btnToggle.setText(if (moneyOnly) R.string.show_money_only else R.string.show_all)
    }

    private fun copy() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("SplitMate log", binding.tvLog.text))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    /** Share the log out of the phone — easier than copy/paste for a long capture. */
    private fun share() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "SplitMate notification log")
            putExtra(Intent.EXTRA_TEXT, binding.tvLog.text.toString())
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }
}
