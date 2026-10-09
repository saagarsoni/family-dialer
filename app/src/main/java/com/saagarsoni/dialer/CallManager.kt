package com.saagarsoni.dialer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile

class Tracked(val call: Call) {
    var number: String = ""
    var name: String? = null
    var wasRinging: Boolean = call.state == Call.STATE_RINGING
    var wasActive: Boolean = call.state == Call.STATE_ACTIVE
    var rejected: Boolean = false
    var activeAt: Long = if (call.state == Call.STATE_ACTIVE) System.currentTimeMillis() else 0L
    var callback: Call.Callback? = null
}

class Ended(val number: String, val name: String?, val wasActive: Boolean)

object CallManager {

    val calls = ArrayList<Tracked>()
    var service: InCallService? = null
    var audio: CallAudioState? = null
    var listener: (() -> Unit)? = null
    private var lastEnded: Ended? = null

    fun hasCalls(): Boolean = calls.isNotEmpty()

    fun primary(): Tracked? {
        var best: Tracked? = null
        var bestScore = -1
        for (t in calls) {
            val par = t.call.parent
            if (par != null && calls.any { it.call === par }) continue
            val s = when (t.call.state) {
                Call.STATE_RINGING -> 5
                Call.STATE_ACTIVE -> 4
                Call.STATE_DIALING -> 4
                Call.STATE_CONNECTING -> 4
                Call.STATE_SELECT_PHONE_ACCOUNT -> 4
                Call.STATE_HOLDING -> 2
                Call.STATE_DISCONNECTING -> 1
                else -> 0
            }
            if (s > bestScore) {
                best = t
                bestScore = s
            }
        }
        return best
    }

    fun otherHolding(t: Tracked): Tracked? =
        calls.firstOrNull { it !== t && it.call.parent == null && it.call.state == Call.STATE_HOLDING }

    fun trackedFor(c: Call): Tracked? = calls.firstOrNull { it.call === c }

    fun takeEnded(): Ended? {
        val e = lastEnded
        lastEnded = null
        return e
    }

    fun changed() {
        val svc = service
        if (svc != null) CallNotifier.update(svc)
        listener?.invoke()
    }

    fun onCallAdded(svc: InCallService, call: Call) {
        val t = Tracked(call)
        val h = call.details.handle
        t.number = if (h != null) Uri.decode(h.schemeSpecificPart ?: "") else ""
        var nm: String? = Data.lookupName(svc, t.number)
        if (nm == null) {
            val d = call.details.callerDisplayName
            if (!d.isNullOrEmpty()) nm = d
        }
        t.name = nm

        val cb = object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) {
                if (state == Call.STATE_RINGING) t.wasRinging = true
                if (state == Call.STATE_ACTIVE) {
                    t.wasActive = true
                    if (t.activeAt == 0L) t.activeAt = System.currentTimeMillis()
                }
                changed()
            }

            override fun onDetailsChanged(c: Call, details: Call.Details) {
                changed()
            }

            override fun onChildrenChanged(c: Call, children: MutableList<Call>) {
                changed()
            }

            override fun onParentChanged(c: Call, parent: Call?) {
                changed()
            }
        }
        t.callback = cb
        call.registerCallback(cb)
        calls.add(t)
        changed()

        val i = Intent(svc, InCallActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            svc.startActivity(i)
        } catch (e: Exception) {
        }
    }

    fun onCallRemoved(svc: InCallService, call: Call) {
        val t = calls.firstOrNull { it.call == call } ?: return
        val cb = t.callback
        if (cb != null) call.unregisterCallback(cb)
        calls.remove(t)
        if (t.wasRinging && !t.wasActive && !t.rejected) {
            CallNotifier.showMissed(svc, t.number, t.name)
        }
        if (calls.isEmpty()) {
            lastEnded = Ended(t.number, t.name, t.wasActive)
        }
        changed()
    }

    fun setMuted(m: Boolean) {
        service?.setMuted(m)
    }

    fun setSpeaker(on: Boolean) {
        service?.setAudioRoute(
            if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE
        )
    }
}

class SamparkCallService : InCallService() {

    override fun onCreate() {
        super.onCreate()
        CallManager.service = this
    }

    override fun onDestroy() {
        CallManager.service = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.service = this
        CallManager.onCallAdded(this, call)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.onCallRemoved(this, call)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallManager.audio = audioState
        CallManager.changed()
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        if (CallManager.hasCalls()) {
            val i = Intent(this, InCallActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(i)
            } catch (e: Exception) {
            }
        }
    }
}

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val t = CallManager.primary() ?: return
        val a = intent.action
        if (a == CallNotifier.ACT_ANSWER) {
            t.call.answer(VideoProfile.STATE_AUDIO_ONLY)
        } else if (a == CallNotifier.ACT_DECLINE) {
            t.rejected = true
            t.call.reject(false, null)
        } else if (a == CallNotifier.ACT_HANGUP) {
            t.call.disconnect()
        }
    }
}
