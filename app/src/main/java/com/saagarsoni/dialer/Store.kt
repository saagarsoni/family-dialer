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

class TagEntry(val number: String, val name: String, val tags: List<String>)

object Store {

    private fun sp(ctx: Context) = ctx.getSharedPreferences("sampark", Context.MODE_PRIVATE)

    // ---------- plain settings ----------

    fun getInt(ctx: Context, key: String, def: Int): Int = sp(ctx).getInt(key, def)

    fun putInt(ctx: Context, key: String, v: Int) {
        sp(ctx).edit().putInt(key, v).apply()
    }

    fun getBool(ctx: Context, key: String, def: Boolean): Boolean = sp(ctx).getBoolean(key, def)

    fun putBool(ctx: Context, key: String, v: Boolean) {
        sp(ctx).edit().putBoolean(key, v).apply()
    }

    fun getStr(ctx: Context, key: String, def: String): String = sp(ctx).getString(key, def) ?: def

    fun putStr(ctx: Context, key: String, v: String) {
        sp(ctx).edit().putString(key, v).apply()
    }

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

    // ---------- tags ----------

    fun tagList(ctx: Context): List<String> {
        val raw = sp(ctx).getString("taglist", null) ?: return listOf("Family", "Friends", "Client", "TAT-Adz")
        val out = ArrayList<String>()
        try {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) out.add(a.getString(i))
        } catch (e: Exception) {
        }
        return out
    }

    private fun saveTagList(ctx: Context, list: List<String>) {
        val a = JSONArray()
        for (t in list) a.put(t)
        sp(ctx).edit().putString("taglist", a.toString()).apply()
    }

    fun addTag(ctx: Context, tag: String): Boolean {
        val t = tag.trim()
        if (t.isEmpty()) return false
        val l = tagList(ctx).toMutableList()
        if (l.any { it.equals(t, true) }) return false
        l.add(t)
        saveTagList(ctx, l)
        return true
    }

    private fun loadTagMap(ctx: Context): LinkedHashMap<String, TagEntry> {
        val out = LinkedHashMap<String, TagEntry>()
        val raw = sp(ctx).getString("tagmap", null) ?: return out
        try {
            val o = JSONObject(raw)
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val e = o.getJSONObject(k)
                val ta = e.getJSONArray("tags")
                val tags = ArrayList<String>()
                for (i in 0 until ta.length()) tags.add(ta.getString(i))
                out[k] = TagEntry(e.optString("number", ""), e.optString("name", ""), tags)
            }
        } catch (e: Exception) {
        }
        return out
    }

    private fun saveTagMap(ctx: Context, m: Map<String, TagEntry>) {
        val o = JSONObject()
        for ((k, v) in m) {
            val e = JSONObject()
            e.put("number", v.number)
            e.put("name", v.name)
            val ta = JSONArray()
            for (t in v.tags) ta.put(t)
            e.put("tags", ta)
            o.put(k, e)
        }
        sp(ctx).edit().putString("tagmap", o.toString()).apply()
    }

    fun deleteTag(ctx: Context, tag: String) {
        saveTagList(ctx, tagList(ctx).filter { it != tag })
        val m = loadTagMap(ctx)
        val fixed = LinkedHashMap<String, TagEntry>()
        for ((k, v) in m) {
            val nt = v.tags.filter { it != tag }
            if (nt.isNotEmpty()) fixed[k] = TagEntry(v.number, v.name, nt)
        }
        saveTagMap(ctx, fixed)
    }

    fun tagsOf(ctx: Context, number: String): List<String> {
        val e = loadTagMap(ctx)[Data.key10(number)]
        return e?.tags ?: emptyList()
    }

    fun setTags(ctx: Context, number: String, name: String, tags: List<String>) {
        val key = Data.key10(number)
        if (key.isEmpty()) return
        val m = loadTagMap(ctx)
        if (tags.isEmpty()) m.remove(key) else m[key] = TagEntry(number, name, tags)
        saveTagMap(ctx, m)
    }

    fun withTag(ctx: Context, tag: String): List<Fav> {
        val out = ArrayList<Fav>()
        for (e in loadTagMap(ctx).values) {
            if (e.tags.contains(tag)) out.add(Fav(e.number, e.name))
        }
        return out
    }

    // ---------- speed dial ----------

    fun speed(ctx: Context, digit: String): Fav? {
        val raw = sp(ctx).getString("speed", null) ?: return null
        return try {
            val o = JSONObject(raw).optJSONObject(digit)
            if (o == null) null else Fav(o.getString("number"), o.optString("name", ""))
        } catch (e: Exception) {
            null
        }
    }

    private fun speedObj(ctx: Context): JSONObject {
        val raw = sp(ctx).getString("speed", null) ?: return JSONObject()
        return try {
            JSONObject(raw)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    fun setSpeed(ctx: Context, digit: String, number: String, name: String) {
        val o = speedObj(ctx)
        val e = JSONObject()
        e.put("number", number)
        e.put("name", name)
        o.put(digit, e)
        sp(ctx).edit().putString("speed", o.toString()).apply()
    }

    fun clearSpeed(ctx: Context, digit: String) {
        val o = speedObj(ctx)
        o.remove(digit)
        sp(ctx).edit().putString("speed", o.toString()).apply()
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

    // ---------- backup / restore ----------

    private val BACKUP_KEYS = arrayOf(
        "notes_", "favs", "tagmap", "taglist", "speed", "theme_mode", "accent", "bday_on"
    )

    private fun allowed(key: String): Boolean = BACKUP_KEYS.any { key.startsWith(it) }

    fun exportJson(ctx: Context): String {
        val data = JSONObject()
        for ((k, v) in sp(ctx).all) {
            if (v == null || !allowed(k)) continue
            data.put(k, v)
        }
        val root = JSONObject()
        root.put("app", "sampark")
        root.put("version", 1)
        root.put("exported", System.currentTimeMillis())
        root.put("data", data)
        return root.toString(2)
    }

    fun importJson(ctx: Context, text: String): Int {
        val root = JSONObject(text)
        if (root.optString("app") != "sampark") throw IllegalArgumentException("not a sampark backup")
        val data = root.getJSONObject("data")
        val ed = sp(ctx).edit()
        var n = 0
        val keys = data.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (!allowed(k)) continue
            val v = data.get(k)
            if (v is String) {
                ed.putString(k, v)
            } else if (v is Int) {
                ed.putInt(k, v)
            } else if (v is Long) {
                ed.putLong(k, v)
            } else if (v is Boolean) {
                ed.putBoolean(k, v)
            } else {
                continue
            }
            n++
        }
        ed.apply()
        return n
    }
}
