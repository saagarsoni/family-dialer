package com.saagarsoni.dialer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.os.Build
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Locale

data class Rec(val uri: Uri, val who: String, val ts: Long, val durMs: Long)

// Samsung Phone app ki banayi hui call recordings ko dhundta hai (sirf padhta hai, record nahi karta).
object Recordings {

    fun permission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasAccess(ctx: Context): Boolean =
        ctx.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED

    // "Call recording Naam_251006_110512.m4a"  ya  "Call recording +9174xxxxxx_251006_110512(1).m4a"
    private val TS = Regex("_(\\d{6})_(\\d{6})(?:\\s*\\(\\d+\\))?$")

    fun forContact(ctx: Context, number: String, name: String?): List<Rec> {
        val out = ArrayList<Rec>()
        val key = Data.key10(number)
        if (key.isEmpty()) return out
        val windows = callWindows(ctx, key)
        val fmt = SimpleDateFormat("yyMMddHHmmss", Locale.US)
        try {
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val cursor = ctx.contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.DATE_ADDED,
                    MediaStore.Audio.Media.DURATION
                ),
                MediaStore.Audio.Media.DISPLAY_NAME + " LIKE ? OR " +
                    MediaStore.Audio.Media.DATA + " LIKE ? OR " +
                    MediaStore.Audio.Media.DATA + " LIKE ? OR " +
                    MediaStore.Audio.Media.DISPLAY_NAME + " LIKE ?",
                arrayOf("%call%record%", "%/Recordings/Call/%", "%/Call/%", "%record%call%"),
                MediaStore.Audio.Media.DATE_ADDED + " DESC"
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val dn = it.getString(1) ?: continue
                    val added = it.getLong(2) * 1000L
                    val dur = it.getLong(3)

                    val dot = dn.lastIndexOf('.')
                    var stem = if (dot > 0) dn.substring(0, dot) else dn
                    stem = stem.trim()
                    if (stem.startsWith("Call recording", true)) {
                        stem = stem.substring("Call recording".length).trim()
                    }
                    stem = stem.trimStart('_', '-', ' ')

                    var ts = added
                    var who = stem
                    val m = TS.find(stem)
                    if (m != null) {
                        who = stem.substring(0, m.range.first).trim()
                        try {
                            val d = fmt.parse(m.groupValues[1] + m.groupValues[2])
                            if (d != null) ts = d.time
                        } catch (e: Exception) {
                        }
                    }

                    val wd = Data.digitsOf(who)
                    val byNum = wd.length >= 7 && (Data.key10(who) == key ||
                        (wd.length < 10 && key.endsWith(wd)))
                    val byName = name != null && who.isNotEmpty() && who.equals(name.trim(), true)
                    // naam/number se na mile (unsaved, private etc.) toh call log ke time se milao
                    var byTime = false
                    for (w in windows) {
                        if (ts >= w[0] - 90_000L && ts <= w[1] + 180_000L) {
                            byTime = true
                            break
                        }
                    }
                    if (byNum || byName || byTime) {
                        out.add(Rec(ContentUris.withAppendedId(uri, id), who, ts, dur))
                    }
                }
            }
        } catch (e: Exception) {
        }
        return out.sortedByDescending { it.ts }
    }

    // is number ke call log entries: [start, end] windows
    private fun callWindows(ctx: Context, key: String): List<LongArray> {
        val res = ArrayList<LongArray>()
        if (ctx.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return res
        try {
            val c = ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.DURATION),
                null, null, CallLog.Calls.DATE + " DESC LIMIT 1500"
            )
            c?.use {
                while (it.moveToNext()) {
                    val n = it.getString(0) ?: continue
                    if (Data.key10(n) != key) continue
                    val st = it.getLong(1)
                    val du = it.getLong(2) * 1000L
                    res.add(longArrayOf(st, st + du))
                }
            }
        } catch (e: Exception) {
        }
        return res
    }
}
