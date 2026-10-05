package com.saagarsoni.dialer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Note(val text: String, val ts: Long)

data class Reminder(
    val id: Int,
    val at: Long,
    val number: String,
    val name: String,
    val msg: String
)

data class Fav(val number: String, val name: String)

object Store {

    private fun sp(ctx: Context) = ctx.getSharedPreferences("sampark", Context.MODE_PRIVATE)

    // ---------- notes ----------

    fun notes(ctx: Context, number: String): List<Note> {
        val raw = sp(ctx).getString("notes_" + Data.key10(number), null) ?: return emptyList()
        val out = ArrayList<Note>()
        try {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Note(o.getString("t"), o.getLong("ts")))
            }
        } catch (e: Exception) {
        }
        return out.sortedByDescending { it.ts }
    }

    private fun saveNotes(ctx: Context, number: String, list: List<Note>) {
        val a = JSONArray()
        for (n in list) {
            val o = JSONObject()
            o.put("t", n.text)
            o.put("ts", n.ts)
            a.put(o)
        }
        sp(ctx).edit().putString("notes_" + Data.key10(number), a.toString()).apply()
    }

    fun addNote(ctx: Context, number: String, text: String) {
        val l = notes(ctx, number).toMutableList()
        l.add(Note(text, System.currentTimeMillis()))
        saveNotes(ctx, number, l)
    }

    fun deleteNote(ctx: Context, number: String, ts: Long) {
        saveNotes(ctx, number, notes(ctx, number).filter { it.ts != ts })
    }

    // ---------- favorites ----------

    fun favs(ctx: Context): List<Fav> {
        val raw = sp(ctx).getString("favs", null) ?: return emptyList()
        val out = ArrayList<Fav>()
        try {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Fav(o.getString("number"), o.optString("name", "")))
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun isFav(ctx: Context, number: String): Boolean {
        val key = Data.key10(number)
        return favs(ctx).any { Data.key10(it.number) == key }
    }

    fun toggleFav(ctx: Context, number: String, name: String): Boolean {
        val key = Data.key10(number)
        val cur = favs(ctx).toMutableList()
        val idx = cur.indexOfFirst { Data.key10(it.number) == key }
        val nowFav: Boolean
        if (idx >= 0) {
            cur.removeAt(idx)
            nowFav = false
        } else {
            cur.add(Fav(number, name))
            nowFav = true
        }
        val a = JSONArray()
        for (f in cur) {
            val o = JSONObject()
            o.put("number", f.number)
            o.put("name", f.name)
            a.put(o)
        }
        sp(ctx).edit().putString("favs", a.toString()).apply()
        return nowFav
    }

    // ---------- reminders ----------

    fun reminders(ctx: Context): List<Reminder> {
        val raw = sp(ctx).getString("rem", null) ?: return emptyList()
        val out = ArrayList<Reminder>()
        try {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(
                    Reminder(
                        o.getInt("id"),
                        o.getLong("at"),
                        o.optString("number", ""),
                        o.optString("name", ""),
                        o.optString("msg", "")
                    )
                )
            }
        } catch (e: Exception) {
        }
        return out.sortedBy { it.at }
    }

    private fun saveReminders(ctx: Context, list: List<Reminder>) {
        val a = JSONArray()
        for (r in list) {
            val o = JSONObject()
            o.put("id", r.id)
            o.put("at", r.at)
            o.put("number", r.number)
            o.put("name", r.name)
            o.put("msg", r.msg)
            a.put(o)
        }
        sp(ctx).edit().putString("rem", a.toString()).apply()
    }

    fun addReminder(ctx: Context, at: Long, number: String, name: String, msg: String): Reminder {
        val prefs = sp(ctx)
        val id = prefs.getInt("remId", 1)
        prefs.edit().putInt("remId", id + 1).apply()
        val r = Reminder(id, at, number, name, msg)
        val list = reminders(ctx).toMutableList()
        list.add(r)
        saveReminders(ctx, list)
        return r
    }

    fun removeReminder(ctx: Context, id: Int) {
        saveReminders(ctx, reminders(ctx).filter { it.id != id })
    }
}
