package com.saagarsoni.dialer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
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

class MainActivity : Activity() {

    private var contacts: List<Contact> = emptyList()
    private var recentEntries: List<CallEntry> = emptyList()
    private val typedText: String get() = numberView.text.toString()

    private fun setTyped(v: String) {
        numberView.setText(v)
        numberView.setSelection(numberView.text.length)
    }

    private fun insertTyped(v: String) {
        val e = numberView.text
        var pos = numberView.selectionStart
        if (pos < 0 || pos > e.length) pos = e.length
        val end = numberView.selectionEnd
        if (end > pos) e.replace(pos, end, v) else e.insert(pos, v)
        numberView.setSelection(pos + v.length)
    }

    private fun backspaceTyped() {
        val e = numberView.text
        if (e.isEmpty()) return
        var a = numberView.selectionStart
        var b = numberView.selectionEnd
        if (a < 0) {
            a = e.length
            b = a
        }
        if (a == b) {
            if (a == 0) return
            e.delete(a - 1, a)
            numberView.setSelection(a - 1)
        } else {
            val lo = minOf(a, b)
            e.delete(lo, maxOf(a, b))
            numberView.setSelection(lo)
        }
    }

    private fun pasteFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = cm.primaryClip
        val raw = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this).toString() else ""
        val clean = Data.cleanNumber(raw)
        if (clean.isEmpty()) {
            toast("Clipboard mein number nahi mila")
        } else {
            insertTyped(clean)
        }
    }

    private lateinit var dialerPane: LinearLayout
    private lateinit var recentsPane: LinearLayout
    private lateinit var contactsPane: LinearLayout
    private lateinit var numberView: EditText
    private lateinit var pasteView: TextView
    private lateinit var backView: TextView
    private lateinit var searchBox: EditText
    private lateinit var recentsSearch: EditText
    private lateinit var contactsInfo: TextView
    private lateinit var recentsInfo: TextView
    private lateinit var contactsChips: Chips
    private lateinit var chipsHolder: FrameLayout
    private var contactsSel = "Sabhi"
    private var birthdays: List<Birthday> = emptyList()
    private var stamp = 0
    private var pendingDigit = ""
    private lateinit var recentsChips: Chips
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
        initTheme(this)
        applySystemBars(this, BAR)
        stamp = themeStamp(this)
        buildUi()
        handleIntent(intent)
        ensurePermissions()
        Birthdays.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        if (themeStamp(this) != stamp) {
            recreate()
            return
        }
        buildContactChips()
        if (has(Manifest.permission.READ_CONTACTS) || has(Manifest.permission.READ_CALL_LOG)) {
            loadData()
        } else {
            applyContactFilter()
        }
        Actions.checkPostCall(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DefaultDialer.REQ) {
            if (DefaultDialer.isDefault(this)) toast("Sampark ab default dialer hai")
        } else if (requestCode == SettingsActivity.REQ_PICK && resultCode == RESULT_OK) {
            val f = Actions.readPicked(this, data)
            if (f != null && pendingDigit.isNotEmpty()) {
                Store.setSpeed(this, pendingDigit, f.number, f.name)
                toast("Key $pendingDigit: " + (if (f.name.isEmpty()) f.number else f.name))
            }
            pendingDigit = ""
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
        val want = permsWanted.toMutableList()
        if (Build.VERSION.SDK_INT >= 33) want.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = want.filter { !has(it) }
        if (missing.isEmpty()) loadData() else requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        loadData()
    }

    private fun loadData() {
        Thread {
            val cs = if (has(Manifest.permission.READ_CONTACTS)) Data.loadContacts(this) else emptyList()
            val rec = if (has(Manifest.permission.READ_CALL_LOG)) Data.loadRecents(this, cs) else emptyList()
            val bd = if (has(Manifest.permission.READ_CONTACTS)) Data.loadBirthdays(this) else emptyList()
            runOnUiThread {
                contacts = cs
                birthdays = bd
                recentEntries = rec
                applyRecentsFilter()
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
        root.setBackgroundColor(BG)

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
        nav.setBackgroundColor(BAR)
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
        val gear = TextView(this)
        gear.text = "⚙\nSettings"
        gear.textSize = 12f
        gear.gravity = Gravity.CENTER
        gear.setTextColor(GRAY)
        gear.setPadding(0, dp(8), 0, dp(8))
        gear.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        nav.addView(gear, LinearLayout.LayoutParams(0, WRAP, 1f))
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
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    private fun buildDialer(): LinearLayout {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL

        val list = ListView(this)
        list.divider = null
        suggestAdapter = RowAdapter(this) { r -> Actions.call(this, r.number) }
        list.adapter = suggestAdapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val r = suggestAdapter.getItem(pos)
            setTyped(Data.cleanNumber(r.number))
        }
        p.addView(list, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val numRow = LinearLayout(this)
        numRow.orientation = LinearLayout.HORIZONTAL
        numRow.gravity = Gravity.CENTER_VERTICAL

        numberView = EditText(this)
        numberView.textSize = 32f
        numberView.setTextColor(INK)
        numberView.gravity = Gravity.CENTER
        numberView.setSingleLine()
        numberView.setBackgroundColor(Color.TRANSPARENT)
        numberView.showSoftInputOnFocus = false
        numberView.isCursorVisible = true
        numberView.filters = arrayOf(
            android.text.InputFilter { src, st, en, _, _, _ ->
                val sb = StringBuilder()
                for (i in st until en) {
                    val c = src[i]
                    if (c.isDigit() || c == '+' || c == '*' || c == '#') sb.append(c)
                }
                if (sb.length == en - st) null else sb.toString()
            }
        )
        numberView.onChange { refreshTyped() }
        val nlp = LinearLayout.LayoutParams(0, WRAP, 1f)
        nlp.leftMargin = dp(56)
        numRow.addView(numberView, nlp)

        backView = TextView(this)
        backView.text = "⌫"
        backView.textSize = 24f
        backView.setTextColor(GRAY)
        backView.gravity = Gravity.CENTER
        backView.setOnClickListener { backspaceTyped() }
        backView.setOnLongClickListener {
            numberView.setText("")
            true
        }
        numRow.addView(backView, LinearLayout.LayoutParams(dp(56), dp(56)))
        p.addView(numRow, LinearLayout.LayoutParams(MATCH, WRAP))

        pasteView = TextView(this)
        pasteView.text = "📋  Paste number"
        pasteView.textSize = 13f
        pasteView.setTextColor(GREEN)
        pasteView.gravity = Gravity.CENTER
        pasteView.setPadding(dp(16), dp(2), dp(16), dp(6))
        pasteView.setOnClickListener { pasteFromClipboard() }
        p.addView(pasteView, LinearLayout.LayoutParams(MATCH, WRAP))

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
        callBtn.background = circleBg(GREEN)
        callBtn.setOnClickListener { onDialCall() }
        val clp = LinearLayout.LayoutParams(dp(68), dp(68))
        clp.gravity = Gravity.CENTER_HORIZONTAL
        clp.topMargin = dp(8)
        clp.bottomMargin = dp(10)
        p.addView(callBtn, clp)

        return p
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
            insertTyped(d)
        }
        if (d == "0") {
            t.setOnLongClickListener {
                insertTyped("+")
                true
            }
        } else if (d.length == 1 && d[0] in '1'..'9') {
            t.setOnLongClickListener {
                if (typedText.isEmpty()) {
                    speedDial(d)
                    true
                } else {
                    false
                }
            }
        }
        return t
    }

    private fun speedDial(d: String) {
        val f = Store.speed(this, d)
        if (f != null) {
            toast("Speed dial $d: " + (if (f.name.isEmpty()) f.number else f.name))
            Actions.call(this, f.number)
            return
        }
        android.app.AlertDialog.Builder(this, dialogTheme())
            .setTitle("Speed dial $d")
            .setMessage("Is key par abhi koi contact set nahi hai. Abhi chunna hai?")
            .setPositiveButton("Contact chuno") { _, _ ->
                pendingDigit = d
                Actions.pickContact(this, SettingsActivity.REQ_PICK)
            }
            .setNegativeButton("Nahi", null)
            .show()
    }

    private fun buildRecents(): LinearLayout {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL

        recentsChips = Chips(this, listOf("Sabhi", "Missed", "Incoming", "Outgoing")) {
            applyRecentsFilter()
        }

        val sf = searchField(this, "Naam ya number se search karo") { applyRecentsFilter() }
        recentsSearch = sf.second
        val slp = LinearLayout.LayoutParams(MATCH, WRAP)
        slp.setMargins(dp(16), dp(8), dp(16), dp(6))
        p.addView(sf.first, slp)

        recentsInfo = infoView()
        p.addView(recentsInfo, LinearLayout.LayoutParams(MATCH, WRAP))

        val list = ListView(this)
        list.divider = null
        recentsAdapter = RowAdapter(this) { r -> Actions.call(this, r.number) }
        list.adapter = recentsAdapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val r = recentsAdapter.getItem(pos)
            if (r.number.isNotEmpty()) Actions.openDetail(this, r.number, r.name)
        }
        p.addView(list, LinearLayout.LayoutParams(MATCH, 0, 1f))
        p.addView(recentsChips.view, LinearLayout.LayoutParams(MATCH, WRAP))
        return p
    }

    private fun buildContacts(): LinearLayout {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL

        chipsHolder = FrameLayout(this)

        val sf = searchField(this, "Naam ya number se search karo") { applyContactFilter() }
        searchBox = sf.second
        val slp = LinearLayout.LayoutParams(MATCH, WRAP)
        slp.setMargins(dp(16), dp(8), dp(16), dp(6))
        p.addView(sf.first, slp)

        contactsInfo = infoView()
        p.addView(contactsInfo, LinearLayout.LayoutParams(MATCH, WRAP))

        val list = ListView(this)
        list.divider = null
        contactsAdapter = RowAdapter(this) { r -> Actions.call(this, r.number) }
        list.adapter = contactsAdapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val r = contactsAdapter.getItem(pos)
            if (r.number.isNotEmpty()) Actions.openDetail(this, r.number, r.name)
        }
        p.addView(list, LinearLayout.LayoutParams(MATCH, 0, 1f))
        p.addView(chipsHolder, LinearLayout.LayoutParams(MATCH, WRAP))
        return p
    }

    private fun buildContactChips() {
        val labels = ArrayList<String>()
        labels.add("Sabhi")
        labels.add("★ Favorites")
        labels.add("🎂 Birthdays")
        for (t in Store.tagList(this)) labels.add("🏷 $t")
        var idx = labels.indexOf(contactsSel)
        if (idx < 0) {
            idx = 0
            contactsSel = "Sabhi"
        }
        contactsChips = Chips(this, labels) { i ->
            contactsSel = labels[i]
            applyContactFilter()
        }
        contactsChips.select(idx)
        chipsHolder.removeAllViews()
        chipsHolder.addView(contactsChips.view, FrameLayout.LayoutParams(MATCH, WRAP))
    }

    private fun favRow(f: Fav): Row = Row(
        if (f.name.isEmpty()) f.number else f.name,
        f.number,
        f.number,
        if (f.name.isEmpty()) null else f.name
    )

    private fun birthdayRows(): List<Row> = birthdays.map {
        val d = Data.daysUntil(it)
        val whenTxt = if (d == 0) "Aaj! 🎉" else if (d == 1) "Kal" else "$d din baad"
        val age = Data.nextAge(it)
        val sub = it.day.toString() + " " + Data.MONTHS[it.month - 1] + " • " + whenTxt +
            (if (age > 0) " • $age saal" else "")
        Row(it.name, sub, it.number, it.name, if (d == 0) GREEN else GRAY)
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
        val empty = typedText.isEmpty()
        backView.visibility = if (empty) View.INVISIBLE else View.VISIBLE
        pasteView.visibility = if (empty) View.VISIBLE else View.INVISIBLE
        val d = typedText.filter { it.isDigit() }
        suggestAdapter.set(Data.match(contacts, d).map { Row(it.name, it.number, it.number, it.name) })
    }

    private fun applyRecentsFilter() {
        val q = recentsSearch.text.toString().trim().lowercase()
        val qd = q.filter { it.isDigit() }
        val f = recentsChips.selected
        val list = recentEntries.filter { e ->
            val typeOk = when (f) {
                1 -> e.type == 3 || e.type == 5
                2 -> e.type == 1
                3 -> e.type == 2
                else -> true
            }
            val nameOk = e.name?.lowercase()?.contains(q) == true
            val numOk = qd.isNotEmpty() && Data.digitsOf(e.number).contains(qd)
            typeOk && (q.isEmpty() || nameOk || numOk)
        }
        recentsAdapter.set(list.map { toRow(it) })
    }

    private fun applyContactFilter() {
        val q = searchBox.text.toString().trim().lowercase()
        val qd = q.filter { it.isDigit() }
        val sel = contactsSel
        val base: List<Row> = when {
            sel == "Sabhi" -> contacts.map { Row(it.name, it.number, it.number, it.name) }
            sel.startsWith("★") -> Store.favs(this).map { favRow(it) }
            sel.startsWith("🎂") -> birthdayRows()
            else -> Store.withTag(this, sel.removePrefix("🏷 ")).map { favRow(it) }
        }
        val list = if (q.isEmpty()) {
            base
        } else {
            base.filter {
                it.title.lowercase().contains(q) || (qd.isNotEmpty() && Data.digitsOf(it.number).contains(qd))
            }
        }
        contactsAdapter.set(list)
    }

    private fun handleIntent(i: Intent?) {
        val data = i?.data ?: return
        if (data.scheme == "tel") {
            val n = Uri.decode(data.schemeSpecificPart ?: "")
            setTyped(Data.cleanNumber(n))
            showTab(0)
        }
    }

    private fun onDialCall() {
        if (typedText.isEmpty()) {
            val last = recentEntries.firstOrNull { it.type == 2 && it.number.isNotEmpty() }
            if (last != null) setTyped(Data.cleanNumber(last.number))
            return
        }
        Actions.call(this, typedText)
    }
}
