package com.saagarsoni.dialer

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

private val CALL_BG: Int = Color.parseColor("#0E1A14")
private val CALL_BTN: Int = Color.parseColor("#24342C")
private val CALL_MUTED: Int = Color.parseColor("#A9B8B0")
private val CALL_RED: Int = Color.parseColor("#E5484D")
private val CALL_GREEN: Int = Color.parseColor("#2FA36B")

private class Ctrl(val box: LinearLayout, val icon: TextView) {
    fun setOn(on: Boolean) {
        icon.background = circleBg(if (on) Color.WHITE else CALL_BTN)
        icon.setTextColor(if (on) CALL_BG else Color.WHITE)
    }
}

class InCallActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var wake: PowerManager.WakeLock? = null
    private var ending = false
    private var keypadOpen = false
    private var accountDialog: AlertDialog? = null
    private var chosenFor: Call? = null

    private lateinit var stateView: TextView
    private lateinit var avatar: TextView
    private lateinit var nameView: TextView
    private lateinit var numView: TextView
    private lateinit var gridBox: LinearLayout
    private lateinit var keypadBox: LinearLayout
    private lateinit var dtmfView: TextView
    private lateinit var incomingRow: LinearLayout
    private lateinit var endBtn: TextView
    private lateinit var muteCtrl: Ctrl
    private lateinit var spkCtrl: Ctrl
    private lateinit var holdCtrl: Ctrl
    private lateinit var swapCtrl: Ctrl
    private lateinit var mergeCtrl: Ctrl
    private lateinit var partBox: LinearLayout

    private val listenerRef: () -> Unit = { render() }

    private val ticker = object : Runnable {
        override fun run() {
            updateTimer()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setBackgroundDrawable(ColorDrawable(CALL_BG))
        window.statusBarColor = CALL_BG
        window.navigationBarColor = CALL_BG
        window.decorView.systemUiVisibility = 0

        buildUi()
        CallManager.listener = listenerRef
        handler.post(ticker)
        render()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ending = false
        render()
    }

    override fun onDestroy() {
        if (CallManager.listener === listenerRef) CallManager.listener = null
        handler.removeCallbacksAndMessages(null)
        releaseWake()
        accountDialog?.dismiss()
        super.onDestroy()
    }

    // ---------- UI ----------

    private fun ctrl(emoji: String, label: String, onClick: () -> Unit): Ctrl {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(0, dp(4), 0, dp(4))
        box.setOnClickListener { onClick() }

        val icon = TextView(this)
        icon.text = emoji
        icon.textSize = 22f
        icon.gravity = Gravity.CENTER
        box.addView(icon, LinearLayout.LayoutParams(dp(54), dp(54)))

        val l = TextView(this)
        l.text = label
        l.textSize = 12f
        l.setTextColor(CALL_MUTED)
        l.gravity = Gravity.CENTER
        l.setPadding(0, dp(4), 0, 0)
        box.addView(l, LinearLayout.LayoutParams(WRAP, WRAP))

        val c = Ctrl(box, icon)
        c.setOn(false)
        return c
    }

    private fun bigBtn(emoji: String, label: String, color: Int, onClick: () -> Unit): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setOnClickListener { onClick() }

        val icon = TextView(this)
        icon.text = emoji
        icon.textSize = 28f
        icon.setTextColor(Color.WHITE)
        icon.gravity = Gravity.CENTER
        icon.background = circleBg(color)
        box.addView(icon, LinearLayout.LayoutParams(dp(76), dp(76)))

        val l = TextView(this)
        l.text = label
        l.textSize = 13f
        l.setTextColor(CALL_MUTED)
        l.gravity = Gravity.CENTER
        l.setPadding(0, dp(8), 0, 0)
        box.addView(l, LinearLayout.LayoutParams(WRAP, WRAP))
        return box
    }

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setBackgroundColor(CALL_BG)
        root.setPadding(dp(24), dp(20), dp(24), dp(8))

        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.VERTICAL
        bottom.setPadding(dp(24), dp(4), dp(24), dp(20))

        stateView = TextView(this)
        stateView.textSize = 15f
        stateView.setTextColor(CALL_MUTED)
        stateView.gravity = Gravity.CENTER
        root.addView(stateView, LinearLayout.LayoutParams(MATCH, WRAP))

        avatar = TextView(this)
        avatar.gravity = Gravity.CENTER
        avatar.setTextColor(Color.WHITE)
        avatar.textSize = 40f
        avatar.typeface = Typeface.DEFAULT_BOLD
        val alp = LinearLayout.LayoutParams(dp(110), dp(110))
        alp.topMargin = dp(16)
        root.addView(avatar, alp)

        nameView = TextView(this)
        nameView.textSize = 28f
        nameView.typeface = Typeface.DEFAULT_BOLD
        nameView.setTextColor(Color.WHITE)
        nameView.gravity = Gravity.CENTER
        nameView.setPadding(0, dp(16), 0, 0)
        root.addView(nameView, LinearLayout.LayoutParams(MATCH, WRAP))

        numView = TextView(this)
        numView.textSize = 16f
        numView.setTextColor(CALL_MUTED)
        numView.gravity = Gravity.CENTER
        root.addView(numView, LinearLayout.LayoutParams(MATCH, WRAP))

        partBox = LinearLayout(this)
        partBox.orientation = LinearLayout.VERTICAL
        partBox.visibility = View.GONE
        val plp = LinearLayout.LayoutParams(MATCH, WRAP)
        plp.topMargin = dp(10)
        root.addView(partBox, plp)

        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        // controls grid
        gridBox = LinearLayout(this)
        gridBox.orientation = LinearLayout.VERTICAL

        muteCtrl = ctrl("🎤", "Mute") {
            val a = CallManager.audio
            CallManager.setMuted(!(a != null && a.isMuted))
        }
        val keyCtrl = ctrl("⌨", "Keypad") {
            keypadOpen = true
            render()
        }
        spkCtrl = ctrl("🔊", "Speaker") {
            val a = CallManager.audio
            CallManager.setSpeaker(!(a != null && a.route == CallAudioState.ROUTE_SPEAKER))
        }
        holdCtrl = ctrl("⏸", "Hold") {
            val t = CallManager.primary()
            if (t != null) {
                if (t.call.state == Call.STATE_HOLDING) t.call.unhold() else t.call.hold()
            }
        }
        val noteCtrl = ctrl("📝", "Note") {
            val t = CallManager.primary()
            if (t != null && t.number.isNotEmpty()) Actions.quickNote(this, t.number)
        }
        swapCtrl = ctrl("🔄", "Swap") {
            val t = CallManager.primary()
            if (t != null) {
                val o = CallManager.otherHolding(t)
                if (o != null) o.call.unhold()
            }
        }

        val addCtrl = ctrl("➕", "Add call") {
            val i = android.content.Intent(this, MainActivity::class.java)
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(i)
        }
        mergeCtrl = ctrl("🔀", "Merge") {
            val t = CallManager.primary()
            if (t != null) {
                val o = CallManager.otherHolding(t)
                val canMerge = (t.call.details.callCapabilities and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0
                try {
                    if (canMerge) t.call.mergeConference()
                    else if (o != null) t.call.conference(o.call)
                } catch (e: Exception) {
                }
            }
        }

        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL
        row1.addView(muteCtrl.box, LinearLayout.LayoutParams(0, WRAP, 1f))
        row1.addView(keyCtrl.box, LinearLayout.LayoutParams(0, WRAP, 1f))
        row1.addView(spkCtrl.box, LinearLayout.LayoutParams(0, WRAP, 1f))
        gridBox.addView(row1, LinearLayout.LayoutParams(MATCH, WRAP))

        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL
        row2.addView(holdCtrl.box, LinearLayout.LayoutParams(0, WRAP, 1f))
        row2.addView(noteCtrl.box, LinearLayout.LayoutParams(0, WRAP, 1f))
        row2.addView(swapCtrl.box, LinearLayout.LayoutParams(0, WRAP, 1f))
        gridBox.addView(row2, LinearLayout.LayoutParams(MATCH, WRAP))
        val row3 = LinearLayout(this)
        row3.orientation = LinearLayout.HORIZONTAL
        row3.addView(addCtrl.box, LinearLayout.LayoutParams(0, WRAP, 1f))
        row3.addView(mergeCtrl.box, LinearLayout.LayoutParams(0, WRAP, 1f))
        row3.addView(View(this), LinearLayout.LayoutParams(0, WRAP, 1f))
        gridBox.addView(row3, LinearLayout.LayoutParams(MATCH, WRAP))
        root.addView(gridBox, LinearLayout.LayoutParams(MATCH, WRAP))

        // keypad
        keypadBox = LinearLayout(this)
        keypadBox.orientation = LinearLayout.VERTICAL
        keypadBox.visibility = View.GONE

        dtmfView = TextView(this)
        dtmfView.textSize = 26f
        dtmfView.setTextColor(Color.WHITE)
        dtmfView.gravity = Gravity.CENTER
        dtmfView.setSingleLine()
        keypadBox.addView(dtmfView, LinearLayout.LayoutParams(MATCH, dp(44)))

        val keys = arrayOf("123", "456", "789", "*0#")
        for (k in keys) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER
            for (ch in k) {
                val kv = TextView(this)
                kv.text = ch.toString()
                kv.textSize = 26f
                kv.setTextColor(Color.WHITE)
                kv.gravity = Gravity.CENTER
                kv.background = circleBg(CALL_BTN)
                kv.setOnClickListener { sendDtmf(ch) }
                val klp = LinearLayout.LayoutParams(dp(64), dp(64))
                klp.setMargins(dp(14), dp(6), dp(14), dp(6))
                r.addView(kv, klp)
            }
            keypadBox.addView(r, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        val hide = TextView(this)
        hide.text = "Keypad chhupao"
        hide.textSize = 14f
        hide.setTextColor(CALL_MUTED)
        hide.gravity = Gravity.CENTER
        hide.setPadding(0, dp(10), 0, dp(6))
        hide.setOnClickListener {
            keypadOpen = false
            render()
        }
        keypadBox.addView(hide, LinearLayout.LayoutParams(MATCH, WRAP))
        root.addView(keypadBox, LinearLayout.LayoutParams(MATCH, WRAP))

        // incoming
        incomingRow = LinearLayout(this)
        incomingRow.orientation = LinearLayout.HORIZONTAL
        incomingRow.gravity = Gravity.CENTER
        val decline = bigBtn("✕", "Decline", CALL_RED) {
            val t = CallManager.primary()
            if (t != null) {
                t.rejected = true
                t.call.reject(false, null)
            }
        }
        val answer = bigBtn("📞", "Answer", CALL_GREEN) {
            val t = CallManager.primary()
            if (t != null) t.call.answer(VideoProfile.STATE_AUDIO_ONLY)
        }
        incomingRow.addView(decline, LinearLayout.LayoutParams(0, WRAP, 1f))
        incomingRow.addView(answer, LinearLayout.LayoutParams(0, WRAP, 1f))
        val ilp = LinearLayout.LayoutParams(MATCH, WRAP)
        ilp.topMargin = dp(8)
        bottom.addView(incomingRow, ilp)

        // end call
        endBtn = TextView(this)
        endBtn.text = "✕   End call"
        endBtn.textSize = 17f
        endBtn.setTextColor(Color.WHITE)
        endBtn.typeface = Typeface.DEFAULT_BOLD
        endBtn.gravity = Gravity.CENTER
        endBtn.background = roundBg(this, CALL_RED, 32)
        endBtn.setOnClickListener {
            val t = CallManager.primary()
            if (t != null) t.call.disconnect()
        }
        val elp = LinearLayout.LayoutParams(dp(220), dp(60))
        elp.gravity = Gravity.CENTER_HORIZONTAL
        elp.topMargin = dp(14)
        bottom.addView(endBtn, elp)

        val sv = android.widget.ScrollView(this)
        sv.isFillViewport = true
        sv.setBackgroundColor(CALL_BG)
        sv.addView(root, android.view.ViewGroup.LayoutParams(MATCH, MATCH))
        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.VERTICAL
        outer.setBackgroundColor(CALL_BG)
        outer.addView(sv, LinearLayout.LayoutParams(MATCH, 0, 1f))
        outer.addView(bottom, LinearLayout.LayoutParams(MATCH, WRAP))
        setContentView(outer)
    }

    private fun fillParticipants(conf: Call) {
        partBox.removeAllViews()
        val kids = conf.children
        for (k in kids) {
            val tk = CallManager.trackedFor(k)
            val h = k.details.handle
            val num = tk?.number ?: (if (h != null) android.net.Uri.decode(h.schemeSpecificPart ?: "") else "")
            val nm = tk?.name ?: (if (num.isEmpty()) "Unknown" else num)
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(12), dp(6), dp(8), dp(6))
            row.background = roundBg(this, CALL_BTN, 14)

            val tv = TextView(this)
            tv.text = "👤  " + nm + (if (tk?.name != null && num.isNotEmpty()) "\n      " + num else "")
            tv.textSize = 15f
            tv.setTextColor(Color.WHITE)
            row.addView(tv, LinearLayout.LayoutParams(0, WRAP, 1f))

            val canSplit = (k.details.callCapabilities and Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE) != 0
            if (canSplit && kids.size > 2) {
                val sp = TextView(this)
                sp.text = "Alag"
                sp.textSize = 13f
                sp.setTextColor(CALL_MUTED)
                sp.setPadding(dp(10), dp(8), dp(10), dp(8))
                sp.setOnClickListener {
                    try {
                        k.splitFromConference()
                    } catch (e: Exception) {
                    }
                }
                row.addView(sp, LinearLayout.LayoutParams(WRAP, WRAP))
            }

            val x = TextView(this)
            x.text = "✕"
            x.textSize = 16f
            x.setTextColor(Color.WHITE)
            x.gravity = Gravity.CENTER
            x.background = circleBg(CALL_RED)
            x.setOnClickListener {
                try {
                    k.disconnect()
                } catch (e: Exception) {
                }
            }
            row.addView(x, LinearLayout.LayoutParams(dp(36), dp(36)))

            val lp = LinearLayout.LayoutParams(MATCH, WRAP)
            lp.topMargin = dp(6)
            partBox.addView(row, lp)
        }
    }

    // ---------- behaviour ----------

    private fun sendDtmf(c: Char) {
        val t = CallManager.primary() ?: return
        t.call.playDtmfTone(c)
        handler.postDelayed({ t.call.stopDtmfTone() }, 160L)
        dtmfView.text = dtmfView.text.toString() + c
    }

    private fun fmt(secs: Long): String {
        val h = secs / 3600
        val m = (secs % 3600) / 60
        val s = secs % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    private fun updateTimer() {
        val t = CallManager.primary() ?: return
        if (t.call.state == Call.STATE_ACTIVE) {
            val ct = t.call.details.connectTimeMillis
            val base = if (ct > 0L) ct else t.activeAt
            if (base > 0L) {
                val secs = (System.currentTimeMillis() - base) / 1000L
                stateView.text = fmt(if (secs < 0L) 0L else secs)
            }
        }
    }

    private fun render() {
        val t = CallManager.primary()
        if (t == null) {
            handleNoCalls()
            return
        }
        ending = false
        val call = t.call
        val state = call.state

        val kidCount = call.children.size
        val isConf = kidCount > 0
        val shown = if (isConf) "Conference call" else (t.name ?: (if (t.number.isEmpty()) "Unknown" else t.number))
        nameView.text = shown
        if (isConf) {
            fillParticipants(call)
            partBox.visibility = if (keypadOpen) View.GONE else View.VISIBLE
        } else {
            partBox.visibility = View.GONE
        }
        val fl = Store.flagOf(this, t.number)
        val flagTxt = if (fl == null) "" else (if (fl.label.isNotEmpty()) "⚠ " + fl.label.uppercase() else "🚫 Block list")
        val baseNum = if (t.name != null) t.number else ""
        numView.text = if (flagTxt.isEmpty()) baseNum else if (baseNum.isEmpty()) flagTxt else "$baseNum  •  $flagTxt"
        if (isConf) numView.text = "$kidCount log call mein"
        avatar.text = if (isConf) "👥" else (t.name?.trim()?.firstOrNull() ?: '#').uppercaseChar().toString()
        avatar.background = circleBg(avatarColor(shown))

        val ringing = state == Call.STATE_RINGING
        if (ringing) keypadOpen = false
        incomingRow.visibility = if (ringing) View.VISIBLE else View.GONE
        endBtn.visibility = if (ringing) View.GONE else View.VISIBLE
        keypadBox.visibility = if (!ringing && keypadOpen) View.VISIBLE else View.GONE
        gridBox.visibility = if (!ringing && !keypadOpen) View.VISIBLE else View.GONE

        val waiting = ringing && CallManager.calls.size > 1
        when (state) {
            Call.STATE_RINGING -> stateView.text = if (waiting) "Call waiting" else "Incoming call"
            Call.STATE_DIALING -> stateView.text = "Calling..."
            Call.STATE_CONNECTING -> stateView.text = "Connecting..."
            Call.STATE_SELECT_PHONE_ACCOUNT -> stateView.text = "SIM chuno"
            Call.STATE_HOLDING -> stateView.text = "On hold"
            Call.STATE_DISCONNECTING -> stateView.text = "Ending..."
            Call.STATE_DISCONNECTED -> stateView.text = "Call khatam"
            else -> updateTimer()
        }

        val a = CallManager.audio
        val speaker = a != null && a.route == CallAudioState.ROUTE_SPEAKER
        muteCtrl.setOn(a != null && a.isMuted)
        spkCtrl.setOn(speaker)
        holdCtrl.setOn(state == Call.STATE_HOLDING)
        val canHold = (call.details.callCapabilities and Call.Details.CAPABILITY_HOLD) != 0
        holdCtrl.box.alpha = if (canHold || state == Call.STATE_HOLDING) 1f else 0.35f
        swapCtrl.box.visibility = if (CallManager.otherHolding(t) != null) View.VISIBLE else View.INVISIBLE
        val canMergeNow = (call.details.callCapabilities and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0
        mergeCtrl.box.visibility = if (CallManager.otherHolding(t) != null || canMergeNow) View.VISIBLE else View.INVISIBLE

        if (state == Call.STATE_SELECT_PHONE_ACCOUNT) askAccount(call)
        updateProximity(state, speaker)
    }

    private fun askAccount(call: Call) {
        if (accountDialog != null) return
        if (call === chosenFor) return
        val tm = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val handles: List<PhoneAccountHandle> = try {
            tm.callCapablePhoneAccounts
        } catch (e: Exception) {
            emptyList()
        }
        if (handles.isEmpty()) {
            call.disconnect()
            return
        }
        val labels = ArrayList<String>()
        for (h in handles) {
            val acc = try {
                tm.getPhoneAccount(h)
            } catch (e: Exception) {
                null
            }
            val lb = acc?.label?.toString()
            labels.add(if (lb.isNullOrEmpty()) h.id else lb)
        }
        accountDialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Kis SIM se call karu?")
            .setItems(labels.toTypedArray()) { _, i ->
                accountDialog = null
                chosenFor = call
                call.phoneAccountSelected(handles[i], false)
            }
            .setOnCancelListener {
                accountDialog = null
                call.disconnect()
            }
            .show()
    }

    private fun updateProximity(state: Int, speaker: Boolean) {
        val want = (state == Call.STATE_ACTIVE || state == Call.STATE_DIALING ||
            state == Call.STATE_CONNECTING) && !speaker && !keypadOpen
        try {
            if (want) {
                if (wake == null) {
                    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                    if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                        wake = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "sampark:proximity")
                    }
                }
                val w = wake
                if (w != null && !w.isHeld) w.acquire()
            } else {
                releaseWake()
            }
        } catch (e: Exception) {
        }
    }

    private fun releaseWake() {
        try {
            val w = wake
            if (w != null && w.isHeld) w.release()
        } catch (e: Exception) {
        }
    }

    private fun handleNoCalls() {
        if (ending) return
        ending = true
        releaseWake()
        stateView.text = "Call khatam"
        val e = CallManager.takeEnded()
        Actions.clearPending()
        if (e != null && e.wasActive && e.number.isNotEmpty()) {
            Actions.showPostCall(this, e.number) {
                if (!CallManager.hasCalls()) finish()
            }
        } else {
            handler.postDelayed({
                if (!CallManager.hasCalls()) finish()
            }, 700L)
        }
    }
}
