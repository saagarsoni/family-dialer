package com.saagarsoni.dialer

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService

// "Caller ID & spam app" role: Samsung Phone default rehte hue bhi Sampark incoming call dekh/rok sakta hai.
object Screening {
    const val REQ = 78

    fun isHeld(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return try {
            (ctx.getSystemService(Context.ROLE_SERVICE) as RoleManager)
                .isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        } catch (e: Exception) {
            false
        }
    }

    fun request(act: Activity) {
        if (Build.VERSION.SDK_INT < 29) {
            act.toast("Is Android version par block ke liye Sampark ko default dialer banana padega")
            return
        }
        try {
            val rm = act.getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                act.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQ)
            } else {
                act.toast("Ye phone call screening support nahi karta")
            }
        } catch (e: Exception) {
            act.toast("Role request nahi khula")
        }
    }
}

object FlagAlert {
    private const val CH_FRAUD = "sampark_fraud"
    private const val CH_BLOCKED = "sampark_blocked"

    fun show(ctx: Context, number: String, f: Flag, blocked: Boolean) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CH_FRAUD) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_FRAUD, "Fraud number alert", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        if (nm.getNotificationChannel(CH_BLOCKED) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_BLOCKED, "Blocked calls", NotificationManager.IMPORTANCE_LOW)
            )
        }

        val id = 60000 + (Data.key10(number).hashCode() and 0x7fffffff) % 10000
        val name = Data.lookupName(ctx, number)
        val who = if (name != null) "$name ($number)" else number

        val open = Intent(ctx, DetailActivity::class.java)
        open.putExtra("number", number)
        open.putExtra("name", name ?: "")
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            ctx,
            id,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val label = if (f.label.isEmpty()) "Block list" else f.label
        val b = if (blocked) {
            Notification.Builder(ctx, CH_BLOCKED)
                .setContentTitle("🚫 Blocked call: $who")
                .setContentText("Sampark ne reject kar di ($label)")
        } else {
            Notification.Builder(ctx, CH_FRAUD)
                .setContentTitle("⚠ $label number call kar raha hai")
                .setContentText("$who. Tumne ise $label mark kiya hai, call mat uthana")
        }
        b.setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pi)
            .setAutoCancel(true)
        nm.notify(id, b.build())
    }
}

class SamparkScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val builder = CallResponse.Builder()
        try {
            val incoming = Build.VERSION.SDK_INT < 29 ||
                callDetails.callDirection == Call.Details.DIRECTION_INCOMING
            val num = callDetails.handle?.schemeSpecificPart ?: ""
            if (incoming && num.isNotEmpty()) {
                val f = Store.flagOf(this, num)
                if (f != null) {
                    if (f.blocked) {
                        builder.setDisallowCall(true)
                        builder.setRejectCall(true)
                        builder.setSkipNotification(true)
                    }
                    FlagAlert.show(this, num, f, f.blocked)
                }
            }
        } catch (e: Exception) {
        }
        respondToCall(callDetails, builder.build())
    }
}
