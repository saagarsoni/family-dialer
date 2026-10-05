package com.saagarsoni.dialer

import android.content.Context
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Contact(
    val name: String,
    val number: String,
    val digits: String,
    val t9: String,
    val words: List<String>
)

data class CallEntry(
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val duration: Long,
    val count: Int
)

object Data {
    private const val T9MAP = "22233344455566677778889999"

    fun t9(s: String): String {
        val sb = StringBuilder()
        for (ch in s.lowercase()) {
            if (ch in 'a'..'z') sb.append(T9MAP[ch - 'a'])
            else if (ch in '0'..'9') sb.append(ch)
        }
        return sb.toString()
    }

    fun digitsOf(n: String): String = n.filter { it.isDigit() }

    fun key10(n: String): String {
        val d = digitsOf(n)
        return if (d.length > 10) d.substring(d.length - 10) else d
    }

    fun cleanNumber(n: String): String =
        n.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

    fun waNumber(n: String): String {
        var d = digitsOf(n)
        if (d.length == 10) d = "91$d"
        else if (d.length == 11 && d.startsWith("0")) d = "91" + d.substring(1)
        return d
    }

    fun loadContacts(ctx: Context): List<Contact> {
        val out = ArrayList<Contact>()
        val seen = HashSet<String>()
        val c = try {
            ctx.contentResolver.query(
                Phone.CONTENT_URI,
                arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER),
                null,
                null,
                Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
            )
        } catch (e: Exception) {
            null
        }
        if (c == null) return out
        c.use {
            val ni = it.getColumnIndex(Phone.DISPLAY_NAME)
            val pi = it.getColumnIndex(Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(ni) ?: continue
                val num = it.getString(pi) ?: continue
                val d = digitsOf(num)
                if (d.isEmpty()) continue
                if (!seen.add(name + "|" + key10(num))) continue
                val words = name.split(" ").map { w -> t9(w) }.filter { w -> w.isNotEmpty() }
                out.add(Contact(name, num, d, t9(name), words))
            }
        }
        return out
    }

    fun loadRecents(ctx: Context, contacts: List<Contact>, limit: Int = 300): List<CallEntry> {
        val byKey = HashMap<String, String>()
        for (ct in contacts) {
            val k = key10(ct.number)
            if (!byKey.containsKey(k)) byKey[k] = ct.name
        }
        val c = try {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
                ),
                null,
                null,
                CallLog.Calls.DATE + " DESC"
            )
        } catch (e: Exception) {
            null
        }
        if (c == null) return emptyList()
        val out = ArrayList<CallEntry>()
        c.use {
            var n = 0
            while (n < limit && it.moveToNext()) {
                n++
                val number = it.getString(0) ?: ""
                val cached = it.getString(1)
                val type = it.getInt(2)
                val date = it.getLong(3)
                val dur = it.getLong(4)
                val name = byKey[key10(number)] ?: (if (cached.isNullOrEmpty()) null else cached)
                if (out.isNotEmpty() && out[out.size - 1].number == number) {
                    val prev = out[out.size - 1]
                    out[out.size - 1] = prev.copy(count = prev.count + 1)
                } else {
                    out.add(CallEntry(number, name, type, date, dur, 1))
                }
            }
        }
        return out
    }

    fun match(all: List<Contact>, d: String): List<Contact> {
        if (d.isEmpty()) return emptyList()
        val scored = ArrayList<Pair<Int, Contact>>()
        for (c in all) {
            val s = when {
                c.words.any { it.startsWith(d) } || c.t9.startsWith(d) -> 0
                c.digits.startsWith(d) || key10(c.number).startsWith(d) -> 1
                c.t9.contains(d) || c.digits.contains(d) -> 2
                else -> -1
            }
            if (s >= 0) scored.add(Pair(s, c))
        }
        scored.sortBy { it.first }
        return scored.take(40).map { it.second }
    }

    fun lookupName(ctx: Context, number: String): String? {
        if (number.isEmpty()) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            ctx.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun historyFor(ctx: Context, number: String, limit: Int): List<CallEntry> {
        val key = key10(number)
        if (key.isEmpty()) return emptyList()
        val c = try {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
                ),
                null,
                null,
                CallLog.Calls.DATE + " DESC"
            )
        } catch (e: Exception) {
            null
        }
        if (c == null) return emptyList()
        val out = ArrayList<CallEntry>()
        c.use {
            var scanned = 0
            while (out.size < limit && scanned < 3000 && it.moveToNext()) {
                scanned++
                val n = it.getString(0) ?: ""
                if (key10(n) != key) continue
                out.add(CallEntry(n, null, it.getInt(1), it.getLong(2), it.getLong(3), 1))
            }
        }
        return out
    }

    fun typeLabel(type: Int): String = when (type) {
        1 -> "Incoming"
        2 -> "Outgoing"
        3 -> "Missed"
        4 -> "Voicemail"
        5 -> "Rejected"
        6 -> "Blocked"
        else -> "Call"
    }

    fun fmtTime(ms: Long): String {
        val now = System.currentTimeMillis()
        val day = SimpleDateFormat("yyyyMMdd", Locale.US)
        val d1 = day.format(Date(ms))
        val d0 = day.format(Date(now))
        val dy = day.format(Date(now - 86400000L))
        val t = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms))
        return when (d1) {
            d0 -> "Aaj, $t"
            dy -> "Kal, $t"
            else -> SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(ms))
        }
    }

    fun fmtDur(s: Long): String {
        if (s <= 0L) return ""
        if (s < 60L) return "${s}s"
        return "${s / 60}m ${s % 60}s"
    }
}
