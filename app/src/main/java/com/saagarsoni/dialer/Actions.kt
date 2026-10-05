package com.saagarsoni.dialer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.view.Gravity
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar

object Actions {

    private var pendingNumber: String? = null
    private var pendingAt: Long = 0L

    fun clearPending() {
        pendingNumber = null
    }

    fun call(act: Activity, number: String) {
        val n = Data.cleanNumber(number)
        if (n.isEmpty()) return
        if (act.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            act.requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 2)
            act.toast("Phone permission allow karo, phir dobara tap karo")
            return
        }
        try {
            act.startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", n, null)))
            pendingNumber = n
            pendingAt = System.currentTimeMillis()
        } catch (e: Exception) {
            act.toast("Call nahi lag paya")
        }
    }

    private fun safeStart(act: Activity, i: Intent) {
        try {
            act.startActivity(i)
        } catch (e: Exception) {
            act.toast("Ye app phone mein nahi mila")
        }
    }

    fun sms(act: Activity, number: String) {
        safeStart(act, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", Data.cleanNumber(number), null)))
    }

    fun whatsapp(act: Activity, number: String) {
        safeStart(act, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + Data.waNumber(number))))
    }

    fun copy(act: Activity, number: String) {
        val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("number", number))
        act.toast("Copy ho gaya")
    }

    fun saveContact(act: Activity, number: String) {
        val i = Intent(ContactsContract.Intents.Insert.ACTION)
        i.type = ContactsContract.RawContacts.CONTENT_TYPE
        i.putExtra(ContactsContract.Intents.Insert.PHONE, number)
        safeStart(act, i)
    }

    fun openDetail(ctx: Context, number: String, name: String?) {
        val i = Intent(ctx, DetailActivity::class.java)
        i.putExtra("number", number)
        i.putExtra("name", name ?: "")
        ctx.startActivity(i)
    }

    // ---------- post-call screen ----------

    fun checkPostCall(act: Activity) {
        val n = pendingNumber ?: return
        if (CallManager.hasCalls()) return
        val age = System.currentTimeMillis() - pendingAt
        if (age > 6L * 3600L * 1000L) {
            pendingNumber = null
            return
        }
        if (age < 2500L) return
        pendingNumber = null
        showPostCall(act, n) {}
    }

    fun showPostCall(act: Activity, number: String, onDone: () -> Unit) {
        val name = Data.lookupName(act, number)

        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(act.dp(20), act.dp(8), act.dp(20), act.dp(4))

        val et = EditText(act)
        et.themed("Is call ka note likho (optional)")
        et.minLines = 2
        et.gravity = Gravity.TOP
        box.addView(et, LinearLayout.LayoutParams(MATCH, WRAP))

        val wa = TextView(act)
        wa.text = "🟢  WhatsApp bhejo"
        wa.setTextColor(Color.WHITE)
        wa.gravity = Gravity.CENTER
        wa.setPadding(0, act.dp(10), 0, act.dp(10))
        wa.background = roundBg(act, GREEN, 10)
        wa.setOnClickListener { whatsapp(act, number) }
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = act.dp(12)
        box.addView(wa, lp)

        val dlg = AlertDialog.Builder(act, dialogTheme())
            .setTitle("Call khatam: " + (name ?: number))
            .setView(box)
            .setPositiveButton("Save note") { _, _ ->
                val t = et.text.toString().trim()
                if (t.isNotEmpty()) {
                    Store.addNote(act, number, t)
                    act.toast("Note save ho gaya")
                }
                onDone()
            }
            .setNeutralButton("Reminder") { _, _ ->
                val t = et.text.toString().trim()
                if (t.isNotEmpty()) Store.addNote(act, number, t)
                askReminder(act, number, name, t, onDone)
            }
            .setNegativeButton("Band karo") { _, _ -> onDone() }
            .setOnCancelListener { onDone() }
            .create()
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
    }

    fun quickNote(act: Activity, number: String) {
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(act.dp(20), act.dp(8), act.dp(20), act.dp(4))
        val et = EditText(act)
        et.themed("Note likho...")
        et.minLines = 2
        et.gravity = Gravity.TOP
        box.addView(et, LinearLayout.LayoutParams(MATCH, WRAP))
        val dlg = AlertDialog.Builder(act, dialogTheme())
            .setTitle("Call note")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val t = et.text.toString().trim()
                if (t.isNotEmpty()) {
                    Store.addNote(act, number, t)
                    act.toast("Note save ho gaya")
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
    }

    // ---------- reminders ----------

    fun askReminder(act: Activity, number: String, name: String?, msg: String) {
        askReminder(act, number, name, msg) {}
    }

    fun askReminder(act: Activity, number: String, name: String?, msg: String, onDone: () -> Unit) {
        val labels = arrayOf(
            "15 minute baad",
            "1 ghante baad",
            "Aaj shaam 6 baje",
            "Kal subah 10 baje",
            "Date aur time chuno"
        )
        AlertDialog.Builder(act, dialogTheme())
            .setTitle("Kab yaad dilau?")
            .setItems(labels) { _, i ->
                if (i == 4) {
                    pickCustom(act, number, name, msg, onDone)
                } else {
                    val cal = Calendar.getInstance()
                    when (i) {
                        0 -> cal.add(Calendar.MINUTE, 15)
                        1 -> cal.add(Calendar.HOUR_OF_DAY, 1)
                        2 -> {
                            cal.set(Calendar.HOUR_OF_DAY, 18)
                            cal.set(Calendar.MINUTE, 0)
                            cal.set(Calendar.SECOND, 0)
                            if (cal.timeInMillis <= System.currentTimeMillis()) {
                                cal.add(Calendar.DAY_OF_YEAR, 1)
                            }
                        }
                        else -> {
                            cal.add(Calendar.DAY_OF_YEAR, 1)
                            cal.set(Calendar.HOUR_OF_DAY, 10)
                            cal.set(Calendar.MINUTE, 0)
                            cal.set(Calendar.SECOND, 0)
                        }
                    }
                    setReminder(act, cal.timeInMillis, number, name, msg)
                    onDone()
                }
            }
            .setOnCancelListener { onDone() }
            .show()
    }

    private fun pickCustom(act: Activity, number: String, name: String?, msg: String, onDone: () -> Unit) {
        val now = Calendar.getInstance()
        val datePicker = DatePickerDialog(
            act,
            dialogTheme(),
            { _, y, m, d ->
                val timePicker = TimePickerDialog(
                    act,
                    dialogTheme(),
                    { _, h, mi ->
                        val c = Calendar.getInstance()
                        c.set(y, m, d, h, mi, 0)
                        c.set(Calendar.MILLISECOND, 0)
                        setReminder(act, c.timeInMillis, number, name, msg)
                        onDone()
                    },
                    10,
                    0,
                    false
                )
                timePicker.setOnCancelListener { onDone() }
                timePicker.show()
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.setOnCancelListener { onDone() }
        datePicker.show()
    }

    private fun setReminder(act: Activity, at: Long, number: String, name: String?, msg: String) {
        if (at <= System.currentTimeMillis()) {
            act.toast("Ye time nikal chuka hai")
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            act.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            act.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3)
        }
        Remind.create(act, at, number, name ?: "", if (msg.isEmpty()) "Call karna hai" else msg)
        act.toast("Reminder set: " + Data.fmtTime(at))
    }
}
