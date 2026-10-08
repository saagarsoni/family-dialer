package com.saagarsoni.dialer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar

// Phone ke (Google / Samsung) contacts mein hi naya contact banata ya purana edit karta hai.
class ContactEditActivity : Activity() {

    companion object {
        const val REQ_PERM = 61
    }

    private var editMode = false
    private var contactId = -1L
    private var rawId = -1L
    private var nameRowId = -1L
    private var phoneRowId = -1L
    private var emailRowId = -1L
    private var orgRowId = -1L
    private var eventRowId = -1L
    private var oldNumber = ""

    private var accType: String? = null
    private var accName: String? = null
    private var accountOptions: List<Triple<String?, String?, Int>> = emptyList()

    private var bYear = 0
    private var bMonth = 0
    private var bDay = 0
    private var keepYear = true
    private val tags = ArrayList<String>()

    private lateinit var nameEt: EditText
    private lateinit var numEt: EditText
    private lateinit var emailEt: EditText
    private lateinit var orgEt: EditText
    private lateinit var bdayView: TextView
    private lateinit var yearView: TextView
    private lateinit var tagView: TextView
    private lateinit var accView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initTheme(this)
        applySystemBars(this, BG)

        val number = intent.getStringExtra("number") ?: ""
        val name = intent.getStringExtra("name") ?: ""
        editMode = intent.getBooleanExtra("edit", false)
        oldNumber = number

        accountOptions = loadAccounts()
        pickDefaultAccount()

        val preName = name
        var preNumber = number
        if (editMode && number.isNotEmpty()) loadExisting(number)
        if (editMode && contactId < 0) editMode = false
        if (editMode) preNumber = numberTextFromRow ?: number
        tags.addAll(Store.tagsOf(this, number))

        buildUi(preName, preNumber)
        if (editMode) {
            fillExisting()
        }
    }

    // ---------- data loading ----------

    private var numberTextFromRow: String? = null
    private var existingName = ""
    private var existingEmail = ""
    private var existingOrg = ""

    private fun loadAccounts(): List<Triple<String?, String?, Int>> {
        val counts = LinkedHashMap<String, Int>()
        val keep = arrayOf(
            "com.google", "com.osp.app.signin", "vnd.sec.contact.phone", "com.android.local",
            "com.microsoft.office.outlook", "com.android.exchange"
        )
        try {
            contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.ACCOUNT_TYPE, ContactsContract.RawContacts.ACCOUNT_NAME),
                ContactsContract.RawContacts.DELETED + "=0",
                null,
                null
            )?.use {
                while (it.moveToNext()) {
                    val t = it.getString(0) ?: ""
                    val n = it.getString(1) ?: ""
                    if (t.isNotEmpty() && keep.none { k -> t == k }) continue
                    val key = "$t|$n"
                    counts[key] = (counts[key] ?: 0) + 1
                }
            }
        } catch (e: Exception) {
        }
        val out = ArrayList<Triple<String?, String?, Int>>()
        for ((k, c) in counts) {
            val i = k.indexOf('|')
            val t = k.substring(0, i)
            val n = k.substring(i + 1)
            out.add(Triple(if (t.isEmpty()) null else t, if (n.isEmpty()) null else n, c))
        }
        return out.sortedByDescending { it.third }
    }

    private fun pickDefaultAccount() {
        val g = accountOptions.firstOrNull { it.first == "com.google" }
        val pick = g ?: accountOptions.firstOrNull { it.first != null }
        if (pick != null) {
            accType = pick.first
            accName = pick.second
        } else {
            accType = null
            accName = null
        }
    }

    private fun loadExisting(number: String) {
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use {
                if (it.moveToFirst()) contactId = it.getLong(0)
            }
            if (contactId < 0) return
            contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.RawContacts._ID,
                    ContactsContract.RawContacts.ACCOUNT_TYPE,
                    ContactsContract.RawContacts.ACCOUNT_NAME
                ),
                ContactsContract.RawContacts.CONTACT_ID + "=? AND " + ContactsContract.RawContacts.DELETED + "=0",
                arrayOf(contactId.toString()),
                null
            )?.use {
                if (it.moveToFirst()) {
                    rawId = it.getLong(0)
                    accType = it.getString(1)
                    accName = it.getString(2)
                }
            }
            if (rawId < 0) return
            val key = Data.key10(number)
            var firstPhoneId = -1L
            var firstPhoneText: String? = null
            contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data._ID,
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.Data.DATA1,
                    ContactsContract.Data.DATA2
                ),
                ContactsContract.Data.RAW_CONTACT_ID + "=?",
                arrayOf(rawId.toString()),
                null
            )?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val mime = it.getString(1) ?: continue
                    val v = it.getString(2) ?: ""
                    when (mime) {
                        StructuredName.CONTENT_ITEM_TYPE -> {
                            nameRowId = id
                            existingName = v
                        }
                        Phone.CONTENT_ITEM_TYPE -> {
                            if (firstPhoneId < 0) {
                                firstPhoneId = id
                                firstPhoneText = v
                            }
                            if (phoneRowId < 0 && Data.key10(v) == key) {
                                phoneRowId = id
                                numberTextFromRow = v
                            }
                        }
                        Email.CONTENT_ITEM_TYPE -> if (emailRowId < 0) {
                            emailRowId = id
                            existingEmail = v
                        }
                        Organization.CONTENT_ITEM_TYPE -> if (orgRowId < 0) {
                            orgRowId = id
                            existingOrg = v
                        }
                        Event.CONTENT_ITEM_TYPE -> {
                            if (it.getString(3) == Event.TYPE_BIRTHDAY.toString()) parseBirthday(v, id)
                        }
                    }
                }
            }
            if (phoneRowId < 0 && firstPhoneId >= 0) {
                phoneRowId = firstPhoneId
                numberTextFromRow = firstPhoneText
            }
        } catch (e: Exception) {
        }
    }

    private fun parseBirthday(raw: String, id: Long) {
        if (eventRowId >= 0) return
        val parts = raw.trim().split("-").filter { p -> p.isNotEmpty() }
        var y = 0
        var m = 0
        var d = 0
        if (parts.size >= 3) {
            y = parts[0].toIntOrNull() ?: 0
            m = parts[1].toIntOrNull() ?: 0
            d = parts[2].take(2).toIntOrNull() ?: 0
        } else if (parts.size == 2) {
            m = parts[0].toIntOrNull() ?: 0
            d = parts[1].take(2).toIntOrNull() ?: 0
        }
        if (m < 1 || m > 12 || d < 1 || d > 31) return
        eventRowId = id
        bYear = if (y > 1900) y else 0
        bMonth = m
        bDay = d
        keepYear = y > 1900
    }

    // ---------- UI ----------

    private fun field(hint: String, type: Int): EditText {
        val e = EditText(this)
        e.themed(hint)
        e.inputType = type
        e.setSingleLine()
        return e
    }

    private fun label(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 13f
        t.setTextColor(GRAY)
        t.setPadding(0, dp(14), 0, 0)
        return t
    }

    private fun tapRow(): TextView {
        val t = TextView(this)
        t.textSize = 16f
        t.setTextColor(INK)
        t.setPadding(0, dp(12), 0, dp(12))
        return t
    }

    private fun buildUi(preName: String, preNumber: String) {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(BG)

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val back = TextView(this)
        back.text = "←"
        back.textSize = 22f
        back.setTextColor(GRAY)
        back.gravity = Gravity.CENTER
        back.setOnClickListener { finish() }
        top.addView(back, LinearLayout.LayoutParams(dp(52), dp(52)))
        val title = TextView(this)
        title.text = if (editMode) "Contact edit" else "Naya contact"
        title.textSize = 20f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setTextColor(INK)
        top.addView(title, LinearLayout.LayoutParams(0, WRAP, 1f))
        val save = TextView(this)
        save.text = "Save"
        save.setTextColor(Color.WHITE)
        save.gravity = Gravity.CENTER
        save.setPadding(dp(18), dp(8), dp(18), dp(8))
        save.background = roundBg(this, GREEN, 18)
        save.setOnClickListener { save() }
        val slp = LinearLayout.LayoutParams(WRAP, WRAP)
        slp.rightMargin = dp(16)
        top.addView(save, slp)
        root.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))

        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), 0, dp(20), dp(32))
        scroll.addView(col, LinearLayout.LayoutParams(MATCH, WRAP))
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        col.addView(label("Naam"))
        nameEt = field("Naam", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        nameEt.setText(preName)
        col.addView(nameEt, LinearLayout.LayoutParams(MATCH, WRAP))

        col.addView(label("Number"))
        numEt = field("Phone number", InputType.TYPE_CLASS_PHONE)
        numEt.setText(preNumber)
        col.addView(numEt, LinearLayout.LayoutParams(MATCH, WRAP))

        col.addView(label("Email (optional)"))
        emailEt = field("Email", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        col.addView(emailEt, LinearLayout.LayoutParams(MATCH, WRAP))

        col.addView(label("Company (optional)"))
        orgEt = field("Company / kaam", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        col.addView(orgEt, LinearLayout.LayoutParams(MATCH, WRAP))

        col.addView(label("Birthday"))
        val bRow = LinearLayout(this)
        bRow.orientation = LinearLayout.HORIZONTAL
        bRow.gravity = Gravity.CENTER_VERTICAL
        bdayView = tapRow()
        bdayView.setOnClickListener { pickBirthday() }
        bRow.addView(bdayView, LinearLayout.LayoutParams(0, WRAP, 1f))
        val clear = TextView(this)
        clear.text = "✕"
        clear.textSize = 16f
        clear.setTextColor(GRAY)
        clear.gravity = Gravity.CENTER
        clear.setOnClickListener {
            bMonth = 0
            bDay = 0
            bYear = 0
            showBirthday()
        }
        bRow.addView(clear, LinearLayout.LayoutParams(dp(40), dp(40)))
        col.addView(bRow, LinearLayout.LayoutParams(MATCH, WRAP))
        yearView = TextView(this)
        yearView.textSize = 13f
        yearView.setTextColor(GREEN)
        yearView.setPadding(0, 0, 0, dp(4))
        yearView.setOnClickListener {
            keepYear = !keepYear
            showBirthday()
        }
        col.addView(yearView, LinearLayout.LayoutParams(MATCH, WRAP))

        col.addView(label("Tags"))
        tagView = tapRow()
        tagView.setOnClickListener { pickTags() }
        col.addView(tagView, LinearLayout.LayoutParams(MATCH, WRAP))

        if (!editMode) {
            col.addView(label("Kahan save karu"))
            accView = tapRow()
            accView.setOnClickListener { pickAccount() }
            col.addView(accView, LinearLayout.LayoutParams(MATCH, WRAP))
        } else {
            accView = tapRow()
        }

        if (editMode) {
            val del = TextView(this)
            del.text = "🗑  Contact delete karo"
            del.setTextColor(Color.WHITE)
            del.gravity = Gravity.CENTER
            del.setPadding(0, dp(12), 0, dp(12))
            del.background = roundBg(this, RED, 10)
            del.setOnClickListener { confirmDelete() }
            val dlp = LinearLayout.LayoutParams(MATCH, WRAP)
            dlp.topMargin = dp(28)
            col.addView(del, dlp)
        }

        setContentView(root)
        showBirthday()
        showTags()
        showAccount()
        if (!editMode) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            nameEt.requestFocus()
        }
    }

    private fun fillExisting() {
        nameEt.setText(existingName.ifEmpty { intent.getStringExtra("name") ?: "" })
        emailEt.setText(existingEmail)
        orgEt.setText(existingOrg)
        showBirthday()
    }

    private fun showBirthday() {
        if (bMonth < 1) {
            bdayView.text = "🎂  Birthday jodo"
            bdayView.setTextColor(GRAY)
            yearView.visibility = View.GONE
            return
        }
        bdayView.setTextColor(INK)
        val y = if (keepYear && bYear > 1900) " " + bYear else ""
        bdayView.text = "🎂  " + bDay + " " + Data.MONTHS[bMonth - 1] + y
        yearView.visibility = View.VISIBLE
        yearView.text = if (keepYear) "☑  Saal bhi save hoga (tap karke hata do)" else "☐  Sirf din aur mahina save hoga (tap karke saal jodo)"
    }

    private fun pickBirthday() {
        val c = Calendar.getInstance()
        var y = if (bYear > 1900) bYear else c.get(Calendar.YEAR) - 25
        var m = if (bMonth > 0) bMonth - 1 else c.get(Calendar.MONTH)
        var d = if (bDay > 0) bDay else c.get(Calendar.DAY_OF_MONTH)
        DatePickerDialog(
            this,
            dialogTheme(),
            { _, yy, mm, dd ->
                bYear = yy
                bMonth = mm + 1
                bDay = dd
                keepYear = true
                showBirthday()
            },
            y,
            m,
            d
        ).show()
    }

    private fun showTags() {
        tagView.text = if (tags.isEmpty()) "🏷  Tag lagao" else "🏷  " + tags.joinToString(", ")
        tagView.setTextColor(if (tags.isEmpty()) GRAY else INK)
    }

    private fun pickTags() {
        val all = Store.tagList(this)
        val checked = BooleanArray(all.size) { tags.contains(all[it]) }
        fun apply() {
            tags.clear()
            for (i in all.indices) if (checked[i]) tags.add(all[i])
        }
        AlertDialog.Builder(this, dialogTheme())
            .setTitle("Tags")
            .setMultiChoiceItems(all.toTypedArray(), checked) { _, i, c -> checked[i] = c }
            .setPositiveButton("Theek hai") { _, _ ->
                apply()
                showTags()
            }
            .setNeutralButton("Naya tag") { _, _ ->
                apply()
                newTag()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun newTag() {
        val et = EditText(this)
        et.themed("Tag ka naam")
        et.setSingleLine()
        val box = LinearLayout(this)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(et, LinearLayout.LayoutParams(MATCH, WRAP))
        AlertDialog.Builder(this, dialogTheme())
            .setTitle("Naya tag")
            .setView(box)
            .setPositiveButton("Add") { _, _ ->
                val t = et.text.toString().trim()
                if (t.isNotEmpty()) {
                    Store.addTag(this, t)
                    val real = Store.tagList(this).firstOrNull { it.equals(t, true) } ?: t
                    if (!tags.contains(real)) tags.add(real)
                }
                showTags()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAccount() {
        accView.text = "👤  " + (if (accName == null) "Sirf phone mein (sync nahi hoga)" else accName)
    }

    private fun pickAccount() {
        val labels = ArrayList<String>()
        for (a in accountOptions) labels.add((a.second ?: "Phone") + "  (" + a.third + " contacts)")
        labels.add("Sirf phone mein (sync nahi hoga)")
        AlertDialog.Builder(this, dialogTheme())
            .setTitle("Kahan save karu?")
            .setItems(labels.toTypedArray()) { _, i ->
                if (i < accountOptions.size) {
                    accType = accountOptions[i].first
                    accName = accountOptions[i].second
                } else {
                    accType = null
                    accName = null
                }
                showAccount()
            }
            .show()
    }

    // ---------- save / delete ----------

    private fun birthdayString(): String {
        if (bMonth < 1) return ""
        return if (keepYear && bYear > 1900) {
            String.format("%04d-%02d-%02d", bYear, bMonth, bDay)
        } else {
            String.format("--%02d-%02d", bMonth, bDay)
        }
    }

    private fun save() {
        val name = nameEt.text.toString().trim()
        val num = Data.cleanNumber(numEt.text.toString())
        if (name.isEmpty() && num.isEmpty()) {
            toast("Naam ya number likho")
            return
        }
        if (checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_CONTACTS), REQ_PERM)
            return
        }
        try {
            if (editMode && rawId >= 0) updateContact(name, num) else insertContact(name, num)
        } catch (e: Exception) {
            toast("Save nahi ho paya")
            return
        }

        val oldKey = Data.key10(oldNumber)
        val newKey = Data.key10(num)
        if (oldKey.isNotEmpty() && oldKey != newKey) Store.setTags(this, oldNumber, "", emptyList())
        if (newKey.isNotEmpty()) Store.setTags(this, num, name, tags)

        val out = Intent()
        out.putExtra("number", num)
        out.putExtra("name", name)
        setResult(RESULT_OK, out)
        toast("Contact save ho gaya")
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERM) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                save()
            } else {
                toast("Contact save karne ke liye permission chahiye")
            }
        }
    }

    private fun insertContact(name: String, num: String) {
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, accType)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accName)
                .build()
        )
        fun ins(mime: String): ContentProviderOperation.Builder =
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, mime)

        if (name.isNotEmpty()) {
            ops.add(ins(StructuredName.CONTENT_ITEM_TYPE).withValue(StructuredName.DISPLAY_NAME, name).build())
        }
        if (num.isNotEmpty()) {
            ops.add(
                ins(Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.NUMBER, num)
                    .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                    .build()
            )
        }
        val email = emailEt.text.toString().trim()
        if (email.isNotEmpty()) {
            ops.add(ins(Email.CONTENT_ITEM_TYPE).withValue(Email.ADDRESS, email).withValue(Email.TYPE, Email.TYPE_HOME).build())
        }
        val org = orgEt.text.toString().trim()
        if (org.isNotEmpty()) {
            ops.add(ins(Organization.CONTENT_ITEM_TYPE).withValue(Organization.COMPANY, org).build())
        }
        val bd = birthdayString()
        if (bd.isNotEmpty()) {
            ops.add(
                ins(Event.CONTENT_ITEM_TYPE)
                    .withValue(Event.START_DATE, bd)
                    .withValue(Event.TYPE, Event.TYPE_BIRTHDAY)
                    .build()
            )
        }
        contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    private fun upsert(
        ops: ArrayList<ContentProviderOperation>,
        rowId: Long,
        mime: String,
        col: String,
        value: String,
        extraCol: String?,
        extraVal: Int
    ) {
        if (rowId >= 0) {
            if (value.isEmpty()) {
                ops.add(
                    ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                        .withSelection(ContactsContract.Data._ID + "=?", arrayOf(rowId.toString()))
                        .build()
                )
            } else {
                ops.add(
                    ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                        .withSelection(ContactsContract.Data._ID + "=?", arrayOf(rowId.toString()))
                        .withValue(col, value)
                        .build()
                )
            }
        } else if (value.isNotEmpty()) {
            val b = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                .withValue(ContactsContract.Data.MIMETYPE, mime)
                .withValue(col, value)
            if (extraCol != null) b.withValue(extraCol, extraVal)
            ops.add(b.build())
        }
    }

    private fun updateContact(name: String, num: String) {
        val ops = ArrayList<ContentProviderOperation>()
        upsert(ops, nameRowId, StructuredName.CONTENT_ITEM_TYPE, StructuredName.DISPLAY_NAME, name, null, 0)
        upsert(ops, phoneRowId, Phone.CONTENT_ITEM_TYPE, Phone.NUMBER, num, Phone.TYPE, Phone.TYPE_MOBILE)
        upsert(ops, emailRowId, Email.CONTENT_ITEM_TYPE, Email.ADDRESS, emailEt.text.toString().trim(), Email.TYPE, Email.TYPE_HOME)
        upsert(ops, orgRowId, Organization.CONTENT_ITEM_TYPE, Organization.COMPANY, orgEt.text.toString().trim(), null, 0)
        upsert(ops, eventRowId, Event.CONTENT_ITEM_TYPE, Event.START_DATE, birthdayString(), Event.TYPE, Event.TYPE_BIRTHDAY)
        if (ops.isNotEmpty()) contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this, dialogTheme())
            .setMessage("Ye contact phone se delete kar du?")
            .setPositiveButton("Haan") { _, _ ->
                if (checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.WRITE_CONTACTS), REQ_PERM + 1)
                    toast("Permission allow karke dobara try karo")
                } else {
                    try {
                        contentResolver.delete(
                            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
                            null,
                            null
                        )
                        val out = Intent()
                        out.putExtra("number", oldNumber)
                        out.putExtra("name", "")
                        setResult(RESULT_OK, out)
                        toast("Contact delete ho gaya")
                        finish()
                    } catch (e: Exception) {
                        toast("Delete nahi ho paya")
                    }
                }
            }
            .setNegativeButton("Nahi", null)
            .show()
    }
}
