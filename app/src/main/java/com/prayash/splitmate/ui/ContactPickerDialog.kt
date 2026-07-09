package com.prayash.splitmate.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.prayash.splitmate.R
import com.prayash.splitmate.util.Contact

/**
 * A searchable, multi-select contact picker. Type to filter by name/number; the checked set
 * is tracked by contact identity so it survives filtering.
 */
object ContactPickerDialog {

    fun show(
        context: Context,
        all: List<Contact>,
        preselected: List<Contact>,
        onDone: (List<Contact>) -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_contact_picker, null)
        val search = view.findViewById<EditText>(R.id.etSearch)
        val rv = view.findViewById<RecyclerView>(R.id.rvContacts)

        val adapter = Adapter(context, all, preselected.toMutableSet())
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = adapter

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = adapter.filter(s?.toString().orEmpty())
            override fun beforeTextChanged(c: CharSequence?, a: Int, b: Int, d: Int) {}
            override fun onTextChanged(c: CharSequence?, a: Int, b: Int, d: Int) {}
        })

        AlertDialog.Builder(context)
            .setTitle(R.string.pick_contacts)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ -> onDone(adapter.selectedList()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private class Adapter(
        context: Context,
        private val all: List<Contact>,
        private val selected: MutableSet<Contact>
    ) : RecyclerView.Adapter<Adapter.VH>() {

        private val inflater = LayoutInflater.from(context)
        private var shown: List<Contact> = all

        fun filter(query: String) {
            val q = query.trim().lowercase()
            shown = if (q.isEmpty()) all
            else all.filter { it.name.lowercase().contains(q) || it.number.lowercase().contains(q) }
            notifyDataSetChanged()
        }

        fun selectedList(): List<Contact> = selected.toList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(inflater.inflate(R.layout.item_contact, parent, false))

        override fun getItemCount() = shown.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val c = shown[position]
            holder.name.text = c.name
            holder.number.text = c.number.ifBlank { "—" }
            // Avoid the recycled listener firing during rebind.
            holder.check.setOnCheckedChangeListener(null)
            holder.check.isChecked = selected.contains(c)
            val toggle = {
                if (selected.contains(c)) selected.remove(c) else selected.add(c)
                holder.check.isChecked = selected.contains(c)
            }
            holder.itemView.setOnClickListener { toggle() }
            holder.check.setOnClickListener { toggle() }
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val check: MaterialCheckBox = v.findViewById(R.id.cbSelect)
            val name: TextView = v.findViewById(R.id.tvName)
            val number: TextView = v.findViewById(R.id.tvNumber)
        }
    }
}
