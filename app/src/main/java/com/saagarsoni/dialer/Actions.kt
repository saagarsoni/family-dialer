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

    // ---------- video / WhatsApp calls ----------

    fun videoMenu(act: Activity, number: String) {
        val opts = arrayOf(
            "🟢  WhatsApp video call",
            "🟢  WhatsApp voice call",
            "📹  Carrier video call (Jio / Airtel)"
        )
        AlertDialog.Builder(act, dialogTheme())
            .setTitle("Video / WhatsApp call")
            .setItems(opts) { _, i ->
                when (i) {
                    0 -> whatsappCall(act, number, true)
                    1 -> whatsappCall(act, number, false)
                    else -> carrierVideo(act, number)
                }
            }
            .show()
    }

    // Contact WhatsApp se synced ho to seedha call lagta hai, warna chat khulti hai.
    fun whatsappCall(act: Activity, number: String, video: Boolean) {
        val key = Data.key10(number)
        if (key.length < 7) {
            act.toast("Number sahi nahi hai")
            return
        }
        val kinds = arrayOf(
            arrayOf("com.whatsapp", if (video) "vnd.android.cursor.item/vnd.com.whatsapp.video.call" else "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"),
            arrayOf("com.whatsapp.w4b", if (video) "vnd.android.cursor.item/vnd.com.whatsapp.w4b.video.call" else "vnd.android.cursor.item/vnd.com.whatsapp.w4b.voip.call")
        )
        for (k in kinds) {
            var id = -1L
            try {
                act.contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(ContactsContract.Data._ID),
                    ContactsContract.Data.MIMETYPE + "=? AND " + ContactsContract.Data.DATA1 + " LIKE ?",
                    arrayOf(k[1], "%" + key + "@s.whatsapp.net"),
                    null
                )?.use {
                    if (it.moveToFirst()) id = it.getLong(0)
                }
            } catch (e: Exception) {
            }
            if (id >= 0L) {
                try {
                    val i = Intent(Intent.ACTION_VIEW)
                    i.setDataAndType(Uri.parse("content://com.android.contacts/data/$id"), k[1])
                    i.setPackage(k[0])
                    act.startActivity(i)
                    return
                } catch (e: Exception) {
                }
            }
        }
        act.toast("Ye contact WhatsApp se synced nahi hai, chat khol raha hu")
        whatsapp(act, number)
    }

    // Carrier ka asli video call. Phone aur SIM dono support karte hon tabhi video lagega.
    fun carrierVideo(act: Activity, number: String) {
        val n = Data.cleanNumber(number)
        if (n.isEmpty()) return
        if (act.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            act.requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 2)
            act.toast("Phone permission allow karo, phir dobara tap karo")
            return
        }
        try {
            val i = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", n, null))
            i.putExtra(android.telecom.TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, 3)
            act.startActivity(i)
            pendingNumber = n
            pendingAt = System.currentTimeMillis()
        } catch (e: Exception) {
            act.toast("Video call nahi lag paya")
        }
    }

    fun copy(act: Activity, number: String) {
        val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("number", number))
        act.toast("Copy ho gaya")
    }

    fun saveContact(act: Activity, number: String) {
        val i = Intent(act, ContactEditActivity::class.java)
        i.putExtra("number", number)
        act.startActivity(i)
    }

    fun editContact(act: Activity, number: String, name: String?) {
        val i = Intent(act, ContactEditActivity::class.java)
        i.putExtra("number", number)
        i.putExtra("name", name ?: "")
        i.putExtra("edit", name != null)
        act.startActivity(i)
    }

    // Recents / list mein lamba dabane par menu
    fun rowMenu(act: Activity, number: String, name: String?, onChange: () -> Unit) {
        val opts = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        opts.add("📞  Call")
        acts.add { call(act, number) }
        opts.add("💬  SMS")
        acts.add { sms(act, number) }
        if (name == null) {
            opts.add("➕  Contact save karo")
            acts.add { saveContact(act, number) }
        } else {
            opts.add("✎  Contact edit karo")
            acts.add { editContact(act, number, name) }
        }
        opts.add("📋  Number copy karo")
        acts.add { copy(act, number) }
        opts.add("🚫  Fraud / Block")
        acts.add { flagDialog(act, number, name) { onChange() } }
        AlertDialog.Builder(act, dialogTheme())
            .setTitle(name ?: number)
            .setItems(opts.toTypedArray()) { _, i -> acts[i]() }
            .show()
    }

    fun pickContact(act: Activity, req: Int) {
        try {
            act.startActivityForResult(
                Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
                req
            )
        } catch (e: Exception) {
            act.toast("Contact picker nahi khula")
        }
    }

    fun readPicked(act: Activity, data: Intent?): Fav? {
        val uri = data?.data ?: return null
        var out: Fav? = null
        try {
            act.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use {
                if (it.moveToFirst()) {
                    val n = it.getString(0)
                    if (n != null) out = Fav(n, it.getString(1) ?: "")
                }
            }
        } catch (e: Exception) {
        }
        return out
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

    fun flagDialog(act: Activity, number: String, name: String?, onDone: () -> Unit) {
        val n = Data.cleanNumber(number)
        if (Data.key10(n).length < 5) {
            act.toast("Is number ko flag nahi kar sakte")
            return
        }
        val cur = Store.flagOf(act, n)
        val opts = ArrayList<String>()
        opts.add("⚠ Fraud mark karo + Block karo")
        opts.add("⚠ Fraud mark karo (block nahi, sirf pehchaan)")
        opts.add("🚫 Sirf block karo")
        if (cur != null) opts.add("✅ Flag / block hatao")
        AlertDialog.Builder(act, dialogTheme())
            .setTitle(name ?: n)
            .setItems(opts.toTypedArray()) { _, i ->
                var wantsBlock = false
                when (i) {
                    0 -> {
                        Store.setFlag(act, n, "Fraud", true)
                        wantsBlock = true
                        act.toast("Fraud mark + block ho gaya")
                    }
                    1 -> {
                        Store.setFlag(act, n, "Fraud", false)
                        act.toast("Fraud mark ho gaya")
                    }
                    2 -> {
                        Store.setFlag(act, n, "", true)
                        wantsBlock = true
                        act.toast("Block ho gaya")
                    }
                    else -> {
                        Store.removeFlag(act, n)
                        act.toast("Flag hata diya")
                    }
                }
                onDone()
                if (!Screening.isHeld(act)) {
                    AlertDialog.Builder(act, dialogTheme())
                        .setTitle("Call blocking chalu karo")
                        .setMessage(
                            (if (wantsBlock) "Block tabhi kaam karega" else "Fraud ki pehchaan call aane par tabhi dikhegi") +
                                " jab Sampark ko 'Caller ID & spam app' bana do. Abhi karu?"
                        )
                        .setPositiveButton("Haan") { _, _ -> Screening.request(act) }
                        .setNegativeButton("Baad mein", null)
                        .show()
                }
            }
            .show()
    }

    fun postCallWanted(ctx: Context, number: String): Boolean {
        return when (Store.getInt(ctx, "postcall_mode", 0)) {
            1 -> Store.tagsOf(ctx, number).isNotEmpty()
            2 -> false
            else -> true
        }
    }

    fun showPostCall(act: Activity, number: String, onDone: () -> Unit) {
        if (!postCallWanted(act, number)) {
            onDone()
            return
        }
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

        if (name == null && Data.key10(number).length >= 5) {
            val sv = TextView(act)
            sv.text = "➕  Contact save karo"
            sv.setTextColor(Color.WHITE)
            sv.gravity = Gravity.CENTER
            sv.setPadding(0, act.dp(10), 0, act.dp(10))
            sv.background = roundBg(act, GREEN, 10)
            sv.setOnClickListener { saveContact(act, number) }
            val svlp = LinearLayout.LayoutParams(MATCH, WRAP)
            svlp.topMargin = act.dp(8)
            box.addView(sv, svlp)
        }

        if (Data.key10(number).length >= 5) {
            val fr = TextView(act)
            fr.text = "🚫  Fraud / Block"
            fr.setTextColor(Color.WHITE)
            fr.gravity = Gravity.CENTER
            fr.setPadding(0, act.dp(10), 0, act.dp(10))
            fr.background = roundBg(act, RED, 10)
            fr.setOnClickListener { flagDialog(act, number, name) {} }
            val flp = LinearLayout.LayoutParams(MATCH, WRAP)
            flp.topMargin = act.dp(8)
            box.addView(fr, flp)
        }

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
            "Kal shaam 5 baje",
            "Date aur time chuno"
        )
        AlertDialog.Builder(act, dialogTheme())
            .setTitle("Kab yaad dilau?")
            .setItems(labels) { _, i ->
                if (i == 5) {
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
                        3 -> {
                            cal.add(Calendar.DAY_OF_YEAR, 1)
                            cal.set(Calendar.HOUR_OF_DAY, 10)
                            cal.set(Calendar.MINUTE, 0)
                            cal.set(Calendar.SECOND, 0)
                        }
                        else -> {
                            cal.add(Calendar.DAY_OF_YEAR, 1)
                            cal.set(Calendar.HOUR_OF_DAY, 17)
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
