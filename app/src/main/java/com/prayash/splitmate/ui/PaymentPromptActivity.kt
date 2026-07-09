package com.prayash.splitmate.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.prayash.splitmate.R
import com.prayash.splitmate.data.Payment
import com.prayash.splitmate.data.Prefs
import com.prayash.splitmate.data.SheetRepository
import com.prayash.splitmate.databinding.ActivityPaymentPromptBinding
import com.prayash.splitmate.util.Contact
import com.prayash.splitmate.util.ContactsHelper
import com.prayash.splitmate.util.MessageTemplates
import com.prayash.splitmate.util.WhatsAppHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * The pop-up shown right after a UPI payment. Runs the full choose → personal/split → send flow
 * inside one activity by toggling section visibility.
 */
class PaymentPromptActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentPromptBinding
    private lateinit var prefs: Prefs
    private lateinit var payment: Payment

    private val selectedContacts = mutableListOf<Contact>()
    private var allContacts: List<Contact> = emptyList()
    private val customNameFields = mutableListOf<EditText>()

    // Auto-advance send queue: each entry is (label, action-that-opens-a-chat).
    private val sendQueue = mutableListOf<Pair<String, () -> Unit>>()
    private var sendIndex = 0
    private var sendingActive = false
    private var pendingAdvance = false
    private var sendProgressView: android.widget.TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentPromptBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)

        @Suppress("DEPRECATION")
        payment = (intent.getSerializableExtra(EXTRA_PAYMENT) as? Payment) ?: run {
            finish(); return
        }

        bindHeader()
        setupCategorySpinners()
        wireButtons()
        showChoose()
    }

    override fun onResume() {
        super.onResume()
        // When the user returns from a WhatsApp chat mid-send, auto-open the next one.
        if (sendingActive && pendingAdvance) {
            pendingAdvance = false
            sendIndex++
            openCurrentSend()
        }
    }

    // ---------------------------------------------------------------- header

    private fun bindHeader() {
        binding.etAmount.setText(amountToField(payment.amount))
        binding.tvVendor.text = if (payment.vendor.isBlank()) "" else "to ${payment.vendor}"
        val dt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            .format(Date(payment.timestampMillis))
        binding.tvMeta.text = "$dt · ${payment.source}"
    }

    private fun setupCategorySpinners() {
        val cats = resources.getStringArray(R.array.categories)
        listOf(binding.spCategoryPersonal, binding.spCategorySplit).forEach { dropdown ->
            dropdown.setSimpleItems(cats)
            dropdown.setText(cats.first(), false)   // sensible default so it's never blank
        }
    }

    // ---------------------------------------------------------------- section switching

    private fun showChoose() = only(binding.sectionChoose)
    private fun showPersonal() = only(binding.sectionPersonal)
    private fun showSplit() { only(binding.sectionSplit); recomputeSplit() }
    private fun showSend() = only(binding.sectionSend)

    private fun only(section: View) {
        listOf(
            binding.sectionChoose, binding.sectionPersonal,
            binding.sectionSplit, binding.sectionSend
        ).forEach { it.visibility = if (it === section) View.VISIBLE else View.GONE }
    }

    // ---------------------------------------------------------------- buttons

    private fun wireButtons() {
        binding.btnPersonal.setOnClickListener { showPersonal() }
        binding.btnSplit.setOnClickListener { showSplit() }
        binding.btnDismiss.setOnClickListener { finish() }
        binding.btnDone.setOnClickListener { finish() }

        binding.btnSavePersonal.setOnClickListener { savePersonal() }

        binding.btnPickContacts.setOnClickListener { onPickContacts() }
        binding.btnSaveSplit.setOnClickListener { saveSplitAndPrepare() }

        val recompute = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = recomputeSplit()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        }
        binding.etPeople.addTextChangedListener(recompute)
        binding.etAmount.addTextChangedListener(recompute)
        binding.cbIncludeSelf.setOnCheckedChangeListener { _, _ -> recomputeSplit() }
    }

    // ---------------------------------------------------------------- personal flow

    private fun savePersonal() {
        val url = requireScriptUrl() ?: return
        val reason = binding.etReasonPersonal.text?.toString()?.trim().orEmpty()
        val category = binding.spCategoryPersonal.text?.toString().orEmpty()
        val (dateStr, timeStr) = dateTime()

        Toast.makeText(this, R.string.saving, Toast.LENGTH_SHORT).show()
        Thread {
            val ok = SheetRepository(applicationContext, url).postPersonal(
                dateStr, timeStr, payment.vendor, currentAmount(), reason, category, payment.source
            )
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (ok) R.string.saved_personal else R.string.save_failed,
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }.start()
    }

    // ---------------------------------------------------------------- split flow

    /** #people you're splitting with (excludes you). */
    private fun numOthers(): Int = binding.etPeople.text?.toString()?.trim()?.toIntOrNull() ?: 0

    private fun divisor(): Int = numOthers() + if (binding.cbIncludeSelf.isChecked) 1 else 0

    private fun perShare(): Double {
        val d = divisor()
        return if (d <= 0) 0.0 else currentAmount() / d
    }

    /** Rebuild the custom-name inputs and the share preview whenever inputs change. */
    private fun recomputeSplit() {
        val others = numOthers()
        val customCount = max(0, others - selectedContacts.size)

        // Rebuild custom-name fields, preserving already-typed text.
        val previous = customNameFields.map { it.text?.toString().orEmpty() }
        binding.containerCustomNames.removeAllViews()
        customNameFields.clear()
        for (i in 0 until customCount) {
            val til = TextInputLayout(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
                hint = getString(R.string.custom_person_hint, i + 1)
            }
            val et = TextInputEditText(til.context)
            if (i < previous.size) et.setText(previous[i])
            til.addView(et)
            binding.containerCustomNames.addView(til)
            customNameFields.add(et)
        }

        // Selected-contacts summary.
        if (selectedContacts.isEmpty()) {
            binding.tvSelectedContacts.visibility = View.GONE
        } else {
            binding.tvSelectedContacts.visibility = View.VISIBLE
            binding.tvSelectedContacts.text = getString(
                R.string.selected_contacts_fmt,
                selectedContacts.joinToString(", ") { it.name }
            )
        }

        // Share preview.
        binding.tvSharePreview.text = if (divisor() <= 0) {
            getString(R.string.enter_people_count)
        } else {
            getString(R.string.each_owes_fmt, money(perShare()), divisor())
        }
    }

    private fun onPickContacts() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            openContactPicker()
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQ_CONTACTS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CONTACTS &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            openContactPicker()
        } else if (requestCode == REQ_CONTACTS) {
            Toast.makeText(this, R.string.contacts_denied, Toast.LENGTH_LONG).show()
        }
    }

    private fun openContactPicker() {
        if (allContacts.isEmpty()) allContacts = ContactsHelper.loadContacts(this)
        if (allContacts.isEmpty()) {
            Toast.makeText(this, R.string.no_contacts, Toast.LENGTH_SHORT).show()
            return
        }
        ContactPickerDialog.show(this, allContacts, selectedContacts) { chosen ->
            selectedContacts.clear()
            selectedContacts.addAll(chosen)
            recomputeSplit()
        }
    }

    private fun saveSplitAndPrepare() {
        val url = requireScriptUrl() ?: return
        if (numOthers() < 1) {
            Toast.makeText(this, R.string.enter_people_count, Toast.LENGTH_SHORT).show()
            return
        }
        val customNames = customNameFields.map { it.text?.toString()?.trim().orEmpty() }
        if (customNames.any { it.isBlank() }) {
            Toast.makeText(this, R.string.fill_custom_names, Toast.LENGTH_SHORT).show()
            return
        }

        val reason = binding.etReasonSplit.text?.toString()?.trim().orEmpty()
        val category = binding.spCategorySplit.text?.toString().orEmpty()
        val share = perShare()
        val (dateStr, timeStr) = dateTime()

        // One row per person who owes: selected contacts + typed custom names, each owing `share`.
        val people = mutableListOf<Pair<String, Double>>()
        selectedContacts.forEach { people.add(it.name to share) }
        customNames.forEach { people.add(it to share) }

        // Persist the shared rows (fire-and-forget with a toast on the result).
        Toast.makeText(this, R.string.saving, Toast.LENGTH_SHORT).show()
        Thread {
            val ok = SheetRepository(applicationContext, url).postShared(
                dateStr, timeStr, payment.vendor, currentAmount(), reason, category,
                payment.source, people
            )
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (ok) R.string.saved_shared else R.string.save_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.start()

        buildSendQueue(reason, share, customNames)
        showSend()
    }

    /** Build the ordered send queue (one entry per contact + a self-reminder for the unreached). */
    private fun buildSendQueue(reason: String, share: Double, customNames: List<String>) {
        sendQueue.clear()
        sendIndex = 0
        sendingActive = false
        pendingAdvance = false

        selectedContacts.forEach { contact ->
            val msg = MessageTemplates.forFriend(
                contact.name, prefs.ownName, payment.vendor, reason, currentAmount(), share
            )
            val phone = WhatsAppHelper.normalize(contact.number, prefs.countryCode)
            sendQueue.add(contact.name to { WhatsAppHelper.openChat(this, phone, msg) })
        }

        if (customNames.isNotEmpty()) {
            val selfMsg = MessageTemplates.forSelf(customNames, payment.vendor, reason, share)
            val ownWa = WhatsAppHelper.normalize(prefs.ownWhatsApp, prefs.countryCode)
            sendQueue.add(getString(R.string.send_self_reminder) to {
                if (ownWa.length >= 10) WhatsAppHelper.openChat(this, ownWa, selfMsg)
                else WhatsAppHelper.openShare(this, selfMsg)
            })
        }

        renderSendStart()
    }

    /** Initial send-screen state: a progress line + a single "Send via WhatsApp" button. */
    private fun renderSendStart() {
        val container = binding.containerSendButtons
        container.removeAllViews()

        val progress = android.widget.TextView(this).apply {
            text = getString(R.string.send_ready_fmt, sendQueue.size)
            setTextColor(ContextCompat.getColor(this@PaymentPromptActivity, R.color.sm_text))
            textSize = 15f
        }
        sendProgressView = progress
        container.addView(progress)

        container.addView(sendButton(getString(R.string.send_start)) { startSending() })
    }

    private fun startSending() {
        if (sendQueue.isEmpty()) { finish(); return }
        sendingActive = true
        sendIndex = 0
        openCurrentSend()
    }

    /** Open the chat at [sendIndex]; onResume advances to the next when the user returns. */
    private fun openCurrentSend() {
        if (sendIndex >= sendQueue.size) { finishSending(); return }
        val (label, action) = sendQueue[sendIndex]
        sendProgressView?.text = getString(R.string.send_progress_fmt, sendIndex + 1, sendQueue.size, label)
        pendingAdvance = true
        action.invoke()
    }

    private fun finishSending() {
        sendingActive = false
        pendingAdvance = false
        sendProgressView?.text = getString(R.string.send_all_done, sendQueue.size)
        Toast.makeText(this, R.string.send_all_done_toast, Toast.LENGTH_SHORT).show()
    }

    private fun sendButton(label: String, onClick: () -> Unit): MaterialButton {
        return MaterialButton(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(8) }
            text = label
            setOnClickListener { onClick() }
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun requireScriptUrl(): String? {
        val url = prefs.scriptUrl
        if (url.isBlank()) {
            Toast.makeText(this, R.string.set_url_first, Toast.LENGTH_LONG).show()
            return null
        }
        return url
    }

    private fun dateTime(): Pair<String, String> {
        val d = Date(payment.timestampMillis)
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(d)
        val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(d)
        return date to time
    }

    private fun money(a: Double): String =
        if (a % 1.0 == 0.0) "₹${a.toLong()}" else "₹%.2f".format(a)

    /** The amount to use everywhere — the (possibly user-corrected) value in the editable field. */
    private fun currentAmount(): Double =
        binding.etAmount.text?.toString()?.trim()?.toDoubleOrNull()?.takeIf { it > 0 } ?: payment.amount

    private fun amountToField(a: Double): String =
        if (a % 1.0 == 0.0) a.toLong().toString() else "%.2f".format(a)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PAYMENT = "extra_payment"
        private const val REQ_CONTACTS = 101
    }
}
