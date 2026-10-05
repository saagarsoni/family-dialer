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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Birthdays {

    private const val CHANNEL = "sampark_birthdays"
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun enabled(ctx: Context): Boolean = Store.getBool(ctx, "bday_on", true)

    private fun pending(ctx: Context): PendingIntent {
        val i = Intent(ctx, BirthdayReceiver::class.java)
        return PendingIntent.getBroadcast(ctx, 7001, i, FLAGS)
    }

    // Roz subah 9 baje ka alarm. Har baar fire hone ke baad agla set hota hai.
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx))
        if (!enabled(ctx)) return
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 9)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, pending(ctx))
    }

    // Aaj ke birthdays ki notification. force=false par ek din mein ek hi baar.
    fun check(ctx: Context, force: Boolean): Int {
        if (!force && !enabled(ctx)) return 0
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        if (!force && Store.getStr(ctx, "bday_last", "") == today) return 0

        val list = Data.loadBirthdays(ctx).filter { Data.daysUntil(it) == 0 }
        if (list.isEmpty()) return 0
        Store.putStr(ctx, "bday_last", today)

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Birthdays", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        for (b in list) {
            val id = 50000 + ((b.name + b.day + b.month).hashCode() and 0x7fffffff) % 10000
            val age = Data.nextAge(b)
            val text = if (age > 0) "$age saal ke ho gaye. Wish kar do!" else "Wish kar do!"

            val open = Intent(ctx, DetailActivity::class.java)
            open.putExtra("number", b.number)
            open.putExtra("name", b.name)
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val openPi = PendingIntent.getActivity(ctx, id, open, FLAGS)

            val nb = Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.star_on)
                .setContentTitle("🎂 " + b.name + " ka birthday aaj hai")
                .setContentText(text)
                .setContentIntent(openPi)
                .setAutoCancel(true)

            if (b.number.isNotEmpty()) {
                val callI = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", Data.cleanNumber(b.number), null))
                callI.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val callPi = PendingIntent.getActivity(ctx, id + 20000, callI, FLAGS)
                nb.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(ctx, android.R.drawable.sym_action_call),
                        "Call karo",
                        callPi
                    ).build()
                )

                val waI = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://wa.me/" + Data.waNumber(b.number) + "?text=" + Uri.encode("Happy Birthday " + b.name + "! 🎂"))
                )
                waI.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val waPi = PendingIntent.getActivity(ctx, id + 40000, waI, FLAGS)
                nb.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(ctx, android.R.drawable.ic_menu_send),
                        "WhatsApp",
                        waPi
                    ).build()
                )
            }
            nm.notify(id, nb.build())
        }
        return list.size
    }
}

class BirthdayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Birthdays.check(context, false)
        } catch (e: Exception) {
        }
        Birthdays.schedule(context)
    }
}
