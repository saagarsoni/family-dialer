package com.saagarsoni.dialer

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class DetailActivity : Activity() {

    private var number = ""
    private var name: String? = null

    private lateinit var starView: TextView
    private lateinit var notesBox: LinearLayout
    private lateinit var remTitle: TextView
    private lateinit var remBox: LinearLayout
    private lateinit var histBox: LinearLayout
    private lateinit var noteInput: EditText

    private var stamp = 0
    private lateinit var tagBox: LinearLayout
    private lateinit var recTitle: TextView
    private lateinit var recBox: LinearLayout
    private var recOpen = false
    private lateinit var flagView: TextView
    private lateinit var histTitle: TextView
    private lateinit var histPreview: TextView
    private var histOpen = false
    private var histCount = 0
    private var recCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initTheme(this)
        applySystemBars(this, BG)
        stamp = themeStamp(this)
        number = intent.getStringExtra("number") ?: ""
        val n = intent.getStringExtra("name")
        name = if (n.isNullOrEmpty()) Data.lookupName(this, number) else n
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (themeStamp(this) != stamp) {
            recreate()
            return
        }
        refresh()
        Actions.checkPostCall(this)
    }

    // ---------- UI building ----------

    private fun iconBtn(text: String, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 22f
        t.setTextColor(GRAY)
        t.gravity = Gravity.CENTER
        t.setOnClickListener { onClick() }
        return t
    }

    private fun actionBtn(emoji: String, label: String, onClick: () -> Unit): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        box.setPadding(0, dp(10), 0, dp(10))
        box.isClickable = true
        box.setOnClickListener { onClick() }

        val e = TextView(this)
        e.text = emoji
        e.textSize = 24f
        e.gravity = Gravity.CENTER
        box.addView(e)

        val l = TextView(this)
        l.text = label
        l.textSize = 12f
        l.setTextColor(INK)
        l.gravity = Gravity.CENTER
        box.addView(l)
        return box
    }

    private fun sectionTitle(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 15f
        t.typeface = Typeface.DEFAULT_BOLD
        t.setTextColor(GREEN)
        t.setPadding(0, dp(20), 0, dp(6))
        return t
    }

    private fun hint(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 13f
        t.setTextColor(GRAY)
        t.setPadding(0, dp(4), 0, dp(4))
        return t
    }

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(BG)

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(iconBtn("←") { finish() }, LinearLayout.LayoutParams(dp(52), dp(52)))
        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        starView = iconBtn("☆") { toggleFav() }
        top.addView(starView, LinearLayout.LayoutParams(dp(52), dp(52)))
        root.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(BG)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), 0, dp(20), dp(24))
        scroll.addView(col, LinearLayout.LayoutParams(MATCH, WRAP))
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val shownName = name ?: (if (number.isEmpty()) "Private number" else number)

        val av = TextView(this)
        av.gravity = Gravity.CENTER
        av.setTextColor(Color.WHITE)
        av.textSize = 34f
        av.typeface = Typeface.DEFAULT_BOLD
        av.background = circleBg(avatarColor(shownName))
        av.text = (name?.trim()?.firstOrNull() ?: '#').uppercaseChar().toString()
        val alp = LinearLayout.LayoutParams(dp(88), dp(88))
        alp.gravity = Gravity.CENTER_HORIZONTAL
        col.addView(av, alp)

        val nameView = TextView(this)
        nameView.text = shownName
        nameView.textSize = 22f
        nameView.typeface = Typeface.DEFAULT_BOLD
        nameView.setTextColor(INK)
        nameView.gravity = Gravity.CENTER
        nameView.setPadding(0, dp(12), 0, 0)
        col.addView(nameView, LinearLayout.LayoutParams(MATCH, WRAP))

        if (name != null) {
            val numView = TextView(this)
            numView.text = number
            numView.textSize = 15f
            numView.setTextColor(GRAY)
            numView.gravity = Gravity.CENTER
            col.addView(numView, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        flagView = TextView(this)
        flagView.textSize = 14f
        flagView.setTextColor(Color.WHITE)
        flagView.gravity = Gravity.CENTER
        flagView.setPadding(dp(14), dp(8), dp(14), dp(8))
        flagView.background = roundBg(this, RED, 10)
        flagView.visibility = View.GONE
        val fvp = LinearLayout.LayoutParams(MATCH, WRAP)
        fvp.topMargin = dp(12)
        col.addView(flagView, fvp)

        if (number.isNotEmpty()) {
            val row1 = LinearLayout(this)
            row1.orientation = LinearLayout.HORIZONTAL
            row1.addView(actionBtn("📞", "Call") { Actions.call(this, number) }, LinearLayout.LayoutParams(0, WRAP, 1f))
            row1.addView(actionBtn("💬", "SMS") { Actions.sms(this, number) }, LinearLayout.LayoutParams(0, WRAP, 1f))
            row1.addView(actionBtn("🟢", "WhatsApp") { Actions.whatsapp(this, number) }, LinearLayout.LayoutParams(0, WRAP, 1f))
            val rlp = LinearLayout.LayoutParams(MATCH, WRAP)
            rlp.topMargin = dp(16)
            col.addView(row1, rlp)

            val row2 = LinearLayout(this)
            row2.orientation = LinearLayout.HORIZONTAL
            row2.addView(actionBtn("⏰", "Reminder") { Actions.askReminder(this, number, name, "") }, LinearLayout.LayoutParams(0, WRAP, 1f))
            row2.addView(actionBtn("📋", "Copy") { Actions.copy(this, number) }, LinearLayout.LayoutParams(0, WRAP, 1f))
            if (name == null) {
                row2.addView(actionBtn("➕", "Save") { openEdit() }, LinearLayout.LayoutParams(0, WRAP, 1f))
            } else {
                row2.addView(actionBtn("✎", "Edit") { openEdit() }, LinearLayout.LayoutParams(0, WRAP, 1f))
            }
            col.addView(row2, LinearLayout.LayoutParams(MATCH, WRAP))

            val row3 = LinearLayout(this)
            row3.orientation = LinearLayout.HORIZONTAL
            row3.addView(
                actionBtn("🚫", "Fraud / Block") {
                    Actions.flagDialog(this, number, name) { refreshFlag() }
                },
                LinearLayout.LayoutParams(0, WRAP, 1f)
            )
            row3.addView(
                actionBtn("📹", "Video") { Actions.videoMenu(this, number) },
                LinearLayout.LayoutParams(0, WRAP, 1f)
            )
            row3.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            col.addView(row3, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        // tags
        tagBox = LinearLayout(this)
        tagBox.orientation = LinearLayout.HORIZONTAL
        if (number.isNotEmpty()) {
            col.addView(sectionTitle("🏷  Tags"))
            val hs = HorizontalScrollView(this)
            hs.isHorizontalScrollBarEnabled = false
            hs.addView(tagBox)
            col.addView(hs, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        // notes
        col.addView(sectionTitle("📝  Notes"))
        val inRow = LinearLayout(this)
        inRow.orientation = LinearLayout.HORIZONTAL
        inRow.gravity = Gravity.CENTER_VERTICAL
        noteInput = EditText(this)
        noteInput.themed("Note likho...")
        inRow.addView(noteInput, LinearLayout.LayoutParams(0, WRAP, 1f))
        val save = TextView(this)
        save.text = "Save"
        save.setTextColor(Color.WHITE)
        save.gravity = Gravity.CENTER
        save.setPadding(dp(16), dp(8), dp(16), dp(8))
        save.background = roundBg(this, GREEN, 8)
        save.setOnClickListener {
            val t = noteInput.text.toString().trim()
            if (t.isNotEmpty()) {
                Store.addNote(this, number, t)
                noteInput.setText("")
                refreshNotes()
            }
        }
        val slp = LinearLayout.LayoutParams(WRAP, WRAP)
        slp.leftMargin = dp(8)
        inRow.addView(save, slp)
        col.addView(inRow, LinearLayout.LayoutParams(MATCH, WRAP))

        notesBox = LinearLayout(this)
        notesBox.orientation = LinearLayout.VERTICAL
        col.addView(notesBox, LinearLayout.LayoutParams(MATCH, WRAP))

        // reminders
        remTitle = sectionTitle("⏰  Reminders")
        col.addView(remTitle)
        remBox = LinearLayout(this)
        remBox.orientation = LinearLayout.VERTICAL
        col.addView(remBox, LinearLayout.LayoutParams(MATCH, WRAP))

        // recordings
        recTitle = sectionTitle("🎙  Call recordings")
        recTitle.visibility = View.GONE
        recTitle.setOnClickListener {
            if (recCount > 0) {
                recOpen = !recOpen
                updateRecTitle()
            }
        }
        col.addView(recTitle)
        recBox = LinearLayout(this)
        recBox.orientation = LinearLayout.VERTICAL
        col.addView(recBox, LinearLayout.LayoutParams(MATCH, WRAP))

        // history
        histTitle = sectionTitle("📞  Call history")
        histTitle.setOnClickListener {
            if (histCount > 0) {
                histOpen = !histOpen
                updateHistTitle()
            }
        }
        col.addView(histTitle)
        histPreview = hint("")
        histPreview.visibility = View.GONE
        histPreview.setOnClickListener {
            histOpen = true
            updateHistTitle()
        }
        col.addView(histPreview)
        histBox = LinearLayout(this)
        histBox.orientation = LinearLayout.VERTICAL
        col.addView(histBox, LinearLayout.LayoutParams(MATCH, WRAP))

        setContentView(root)
    }

    // ---------- data ----------

    private fun tagChip(text: String, filled: Boolean, onClick: (() -> Unit)?): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 13f
        t.gravity = Gravity.CENTER
        t.setPadding(dp(14), dp(7), dp(14), dp(7))
        if (filled) {
            t.background = roundBg(this, GREEN, 18)
            t.setTextColor(Color.WHITE)
        } else {
            t.background = roundBg(this, PRESS, 18)
            t.setTextColor(INK)
        }
        if (onClick != null) t.setOnClickListener { onClick() }
        return t
    }

    private fun refreshTags() {
        tagBox.removeAllViews()
        if (number.isEmpty()) return
        val mine = Store.tagsOf(this, number)
        for (t in mine) {
            val lp = LinearLayout.LayoutParams(WRAP, WRAP)
            lp.rightMargin = dp(8)
            tagBox.addView(tagChip(t, true) { editTags() }, lp)
        }
        tagBox.addView(
            tagChip(if (mine.isEmpty()) "＋ Tag lagao" else "✎ Badlo", false) { editTags() },
            LinearLayout.LayoutParams(WRAP, WRAP)
        )
    }

    private fun editTags() {
        val all = Store.tagList(this)
        val mine = Store.tagsOf(this, number)
        val checked = BooleanArray(all.size) { mine.contains(all[it]) }

        fun selected(): List<String> {
            val out = ArrayList<String>()
            for (i in all.indices) if (checked[i]) out.add(all[i])
            return out
        }

        AlertDialog.Builder(this, dialogTheme())
            .setTitle("Tags")
            .setMultiChoiceItems(all.toTypedArray(), checked) { _, i, c -> checked[i] = c }
            .setPositiveButton("Save") { _, _ ->
                Store.setTags(this, number, name ?: "", selected())
                refreshTags()
            }
            .setNeutralButton("Naya tag") { _, _ -> newTag(selected()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun newTag(keep: List<String>) {
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
                    val l = keep.toMutableList()
                    val real = Store.tagList(this).firstOrNull { it.equals(t, true) } ?: t
                    if (!l.contains(real)) l.add(real)
                    Store.setTags(this, number, name ?: "", l)
                }
                refreshTags()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshRecordings() {
        recBox.removeAllViews()
        if (number.isEmpty()) {
            recTitle.visibility = View.GONE
            return
        }
        if (!Recordings.hasAccess(this)) {
            recTitle.visibility = View.VISIBLE
            recTitle.text = "🎙  Call recordings"
            recBox.visibility = View.VISIBLE
            recCount = 0
            val h = hint("Samsung ki call recordings dekhne ke liye yahan tap karke permission do")
            h.setTextColor(GREEN)
            h.setOnClickListener { requestPermissions(arrayOf(Recordings.permission()), 5) }
            recBox.addView(h)
            return
        }
        Thread {
            val list = Recordings.forContact(this, number, name)
            runOnUiThread { fillRecordings(list) }
        }.start()
    }

    private fun updateRecTitle() {
        recTitle.text = "🎙  Call recordings ($recCount)   " + (if (recOpen) "▴" else "▾")
        recBox.visibility = if (recOpen) View.VISIBLE else View.GONE
    }

    private fun fillRecordings(list: List<Rec>) {
        recBox.removeAllViews()
        recCount = list.size
        recTitle.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        if (list.isNotEmpty()) updateRecTitle()
        for (r in list) {
            val dur = Data.fmtDur(r.durMs / 1000L)
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            line.gravity = Gravity.CENTER_VERTICAL
            val t = TextView(this)
            t.text = "▶   " + Data.fmtTime(r.ts) + (if (dur.isNotEmpty()) " • $dur" else "")
            t.textSize = 15f
            t.setTextColor(INK)
            t.setPadding(0, dp(10), 0, dp(10))
            t.setOnClickListener { playRec(r) }
            line.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
            val sh = iconBtn("📤") { shareRec(r) }
            sh.textSize = 20f
            line.addView(sh, LinearLayout.LayoutParams(dp(48), dp(44)))
            recBox.addView(line, LinearLayout.LayoutParams(MATCH, WRAP))
        }
    }

    private fun shareRec(r: Rec) {
        val i = android.content.Intent(android.content.Intent.ACTION_SEND)
        i.type = "audio/*"
        i.putExtra(android.content.Intent.EXTRA_STREAM, r.uri)
        i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(android.content.Intent.createChooser(i, "Recording share karo"))
        } catch (e: Exception) {
            toast("Share nahi ho paya")
        }
    }

    private fun playRec(r: Rec) {
        val i = android.content.Intent(android.content.Intent.ACTION_VIEW)
        i.setDataAndType(r.uri, "audio/*")
        i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(i)
        } catch (e: Exception) {
            toast("Recording chalane wala app nahi mila")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshRecordings()
    }

    private fun refreshFlag() {
        val f = if (number.isEmpty()) null else Store.flagOf(this, number)
        if (f == null) {
            flagView.visibility = View.GONE
            return
        }
        val label = if (f.label.isEmpty()) "Block list" else "⚠ " + f.label
        flagView.text = label + (if (f.blocked) "  •  🚫 Blocked" else "  •  Sirf pehchaan")
        flagView.visibility = View.VISIBLE
    }

    private fun openEdit() {
        val i = android.content.Intent(this, ContactEditActivity::class.java)
        i.putExtra("number", number)
        i.putExtra("name", name ?: "")
        i.putExtra("edit", name != null)
        startActivityForResult(i, 62)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 62 && resultCode == RESULT_OK && data != null) {
            intent.putExtra("number", data.getStringExtra("number") ?: number)
            intent.putExtra("name", data.getStringExtra("name") ?: "")
            recreate()
        }
    }

    private fun refresh() {
        refreshFlag()
        updateStar()
        refreshTags()
        refreshRecordings()
        refreshNotes()
        refreshReminders()
        Thread {
            val h = Data.historyFor(this, number, 30)
            val sims = Data.simLabels(this)
            runOnUiThread { fillHistory(h, sims) }
        }.start()
    }

    private fun updateStar() {
        val fav = Store.isFav(this, number)
        starView.text = if (fav) "★" else "☆"
        starView.setTextColor(if (fav) GOLD else GRAY)
    }

    private fun toggleFav() {
        if (number.isEmpty()) return
        val now = Store.toggleFav(this, number, name ?: "")
        updateStar()
        toast(if (now) "Favorites mein add ho gaya" else "Favorites se hata diya")
    }

    private fun confirm(msg: String, onYes: () -> Unit) {
        AlertDialog.Builder(this, dialogTheme())
            .setMessage(msg)
            .setPositiveButton("Haan") { _, _ -> onYes() }
            .setNegativeButton("Nahi", null)
            .show()
    }

    private fun itemRow(line1: String, line2: String, onDelete: () -> Unit): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(4), 0, dp(4))

        val colv = LinearLayout(this)
        colv.orientation = LinearLayout.VERTICAL
        val t = TextView(this)
        t.text = line1
        t.textSize = 15f
        t.setTextColor(INK)
        val d = TextView(this)
        d.text = line2
        d.textSize = 12f
        d.setTextColor(GRAY)
        colv.addView(t)
        colv.addView(d)
        row.addView(colv, LinearLayout.LayoutParams(0, WRAP, 1f))

        val x = iconBtn("✕") { onDelete() }
        x.textSize = 16f
        row.addView(x, LinearLayout.LayoutParams(dp(40), dp(40)))
        return row
    }

    private fun refreshNotes() {
        notesBox.removeAllViews()
        val list = Store.notes(this, number)
        if (list.isEmpty()) {
            notesBox.addView(hint("Abhi koi note nahi"))
            return
        }
        for (n in list) {
            notesBox.addView(
                itemRow(n.text, Data.fmtTime(n.ts)) {
                    confirm("Ye note delete karu?") {
                        Store.deleteNote(this, number, n.ts)
                        refreshNotes()
                    }
                },
                LinearLayout.LayoutParams(MATCH, WRAP)
            )
        }
    }

    private fun refreshReminders() {
        remBox.removeAllViews()
        val key = Data.key10(number)
        val list = Store.reminders(this).filter { Data.key10(it.number) == key }
        remTitle.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        for (r in list) {
            remBox.addView(
                itemRow(r.msg, Data.fmtTime(r.at)) {
                    confirm("Ye reminder hata du?") {
                        Remind.delete(this, r)
                        refreshReminders()
                    }
                },
                LinearLayout.LayoutParams(MATCH, WRAP)
            )
        }
    }

    private fun updateHistTitle() {
        histTitle.text = "📞  Call history ($histCount)   " + (if (histOpen) "▴" else "▾")
        histBox.visibility = if (histOpen) View.VISIBLE else View.GONE
        histPreview.visibility = if (histOpen) View.GONE else View.VISIBLE
    }

    private fun histLine(e: CallEntry, sims: Map<String, String>): String {
        val dur = Data.fmtDur(e.duration)
        var text = Data.typeLabel(e.type) + " • " + Data.fmtTime(e.date)
        if (dur.isNotEmpty() && e.type != 3) text += " • $dur"
        val sim = Data.simName(sims, e.sim)
        if (sim.isNotEmpty()) text += " • $sim"
        return text
    }

    private fun fillHistory(list: List<CallEntry>, sims: Map<String, String>) {
        histBox.removeAllViews()
        histCount = list.size
        if (list.isEmpty()) {
            histTitle.text = "📞  Call history"
            histPreview.visibility = View.GONE
            histBox.visibility = View.VISIBLE
            histBox.addView(hint("Koi call history nahi"))
            return
        }
        histPreview.text = "Aakhri: " + histLine(list[0], sims)
        updateHistTitle()
        for (e in list) {
            val text = histLine(e, sims)
            val t = TextView(this)
            t.text = text
            t.textSize = 14f
            t.setTextColor(if (e.type == 3 || e.type == 5) RED else INK)
            t.setPadding(0, dp(6), 0, dp(6))
            histBox.addView(t, LinearLayout.LayoutParams(MATCH, WRAP))
        }
    }
}
