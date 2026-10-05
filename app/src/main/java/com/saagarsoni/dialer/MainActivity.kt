package com.saagarsoni.dialer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private var contacts: List<Contact> = emptyList()
    private var recentEntries: List<CallEntry> = emptyList()
    private var typed = StringBuilder()
    private var pendingCall: String? = null

    private lateinit var dialerPane: LinearLayout
    private lateinit var recentsPane: LinearLayout
    private lateinit var contactsPane: LinearLayout
    private lateinit var numberView: TextView
    private lateinit var backView: TextView
    private lateinit var searchBox: EditText
    private lateinit var contactsInfo: TextView
    private lateinit var recentsInfo: TextView
    private lateinit var suggestAdapter: RowAdapter
    private lateinit var contactsAdapter: RowAdapter
    private lateinit var recentsAdapter: RowAdapter
    private val tabButtons = ArrayList<TextView>()

    private val permsWanted = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.CALL_PHONE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        buildUi()
        handleIntent(intent)
        ensurePermissions()
    }

    override fun onResume() {
        super.onResume()
        if (has(Manifest.permission.READ_CONTACTS) || has(Manifest.permission.READ_CALL_LOG)) {
            loadData()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    // ---------- permissions & data ----------

    private fun has(p: String): Boolean =
        checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun ensurePermissions() {
        val missing = permsWanted.filter { !has(it) }
        if (missing.isEmpty()) loadData() else requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        loadData()
        val p = pendingCall
        if (p != null) {
            pendingCall = null
            if (has(Manifest.permission.CALL_PHONE)) placeCall(p)
        }
    }

    private fun loadData() {
        Thread {
            val cs = if (has(Manifest.permission.READ_CONTACTS)) Data.loadContacts(this) else emptyList()
            val rec = if (has(Manifest.permission.READ_CALL_LOG)) Data.loadRecents(this, cs) else emptyList()
            runOnUiThread {
                contacts = cs
                recentEntries = rec
                recentsAdapter.set(rec.map { toRow(it) })
                applyContactFilter()
                refreshTyped()
                contactsInfo.visibility =
                    if (has(Manifest.permission.READ_CONTACTS)) View.GONE else View.VISIBLE
                recentsInfo.visibility =
                    if (has(Manifest.permission.READ_CALL_LOG)) View.GONE else View.VISIBLE
            }
        }.start()
    }

    private fun toRow(e: CallEntry): Row {
        val base = e.name ?: (if (e.number.isEmpty()) "Private number" else e.number)
        val title = if (e.count > 1) "$base (${e.count})" else base
        val parts = ArrayList<String>()
        parts.add(Data.typeLabel(e.type))
        parts.add(Data.fmtTime(e.date))
        val dur = Data.fmtDur(e.duration)
        if (dur.isNotEmpty() && e.type != 3) parts.add(dur)
        val bad = e.type == 3 || e.type == 5
        return Row(title, parts.joinToString(" • "), e.number, e.name, if (bad) RED else GRAY)
    }

    // ---------- UI ----------

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.WHITE)

        val frame = FrameLayout(this)
        root.addView(frame, LinearLayout.LayoutParams(MATCH, 0, 1f))

        dialerPane = buildDialer()
        recentsPane = buildRecents()
        contactsPane = buildContacts()
        frame.addView(dialerPane, FrameLayout.LayoutParams(MATCH, MATCH))
        frame.addView(recentsPane, FrameLayout.LayoutParams(MATCH, MATCH))
        frame.addView(contactsPane, FrameLayout.LayoutParams(MATCH, MATCH))

        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.setBackgroundColor(Color.parseColor("#F3F4F6"))
        val labels = arrayOf("⌨\nDialer", "🕘\nRecents", "👤\nContacts")
        for (i in labels.indices) {
            val b = TextView(this)
            b.text = labels[i]
            b.textSize = 12f
            b.gravity = Gravity.CENTER
            b.setPadding(0, dp(8), 0, dp(8))
            b.setOnClickListener { showTab(i) }
            tabButtons.add(b)
            nav.addView(b, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        root.addView(nav, LinearLayout.LayoutParams(MATCH, WRAP))

        setContentView(root)
        showTab(0)
    }

    private fun showTab(i: Int) {
        dialerPane.visibility = if (i == 0) View.VISIBLE else View.GONE
        recentsPane.visibility = if (i == 1) View.VISIBLE else View.GONE
        contactsPane.visibility = if (i == 2) View.VISIBLE else View.GONE
        for (idx in tabButtons.indices) {
            tabButtons[idx].setTextColor(if (idx == i) GREEN else GRAY)
        }
        if (i != 2) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(searchBox.windowToken, 0)
        }
    }

    private fun buildDialer(): LinearLayout {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL

        val list = ListView(this)
        list.divider = null
        suggestAdapter = RowAdapter(this) { r -> if (r.number.isNotEmpty()) placeCall(r.number) }
        list.adapter = suggestAdapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val r = suggestAdapter.getItem(pos)
            typed = StringBuilder(Data.cleanNumber(r.number))
            refreshTyped()
        }
        p.addView(list, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val numRow = LinearLayout(this)
        numRow.orientation = LinearLayout.HORIZONTAL
        numRow.gravity = Gravity.CENTER_VERTICAL

        numberView = TextView(this)
        numberView.textSize = 32f
        numberView.setTextColor(INK)
        numberView.gravity = Gravity.CENTER
        numberView.setSingleLine()
        numberView.ellipsize = TextUtils.TruncateAt.START
        val nlp = LinearLayout.LayoutParams(0, WRAP, 1f)
        nlp.leftMargin = dp(56)
        numRow.addView(numberView, nlp)

        backView = TextView(this)
        backView.text = "⌫"
        backView.textSize = 24f
        backView.setTextColor(GRAY)
        backView.gravity = Gravity.CENTER
        backView.setOnClickListener {
            if (typed.isNotEmpty()) {
                typed.setLength(typed.length - 1)
                refreshTyped()
            }
        }
        backView.setOnLongClickListener {
            typed.setLength(0)
            refreshTyped()
            true
        }
        numRow.addView(backView, LinearLayout.LayoutParams(dp(56), dp(56)))
        p.addView(numRow, LinearLayout.LayoutParams(MATCH, WRAP))

        val keys = arrayOf(
            arrayOf("1" to "", "2" to "ABC", "3" to "DEF"),
            arrayOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
            arrayOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
            arrayOf("*" to "", "0" to "+", "#" to "")
        )
        for (rowKeys in keys) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            for ((d, l) in rowKeys) {
                r.addView(keyView(d, l), LinearLayout.LayoutParams(0, dp(66), 1f))
            }
            p.addView(r, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        val callBtn = TextView(this)
        callBtn.text = "📞"
        callBtn.textSize = 26f
        callBtn.gravity = Gravity.CENTER
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(GREEN)
        callBtn.background = bg
        callBtn.setOnClickListener { onDialCall() }
        val clp = LinearLayout.LayoutParams(dp(68), dp(68))
        clp.gravity = Gravity.CENTER_HORIZONTAL
        clp.topMargin = dp(8)
        clp.bottomMargin = dp(10)
        p.addView(callBtn, clp)

        return p
    }

    private fun pressBg(): StateListDrawable {
        val sl = StateListDrawable()
        val pressed = GradientDrawable()
        pressed.shape = GradientDrawable.OVAL
        pressed.setColor(Color.parseColor("#E3E5E8"))
        sl.addState(intArrayOf(android.R.attr.state_pressed), pressed)
        sl.addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        return sl
    }

    private fun keyView(d: String, letters: String): TextView {
        val label = if (letters.isEmpty()) d else "$d\n$letters"
        val s = SpannableString(label)
        if (letters.isNotEmpty()) {
            s.setSpan(RelativeSizeSpan(0.4f), 1, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val t = TextView(this)
        t.text = s
        t.textSize = 26f
        t.setTextColor(INK)
        t.gravity = Gravity.CENTER
        t.background = pressBg()
        t.isClickable = true
        t.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            typed.append(d)
            refreshTyped()
        }
        if (d == "0") {
            t.setOnLongClickListener {
                typed.append("+")
                refreshTyped()
                true
            }
        }
        return t
    }

    private fun buildRecents(): LinearLayout {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL

        recentsInfo = infoView()
        p.addView(recentsInfo, LinearLayout.LayoutParams(MATCH, WRAP))

        val list = ListView(this)
        list.divider = null
        recentsAdapter = RowAdapter(this) { r -> if (r.number.isNotEmpty()) placeCall(r.number) }
        list.adapter = recentsAdapter
        list.setOnItemClickListener { _, _, pos, _ -> showOptions(recentsAdapter.getItem(pos)) }
        p.addView(list, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return p
    }

    private fun buildContacts(): LinearLayout {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL

        searchBox = EditText(this)
        searchBox.hint = "Naam ya number se search karo"
        searchBox.setSingleLine()
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                applyContactFilter()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        val slp = LinearLayout.LayoutParams(MATCH, WRAP)
        slp.setMargins(dp(16), dp(8), dp(16), dp(4))
        p.addView(searchBox, slp)

        contactsInfo = infoView()
        p.addView(contactsInfo, LinearLayout.LayoutParams(MATCH, WRAP))

        val list = ListView(this)
        list.divider = null
        contactsAdapter = RowAdapter(this) { r -> if (r.number.isNotEmpty()) placeCall(r.number) }
        list.adapter = contactsAdapter
        list.setOnItemClickListener { _, _, pos, _ -> showOptions(contactsAdapter.getItem(pos)) }
        p.addView(list, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return p
    }

    private fun infoView(): TextView {
        val t = TextView(this)
        t.text = "Permission band hai. Yahan tap karo, phir Permissions mein Contacts, Call logs aur Phone allow karo."
        t.textSize = 14f
        t.setTextColor(RED)
        t.setPadding(dp(16), dp(12), dp(16), dp(12))
        t.visibility = View.GONE
        t.setOnClickListener {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            i.data = Uri.fromParts("package", packageName, null)
            startActivity(i)
        }
        return t
    }

    // ---------- behaviour ----------

    private fun refreshTyped() {
        numberView.text = typed.toString()
        backView.visibility = if (typed.isEmpty()) View.INVISIBLE else View.VISIBLE
        val d = typed.toString().filter { it.isDigit() }
        suggestAdapter.set(Data.match(contacts, d).map { Row(it.name, it.number, it.number, it.name) })
    }

    private fun applyContactFilter() {
        val q = searchBox.text.toString().trim().lowercase()
        val qd = q.filter { it.isDigit() }
        val list = if (q.isEmpty()) {
            contacts
        } else {
            contacts.filter {
                it.name.lowercase().contains(q) || (qd.isNotEmpty() && it.digits.contains(qd))
            }
        }
        contactsAdapter.set(list.map { Row(it.name, it.number, it.number, it.name) })
    }

    private fun handleIntent(i: Intent?) {
        val data = i?.data ?: return
        if (data.scheme == "tel") {
            val n = Uri.decode(data.schemeSpecificPart ?: "")
            typed = StringBuilder(Data.cleanNumber(n))
            refreshTyped()
            showTab(0)
        }
    }

    private fun onDialCall() {
        if (typed.isEmpty()) {
            val last = recentEntries.firstOrNull { it.type == 2 && it.number.isNotEmpty() }
            if (last != null) {
                typed = StringBuilder(Data.cleanNumber(last.number))
                refreshTyped()
            }
            return
        }
        placeCall(typed.toString())
    }

    private fun placeCall(number: String) {
        val n = Data.cleanNumber(number)
        if (n.isEmpty()) return
        if (!has(Manifest.permission.CALL_PHONE)) {
            pendingCall = n
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 2)
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", n, null)))
        } catch (e: Exception) {
            toast("Call nahi lag paya")
        }
    }

    private fun showOptions(r: Row) {
        if (r.number.isEmpty()) return
        val labels = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()

        labels.add("📞  Call")
        acts.add { placeCall(r.number) }

        labels.add("💬  SMS")
        acts.add {
            safeStart(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", Data.cleanNumber(r.number), null)))
        }

        labels.add("🟢  WhatsApp")
        acts.add {
            safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + Data.waNumber(r.number))))
        }

        labels.add("📋  Number copy karo")
        acts.add {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("number", r.number))
            toast("Copy ho gaya")
        }

        if (r.name == null) {
            labels.add("➕  Contact save karo")
            acts.add {
                val i = Intent(ContactsContract.Intents.Insert.ACTION)
                i.type = ContactsContract.RawContacts.CONTENT_TYPE
                i.putExtra(ContactsContract.Intents.Insert.PHONE, r.number)
                safeStart(i)
            }
        }

        AlertDialog.Builder(this)
            .setTitle(r.name ?: r.number)
            .setItems(labels.toTypedArray()) { _, i -> acts[i]() }
            .show()
    }

    private fun safeStart(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            toast("Ye app phone mein nahi mila")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
