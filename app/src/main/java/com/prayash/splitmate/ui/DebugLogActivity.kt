package com.prayash.splitmate.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.prayash.splitmate.R
import com.prayash.splitmate.data.NotifLog
import com.prayash.splitmate.databinding.ActivityDebugLogBinding

/** Shows the captured-notification log so the payment parser can be diagnosed on-device. */
class DebugLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDebugLogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRefresh.setOnClickListener { render() }
        binding.btnCopy.setOnClickListener { copy() }
        binding.btnClear.setOnClickListener {
            NotifLog.clear(this)
            render()
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        binding.tvLog.text = NotifLog.read(this)
    }

    private fun copy() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("SplitMate log", binding.tvLog.text))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }
}
