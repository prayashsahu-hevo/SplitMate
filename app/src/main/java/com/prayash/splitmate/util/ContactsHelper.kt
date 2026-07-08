package com.prayash.splitmate.util

import android.content.Context
import android.provider.ContactsContract

data class Contact(val name: String, val number: String) {
    /** Label shown in the picker list. */
    val label: String get() = if (number.isBlank()) name else "$name  ·  $number"
}

object ContactsHelper {

    /**
     * Load contacts that have at least one phone number, de-duplicated by name,
     * sorted alphabetically. Requires READ_CONTACTS to already be granted.
     */
    fun loadContacts(context: Context): List<Contact> {
        val out = LinkedHashMap<String, Contact>()   // key = name, keeps first number seen
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx)?.trim().orEmpty()
                val number = cursor.getString(numIdx)?.trim().orEmpty()
                if (name.isBlank()) continue
                out.getOrPut(name) { Contact(name, number) }
            }
        }
        return out.values.toList()
    }
}
