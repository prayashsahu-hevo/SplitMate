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

    // ---------------------------------------------------------------- header

    private fun bindHeader() {
        binding.tvAmount.text = money(payment.amount)
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
                dateStr, timeStr, payment.vendor, payment.amount, reason, category, payment.source
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
        return if (d <= 0) 0.0 else payment.amount / d
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
        val labels = allContacts.map { it.label }.toTypedArray()
        val checked = BooleanArray(allContacts.size) { i -> selectedContacts.contains(allContacts[i]) }

        AlertDialog.Builder(this)
            .setTitle(R.string.pick_contacts)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.ok) { _, _ ->
                selectedContacts.clear()
                allContacts.forEachIndexed { i, c -> if (checked[i]) selectedContacts.add(c) }
                recomputeSplit()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
        val yourShare = if (binding.cbIncludeSelf.isChecked) share else 0.0
        val (dateStr, timeStr) = dateTime()

        // Persist the shared row (fire-and-forget with a toast on the result).
        Toast.makeText(this, R.string.saving, Toast.LENGTH_SHORT).show()
        Thread {
            val ok = SheetRepository(applicationContext, url).postShared(
                dateStr, timeStr, payment.vendor, payment.amount, reason, category,
                divisor(), share,
                selectedContacts.map { it.name }, customNames, yourShare, payment.source
            )
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (ok) R.string.saved_shared else R.string.save_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.start()

        buildSendButtons(reason, share, customNames)
        showSend()
    }

    /** Build a "Message X" button per contact + a self-reminder button for the unreached. */
    private fun buildSendButtons(reason: String, share: Double, customNames: List<String>) {
        val container = binding.containerSendButtons
        container.removeAllViews()

        selectedContacts.forEach { contact ->
            val msg = MessageTemplates.forFriend(
                contact.name, prefs.ownName, payment.vendor, reason, payment.amount, share
            )
            val phone = WhatsAppHelper.normalize(contact.number, prefs.countryCode)
            container.addView(sendButton(getString(R.string.message_person, contact.name)) {
                WhatsAppHelper.openChat(this, phone, msg)
            })
        }

        if (customNames.isNotEmpty()) {
            val selfMsg = MessageTemplates.forSelf(customNames, payment.vendor, reason, share)
            val ownWa = WhatsAppHelper.normalize(prefs.ownWhatsApp, prefs.countryCode)
            container.addView(sendButton(getString(R.string.send_self_reminder)) {
                if (ownWa.length >= 10) WhatsAppHelper.openChat(this, ownWa, selfMsg)
                else WhatsAppHelper.openShare(this, selfMsg)
            })
        }
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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PAYMENT = "extra_payment"
        private const val REQ_CONTACTS = 101
    }
}
