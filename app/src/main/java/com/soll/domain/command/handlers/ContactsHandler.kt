package com.soll.domain.command.handlers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler

class ContactsHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "contacts"
    override val description = "List contacts or search: /contacts [query]"

    override suspend fun execute(message: Message, args: String?) {
        if (!hasPermission()) {
            reply(message, "Contacts permission not granted. Please grant READ_CONTACTS permission in app settings.")
            return
        }

        val query = args?.trim()?.takeIf { it.isNotEmpty() }

        val contacts = if (query != null) {
            searchContacts(query)
        } else {
            getAllContacts(limit = 30)
        }

        if (contacts.isEmpty()) {
            val msg = if (query != null) {
                "No contacts found matching: $query"
            } else {
                "No contacts found."
            }
            reply(message, msg)
            return
        }

        val text = buildString {
            if (query != null) {
                append("<b>🔍 Contacts matching \"$query\"</b>\n\n")
            } else {
                append("<b>📇 Contacts (${contacts.size})</b>\n\n")
            }

            contacts.forEachIndexed { index, contact ->
                append("<b>${index + 1}. ${contact.name}</b>\n")
                contact.phones.forEach { phone ->
                    append("    📱 $phone\n")
                }
                contact.emails.forEach { email ->
                    append("    📧 $email\n")
                }
                append("\n")
            }

            if (contacts.size >= 30 && query == null) {
                append("<i>Showing first 30 contacts. Use /contacts &lt;name&gt; to search.</i>")
            }
        }

        reply(message, text)
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getAllContacts(limit: Int): List<ContactData> {
        val contactsMap = mutableMapOf<Long, ContactData>()

        // Get contact names
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
                ),
                null,
                null,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC LIMIT $limit"
            )

            cursor?.let {
                val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)

                while (it.moveToNext()) {
                    val id = it.getLong(idIndex)
                    val name = it.getString(nameIndex) ?: "Unknown"
                    contactsMap[id] = ContactData(id, name, mutableListOf(), mutableListOf())
                }
            }
        } finally {
            cursor?.close()
        }

        // Get phone numbers
        loadPhoneNumbers(contactsMap)

        // Get emails
        loadEmails(contactsMap)

        return contactsMap.values.toList()
    }

    private fun searchContacts(query: String): List<ContactData> {
        val contactsMap = mutableMapOf<Long, ContactData>()

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
                ),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?",
                arrayOf("%$query%"),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC LIMIT 20"
            )

            cursor?.let {
                val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)

                while (it.moveToNext()) {
                    val id = it.getLong(idIndex)
                    val name = it.getString(nameIndex) ?: "Unknown"
                    contactsMap[id] = ContactData(id, name, mutableListOf(), mutableListOf())
                }
            }
        } finally {
            cursor?.close()
        }

        loadPhoneNumbers(contactsMap)
        loadEmails(contactsMap)

        return contactsMap.values.toList()
    }

    private fun loadPhoneNumbers(contactsMap: MutableMap<Long, ContactData>) {
        if (contactsMap.isEmpty()) return

        val ids = contactsMap.keys.joinToString(",")

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} IN ($ids)",
                null,
                null
            )

            cursor?.let {
                val contactIdIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val contactId = it.getLong(contactIdIndex)
                    val number = it.getString(numberIndex)
                    if (number != null) {
                        contactsMap[contactId]?.phones?.add(number)
                    }
                }
            }
        } finally {
            cursor?.close()
        }
    }

    private fun loadEmails(contactsMap: MutableMap<Long, ContactData>) {
        if (contactsMap.isEmpty()) return

        val ids = contactsMap.keys.joinToString(",")

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Email.ADDRESS
                ),
                "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} IN ($ids)",
                null,
                null
            )

            cursor?.let {
                val contactIdIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
                val emailIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)

                while (it.moveToNext()) {
                    val contactId = it.getLong(contactIdIndex)
                    val email = it.getString(emailIndex)
                    if (email != null) {
                        contactsMap[contactId]?.emails?.add(email)
                    }
                }
            }
        } finally {
            cursor?.close()
        }
    }

    private data class ContactData(
        val id: Long,
        val name: String,
        val phones: MutableList<String>,
        val emails: MutableList<String>
    )
}
