package com.saagarsoni.dialer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.telecom.Call

object CallNotifier {

    const val ID = 7001
    const val ACT_ANSWER = "com.saagarsoni.dialer.ANSWER"
    const val ACT_DECLINE = "com.saagarsoni.dialer.DECLINE"
    const val ACT_HANGUP = "com.saagarsoni.dialer.HANGUP"

    private const val CH_RING = "sampark_ring"
    private const val CH_ONGOING = "sampark_ongoing"
    private const val CH_MISSED = "sampark_missed"
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun nm(ctx: Context): NotificationManager =
        ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ensure(ctx: Context) {
        val m = nm(ctx)
        if (m.getNotificationChannel(CH_RING) == null) {
            val ch = NotificationChannel(CH_RING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(null, null)
            ch.enableVibration(false)
            m.createNotificationChannel(ch)
        }
        if (m.getNotificationChannel(CH_ONGOING) == null) {
            val ch = NotificationChannel(CH_ONGOING, "Ongoing call", NotificationManager.IMPORTANCE_LOW)
            m.createNotificationChannel(ch)
        }
        if (m.getNotificationChannel(CH_MISSED) == null) {
            val ch = NotificationChannel(CH_MISSED, "Missed calls", NotificationManager.IMPORTANCE_HIGH)
            m.createNotificationChannel(ch)
        }
    }

    private fun receiverAction(ctx: Context, label: String, action: String, code: Int): Notification.Action {
        val i = Intent(ctx, CallActionReceiver::class.java)
        i.action = action
        val pi = PendingIntent.getBroadcast(ctx, code, i, FLAGS)
        val icon = Icon.createWithResource(ctx, android.R.drawable.sym_action_call)
        return Notification.Action.Builder(icon, label, pi).build()
    }

    fun update(ctx: Context) {
        val t = CallManager.primary()
        val m = nm(ctx)
        if (t == null) {
            m.cancel(ID)
            return
        }
        ensure(ctx)

        val title = t.name ?: (if (t.number.isEmpty()) "Unknown number" else t.number)
        val openI = Intent(ctx, InCallActivity::class.java)
        openI.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val openPi = PendingIntent.getActivity(ctx, 1, openI, FLAGS)
        val state = t.call.state

        val b: Notification.Builder
        if (state == Call.STATE_RINGING) {
            b = Notification.Builder(ctx, CH_RING)
                .setSmallIcon(android.R.drawable.sym_action_call)
                .setContentTitle("Incoming call")
                .setContentText(title)
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setContentIntent(openPi)
                .setFullScreenIntent(openPi, true)
                .addAction(receiverAction(ctx, "Decline", ACT_DECLINE, 11))
                .addAction(receiverAction(ctx, "Answer", ACT_ANSWER, 12))
        } else {
            val text = when (state) {
                Call.STATE_ACTIVE -> "Call chal rahi hai"
                Call.STATE_HOLDING -> "Call hold pe hai"
                else -> "Calling..."
            }
            b = Notification.Builder(ctx, CH_ONGOING)
                .setSmallIcon(android.R.drawable.sym_action_call)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openPi)
                .addAction(receiverAction(ctx, "Hang up", ACT_HANGUP, 13))
            if (state == Call.STATE_ACTIVE && t.activeAt > 0L) {
                b.setUsesChronometer(true)
                b.setWhen(t.activeAt)
            }
        }
        m.notify(ID, b.build())
    }

    fun showMissed(ctx: Context, number: String, name: String?) {
        ensure(ctx)
        val key = Data.key10(number)
        val id = 20000 + (key.hashCode() and 0xffff)
        val title = name ?: (if (number.isEmpty()) "Unknown number" else number)

        val openI = Intent(ctx, DetailActivity::class.java)
        openI.putExtra("number", number)
        openI.putExtra("name", name ?: "")
        openI.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val openPi = PendingIntent.getActivity(ctx, 1000000 + id, openI, FLAGS)

        val b = Notification.Builder(ctx, CH_MISSED)
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setContentTitle("Missed call: $title")
            .setContentText(if (name != null) number + " • " + Data.fmtTime(System.currentTimeMillis()) else Data.fmtTime(System.currentTimeMillis()))
            .setAutoCancel(true)
            .setContentIntent(openPi)

        if (number.isNotEmpty()) {
            val callI = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", Data.cleanNumber(number), null))
            callI.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val callPi = PendingIntent.getActivity(ctx, 2000000 + id, callI, FLAGS)
            b.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(ctx, android.R.drawable.sym_action_call),
                    "Call back",
                    callPi
                ).build()
            )

            val waI = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + Data.waNumber(number)))
            waI.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val waPi = PendingIntent.getActivity(ctx, 3000000 + id, waI, FLAGS)
            b.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(ctx, android.R.drawable.sym_action_chat),
                    "WhatsApp",
                    waPi
                ).build()
            )
        }
        nm(ctx).notify(id, b.build())
    }
}
