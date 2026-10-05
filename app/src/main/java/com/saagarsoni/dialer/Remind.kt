package com.saagarsoni.dialer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri

object Remind {

    private const val CHANNEL = "sampark_reminders"
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun pending(ctx: Context, r: Reminder): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
        i.putExtra("id", r.id)
        i.putExtra("number", r.number)
        i.putExtra("name", r.name)
        i.putExtra("msg", r.msg)
        return PendingIntent.getBroadcast(ctx, r.id, i, FLAGS)
    }

    private fun schedule(ctx: Context, r: Reminder) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at, pending(ctx, r))
    }

    fun create(ctx: Context, at: Long, number: String, name: String, msg: String): Reminder {
        val r = Store.addReminder(ctx, at, number, name, msg)
        schedule(ctx, r)
        return r
    }

    fun delete(ctx: Context, r: Reminder) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx, r))
        Store.removeReminder(ctx, r.id)
    }

    fun rescheduleAll(ctx: Context) {
        val now = System.currentTimeMillis()
        for (r in Store.reminders(ctx)) {
            if (r.at > now) schedule(ctx, r) else fire(ctx, r)
        }
    }

    fun fire(ctx: Context, r: Reminder) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val open = Intent(ctx, DetailActivity::class.java)
        open.putExtra("number", r.number)
        open.putExtra("name", r.name)
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openPi = PendingIntent.getActivity(ctx, r.id, open, FLAGS)

        val title = if (r.name.isNotEmpty()) r.name else r.number
        val b = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("Callback: $title")
            .setContentText(r.msg)
            .setContentIntent(openPi)
            .setAutoCancel(true)

        if (r.number.isNotEmpty()) {
            val callI = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", Data.cleanNumber(r.number), null))
            callI.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val callPi = PendingIntent.getActivity(ctx, 100000 + r.id, callI, FLAGS)
            val icon = Icon.createWithResource(ctx, android.R.drawable.sym_action_call)
            b.addAction(Notification.Action.Builder(icon, "Call karo", callPi).build())
        }

        nm.notify(r.id, b.build())
        Store.removeReminder(ctx, r.id)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val r = Reminder(
            intent.getIntExtra("id", 0),
            System.currentTimeMillis(),
            intent.getStringExtra("number") ?: "",
            intent.getStringExtra("name") ?: "",
            intent.getStringExtra("msg") ?: ""
        )
        Remind.fire(context, r)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Remind.rescheduleAll(context)
        }
    }
}
