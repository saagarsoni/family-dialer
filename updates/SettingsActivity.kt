package com.saagarsoni.dialer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : Activity() {

    companion object {
        const val REQ_PICK = 88
        const val REQ_BACKUP = 91
        const val REQ_RESTORE = 92
    }

    private lateinit var col: LinearLayout
    private var stamp = 0
    private var pendingDigit = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initTheme(this)
        applySystemBars(this, BG)
        stamp = themeStamp(this)

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
        title.text = "Settings"
        title.textSize = 20f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setTextColor(INK)
        top.addView(title, LinearLayout.LayoutParams(WRAP, WRAP))
        root.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))

        val scroll = ScrollView(this)
        col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(8), 0, dp(8), dp(32))
        scroll.addView(col, LinearLayout.LayoutParams(MATCH, WRAP))
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        setContentView(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (themeStamp(this) != stamp) recreate()
    }

    // ---------- helpers ----------

    private fun section(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 15f
        t.typeface = Typeface.DEFAULT_BOLD
        t.setTextColor(GREEN)
        t.setPadding(dp(12), dp(22), dp(12), dp(6))
        return t
    }

    private fun row(title: String, sub: String?, onClick: (() -> Unit)?): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.VERTICAL
        r.setPadding(dp(12), dp(10), dp(12), dp(10))
        val t = TextView(this)
        t.text = title
        t.textSize = 16f
        t.setTextColor(INK)
        r.addView(t)
        if (sub != null) {
            val s = TextView(this)
            s.text = sub
            s.textSize = 13f
            s.setTextColor(GRAY)
            r.addView(s)
        }
        if (onClick != null) {
            val sl = StateListDrawable()
            sl.addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(PRESS))
            sl.addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
            r.background = sl
            r.isClickable = true
            r.setOnClickListener { onClick() }
        }
        return r
    }

    private fun add(v: View) {
        col.addView(v, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    private fun confirm(msg: String, onYes: () -> Unit) {
        AlertDialog.Builder(this, dialogTheme())
            .setMessage(msg)
            .setPositiveButton("Haan") { _, _ -> onYes() }
            .setNegativeButton("Nahi", null)
            .show()
    }

    // ---------- screen ----------

    private fun render() {
        col.removeAllViews()

        // theme
        add(section("🎨  Theme"))
        val modeChips = Chips(this, listOf("Auto", "Light", "Dark")) { i ->
            Store.putInt(this, "theme_mode", i)
            bumpTheme(this)
            recreate()
        }
        modeChips.select(Store.getInt(this, "theme_mode", 0))
        add(modeChips.view)

        val accentRow = LinearLayout(this)
        accentRow.orientation = LinearLayout.HORIZONTAL
        accentRow.setPadding(dp(12), dp(8), dp(12), dp(4))
        val cur = Store.getInt(this, "accent", 0)
        for (i in ACCENTS.indices) {
            val dot = TextView(this)
            dot.gravity = Gravity.CENTER
            dot.textSize = 18f
            dot.setTextColor(Color.WHITE)
            dot.text = if (i == cur) "✓" else ""
            dot.background = circleBg(Color.parseColor(ACCENTS[i]))
            dot.setOnClickListener {
                Store.putInt(this, "accent", i)
                bumpTheme(this)
                recreate()
            }
            val lp = LinearLayout.LayoutParams(dp(40), dp(40))
            lp.rightMargin = dp(12)
            accentRow.addView(dot, lp)
        }
        add(accentRow)
        val accName = TextView(this)
        accName.text = "Rang: " + ACCENT_NAMES[if (cur in ACCENTS.indices) cur else 0]
        accName.textSize = 13f
        accName.setTextColor(GRAY)
        accName.setPadding(dp(12), 0, dp(12), 0)
        add(accName)

        // default dialer
        add(section("📞  Dialer"))
        val isDef = DefaultDialer.isDefault(this)
        add(
            row(
                "Default dialer",
                if (isDef) "Haan, Sampark default dialer hai" else "Abhi nahi. Tap karke Sampark ko default banao"
            ) {
                if (!isDef) DefaultDialer.request(this)
            }
        )

        val pcNames = arrayOf("Hamesha", "Sirf tag wale contacts ke liye", "Kabhi nahi")
        val pc = Store.getInt(this, "postcall_mode", 0).coerceIn(0, 2)
        add(
            row(
                "Call ke baad note popup: " + pcNames[pc],
                "Call khatam hone par note / reminder wala popup. Tap karke badlo"
            ) {
                Store.putInt(this, "postcall_mode", (pc + 1) % 3)
                render()
            }
        )

        // fraud / block
        add(section("🚫  Fraud aur Block"))
        val held = Screening.isHeld(this)
        add(
            row(
                "Call blocking: " + (if (held) "ON" else "OFF"),
                if (held) "Chalu hai. Block kiye number ki call apne aap reject hogi, fraud number par alert aayega"
                else "Band hai. Tap karke Sampark ko 'Caller ID & spam app' banao"
            ) {
                if (!held) Screening.request(this)
            }
        )
        val flagged = Store.flags(this).values.sortedByDescending { it.ts }
        for (f in flagged) {
            val nm = Data.lookupName(this, f.number)
            val tag = (if (f.label.isNotEmpty()) "⚠ " + f.label else "") +
                (if (f.blocked) (if (f.label.isNotEmpty()) " • " else "") + "🚫 Blocked" else " • Sirf pehchaan")
            add(row(if (nm != null) nm + "  (" + f.number + ")" else f.number, tag) {
                Actions.flagDialog(this, f.number, nm) { render() }
            })
        }
        add(row("＋ Number add karo", "Koi number haath se fraud / block list mein daalo") { addFlagDialog() })
        add(row("Tip: Recents mein kisi call ko lamba dabao, seedha Fraud / Block ka option aayega", null, null))

        // birthdays
        add(section("🎂  Birthdays"))
        val on = Birthdays.enabled(this)
        add(
            row(
                "Birthday reminders: " + (if (on) "ON" else "OFF"),
                "Contacts ke birthday par roz subah 9 baje notification. Tap karke badlo"
            ) {
                Store.putBool(this, "bday_on", !on)
                Birthdays.schedule(this)
                render()
            }
        )
        add(row("Aane wale birthdays dekho", "Abhi check karo (aaj ka ho to notification bhi aayega)") { showUpcoming() })

        // speed dial
        add(section("⚡  Speed dial"))
        add(row("Dialer mein 1 se 9 key ko lamba dabao (jab number type na kiya ho)", null, null))
        for (d in 1..9) {
            val ds = d.toString()
            val f = Store.speed(this, ds)
            val sub = if (f == null) "Khali. Tap karke contact chuno" else (if (f.name.isEmpty()) f.number else f.name + "  •  " + f.number)
            add(
                row("Key $ds", sub) {
                    if (f == null) {
                        pendingDigit = ds
                        Actions.pickContact(this, REQ_PICK)
                    } else {
                        AlertDialog.Builder(this, dialogTheme())
                            .setTitle("Key $ds")
                            .setItems(arrayOf("Dusra contact chuno", "Hata do")) { _, w ->
                                if (w == 0) {
                                    pendingDigit = ds
                                    Actions.pickContact(this, REQ_PICK)
                                } else {
                                    Store.clearSpeed(this, ds)
                                    render()
                                }
                            }
                            .show()
                    }
                }
            )
        }

        // tags
        add(section("🏷  Tags"))
        for (t in Store.tagList(this)) {
            val n = Store.withTag(this, t).size
            add(
                row(t, "$n contact") {
                    confirm("Tag \"$t\" delete karu? Contacts se bhi hat jayega.") {
                        Store.deleteTag(this, t)
                        render()
                    }
                }
            )
        }
        add(row("＋ Naya tag", "Contact ka detail khol ke tag lagao") { newTagDialog() })

        // backup
        add(section("💾  Backup"))
        add(row("Backup banao", "Notes, tags, favorites, speed dial aur theme ek file mein") {
            val name = "sampark-backup-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".json"
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "application/json"
            i.putExtra(Intent.EXTRA_TITLE, name)
            try {
                startActivityForResult(i, REQ_BACKUP)
            } catch (e: Exception) {
                toast("File picker nahi khula")
            }
        })
        add(row("Restore karo", "Purani backup file chuno") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "*/*"
            try {
                startActivityForResult(i, REQ_RESTORE)
            } catch (e: Exception) {
                toast("File picker nahi khula")
            }
        })

        add(section("ℹ️  About"))
        add(row("Sampark v2.0", "Saagar ka apna dialer", null))
    }

    // ---------- actions ----------

    private fun addFlagDialog() {
        val et = EditText(this)
        et.themed("Number")
        et.setSingleLine()
        et.inputType = android.text.InputType.TYPE_CLASS_PHONE
        val box = LinearLayout(this)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(et, LinearLayout.LayoutParams(MATCH, WRAP))
        AlertDialog.Builder(this, dialogTheme())
            .setTitle("Number add karo")
            .setView(box)
            .setPositiveButton("Aage") { _, _ ->
                val n = Data.cleanNumber(et.text.toString())
                Actions.flagDialog(this, n, null) { render() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun newTagDialog() {
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
                if (!Store.addTag(this, et.text.toString())) toast("Tag khali ya pehle se hai")
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showUpcoming() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            toast("Pehle Contacts permission allow karo")
            return
        }
        Thread {
            val all = Data.loadBirthdays(this)
            try {
                Birthdays.check(this, true)
            } catch (e: Exception) {
            }
            runOnUiThread {
                val msg = if (all.isEmpty()) {
                    "Contacts mein koi birthday nahi mila. Contact mein birthday date bhari honi chahiye."
                } else {
                    all.take(8).joinToString("\n") {
                        val d = Data.daysUntil(it)
                        val whenTxt = if (d == 0) "AAJ 🎉" else if (d == 1) "kal" else "$d din baad"
                        "🎂 " + it.name + " — " + it.day + " " + Data.MONTHS[it.month - 1] + " (" + whenTxt + ")"
                    }
                }
                AlertDialog.Builder(this, dialogTheme())
                    .setTitle("Aane wale birthdays")
                    .setMessage(msg)
                    .setPositiveButton("Theek hai", null)
                    .show()
            }
        }.start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DefaultDialer.REQ || requestCode == Screening.REQ) {
            render()
            return
        }
        if (resultCode != RESULT_OK) return

        if (requestCode == REQ_PICK) {
            val f = Actions.readPicked(this, data)
            if (f == null) {
                toast("Contact nahi mila")
            } else if (pendingDigit.isNotEmpty()) {
                Store.setSpeed(this, pendingDigit, f.number, f.name)
                toast("Key $pendingDigit: " + (if (f.name.isEmpty()) f.number else f.name))
                pendingDigit = ""
                render()
            }
        } else if (requestCode == REQ_BACKUP) {
            val uri = data?.data ?: return
            try {
                contentResolver.openOutputStream(uri)?.use {
                    it.write(Store.exportJson(this).toByteArray(Charsets.UTF_8))
                }
                toast("Backup save ho gaya")
            } catch (e: Exception) {
                toast("Backup nahi ho paya")
            }
        } else if (requestCode == REQ_RESTORE) {
            val uri = data?.data ?: return
            try {
                val text = contentResolver.openInputStream(uri)?.use {
                    String(it.readBytes(), Charsets.UTF_8)
                } ?: ""
                val n = Store.importJson(this, text)
                bumpTheme(this)
                Birthdays.schedule(this)
                toast("Restore ho gaya ($n items)")
                recreate()
            } catch (e: Exception) {
                toast("Ye Sampark ki backup file nahi lagti")
            }
        }
    }
}
