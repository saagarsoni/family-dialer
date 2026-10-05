package com.saagarsoni.dialer

import android.content.Context
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Contact(
    val name: String,
    val number: String,
    val digits: String,
    val t9: String,
    val words: List<String>
)

data class Birthday(
    val name: String,
    val number: String,
    val month: Int,
    val day: Int,
    val year: Int
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

    val MONTHS = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    private fun midnight(): Calendar {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c
    }

    private fun nextDate(b: Birthday): Calendar {
        val today = midnight()
        val t = Calendar.getInstance()
        t.set(today.get(Calendar.YEAR), b.month - 1, b.day, 0, 0, 0)
        t.set(Calendar.MILLISECOND, 0)
        if (t.before(today)) t.add(Calendar.YEAR, 1)
        return t
    }

    fun daysUntil(b: Birthday): Int {
        val t = nextDate(b)
        val today = midnight()
        return ((t.timeInMillis - today.timeInMillis + 3600000L) / 86400000L).toInt()
    }

    fun nextAge(b: Birthday): Int {
        if (b.year <= 1900) return 0
        return nextDate(b).get(Calendar.YEAR) - b.year
    }

    fun loadBirthdays(ctx: Context): List<Birthday> {
        val numbers = HashMap<Long, String>()
        try {
            ctx.contentResolver.query(
                Phone.CONTENT_URI,
                arrayOf(Phone.CONTACT_ID, Phone.NUMBER),
                null,
                null,
                null
            )?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val n = it.getString(1) ?: continue
                    if (!numbers.containsKey(id)) numbers[id] = n
                }
            }
        } catch (e: Exception) {
        }

        val out = ArrayList<Birthday>()
        val seen = HashSet<String>()
        try {
            val sel = ContactsContract.Data.MIMETYPE + "=? AND " + Event.TYPE + "=" + Event.TYPE_BIRTHDAY
            ctx.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data.CONTACT_ID,
                    ContactsContract.Data.DISPLAY_NAME,
                    Event.START_DATE
                ),
                sel,
                arrayOf(Event.CONTENT_ITEM_TYPE),
                null
            )?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val name = it.getString(1) ?: continue
                    val raw = it.getString(2) ?: continue
                    val parts = raw.trim().split("-").filter { p -> p.isNotEmpty() }
                    var y = 0
                    var m = 0
                    var d = 0
                    if (parts.size >= 3) {
                        y = parts[0].toIntOrNull() ?: 0
                        m = parts[1].toIntOrNull() ?: 0
                        d = parts[2].take(2).toIntOrNull() ?: 0
                    } else if (parts.size == 2) {
                        m = parts[0].toIntOrNull() ?: 0
                        d = parts[1].take(2).toIntOrNull() ?: 0
                    }
                    if (m < 1 || m > 12 || d < 1 || d > 31) continue
                    if (!seen.add(name + "|" + m + "|" + d)) continue
                    out.add(Birthday(name, numbers[id] ?: "", m, d, y))
                }
            }
        } catch (e: Exception) {
        }
        return out.sortedBy { daysUntil(it) }
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
