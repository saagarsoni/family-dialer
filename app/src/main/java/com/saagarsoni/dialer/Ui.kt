package com.saagarsoni.dialer

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

val ACCENTS = arrayOf("#1B8A5A", "#1A73E8", "#8E44AD", "#E8710A", "#D93025", "#12857E")
val ACCENT_NAMES = arrayOf("Green", "Blue", "Purple", "Orange", "Red", "Teal")

var DARK = false
var GREEN: Int = Color.parseColor("#1B8A5A")
var BG: Int = Color.WHITE
var BAR: Int = Color.parseColor("#F3F4F6")
var PRESS: Int = Color.parseColor("#E3E5E8")
var INK: Int = Color.parseColor("#202124")
var GRAY: Int = Color.parseColor("#70757A")
var RED: Int = Color.parseColor("#D93025")
val GOLD: Int = Color.parseColor("#F9AB00")

private val AVATAR_COLORS = intArrayOf(
    Color.parseColor("#1B8A5A"),
    Color.parseColor("#1A73E8"),
    Color.parseColor("#E8710A"),
    Color.parseColor("#9334E6"),
    Color.parseColor("#D93025"),
    Color.parseColor("#12857E"),
    Color.parseColor("#B06000")
)

fun lighten(c: Int, f: Float): Int {
    val r = (Color.red(c) + (255 - Color.red(c)) * f).toInt()
    val g = (Color.green(c) + (255 - Color.green(c)) * f).toInt()
    val b = (Color.blue(c) + (255 - Color.blue(c)) * f).toInt()
    return Color.rgb(r, g, b)
}

fun initTheme(ctx: Context) {
    val prefs = ctx.getSharedPreferences("sampark", Context.MODE_PRIVATE)
    val mode = prefs.getInt("theme_mode", 0)
    val sys = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    DARK = if (mode == 1) false else if (mode == 2) true else sys

    var idx = prefs.getInt("accent", 0)
    if (idx < 0 || idx >= ACCENTS.size) idx = 0
    val base = Color.parseColor(ACCENTS[idx])

    if (DARK) {
        GREEN = lighten(base, 0.18f)
        BG = Color.parseColor("#121212")
        BAR = Color.parseColor("#1E1E1E")
        PRESS = Color.parseColor("#2C2C2C")
        INK = Color.parseColor("#ECECEC")
        GRAY = Color.parseColor("#9AA0A6")
        RED = Color.parseColor("#F28B82")
    } else {
        GREEN = base
        BG = Color.WHITE
        BAR = Color.parseColor("#F3F4F6")
        PRESS = Color.parseColor("#E3E5E8")
        INK = Color.parseColor("#202124")
        GRAY = Color.parseColor("#70757A")
        RED = Color.parseColor("#D93025")
    }
}

fun themeStamp(ctx: Context): Int =
    ctx.getSharedPreferences("sampark", Context.MODE_PRIVATE).getInt("theme_stamp", 0)

fun bumpTheme(ctx: Context) {
    val p = ctx.getSharedPreferences("sampark", Context.MODE_PRIVATE)
    p.edit().putInt("theme_stamp", p.getInt("theme_stamp", 0) + 1).apply()
}

fun applySystemBars(act: Activity, navColor: Int) {
    act.window.setBackgroundDrawable(ColorDrawable(BG))
    act.window.statusBarColor = BG
    act.window.navigationBarColor = navColor
    var flags = 0
    if (!DARK) {
        flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }
    act.window.decorView.systemUiVisibility = flags
}

fun dialogTheme(): Int =
    if (DARK) android.R.style.Theme_Material_Dialog_Alert
    else android.R.style.Theme_Material_Light_Dialog_Alert

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun Context.toast(msg: String) {
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

fun avatarColor(key: String): Int =
    AVATAR_COLORS[(key.hashCode() and 0x7fffffff) % AVATAR_COLORS.size]

fun roundBg(ctx: Context, color: Int, radiusDp: Int): GradientDrawable {
    val g = GradientDrawable()
    g.cornerRadius = ctx.dp(radiusDp).toFloat()
    g.setColor(color)
    return g
}

fun circleBg(color: Int): GradientDrawable {
    val g = GradientDrawable()
    g.shape = GradientDrawable.OVAL
    g.setColor(color)
    return g
}

fun pressBg(): StateListDrawable {
    val sl = StateListDrawable()
    sl.addState(intArrayOf(android.R.attr.state_pressed), circleBg(PRESS))
    sl.addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
    return sl
}

fun EditText.themed(hintText: String) {
    hint = hintText
    setTextColor(INK)
    setHintTextColor(GRAY)
}

fun EditText.onChange(cb: () -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            cb()
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    })
}

data class Row(
    val title: String,
    val sub: String,
    val number: String,
    val name: String?,
    val subColor: Int = GRAY
)

class Chips(private val ctx: Context, labels: List<String>, private val onSelect: (Int) -> Unit) {

    val view = HorizontalScrollView(ctx)
    private val inner = LinearLayout(ctx)
    private val items = ArrayList<TextView>()
    var selected = 0
        private set

    init {
        view.isHorizontalScrollBarEnabled = false
        inner.orientation = LinearLayout.HORIZONTAL
        inner.setPadding(ctx.dp(12), ctx.dp(6), ctx.dp(12), ctx.dp(6))
        view.addView(inner)
        for (i in labels.indices) {
            val t = TextView(ctx)
            t.text = labels[i]
            t.textSize = 13f
            t.gravity = Gravity.CENTER
            t.setPadding(ctx.dp(14), ctx.dp(7), ctx.dp(14), ctx.dp(7))
            t.setOnClickListener {
                select(i)
                onSelect(i)
            }
            val lp = LinearLayout.LayoutParams(WRAP, WRAP)
            lp.rightMargin = ctx.dp(8)
            inner.addView(t, lp)
            items.add(t)
        }
        paint()
    }

    fun select(i: Int) {
        selected = i
        paint()
    }

    fun setLabel(i: Int, text: String) {
        if (i >= 0 && i < items.size) items[i].text = text
    }

    private fun paint() {
        for (i in items.indices) {
            if (i == selected) {
                items[i].background = roundBg(ctx, GREEN, 18)
                items[i].setTextColor(Color.WHITE)
            } else {
                items[i].background = roundBg(ctx, PRESS, 18)
                items[i].setTextColor(INK)
            }
        }
    }
}

class RowAdapter(private val ctx: Context, private val onCall: (Row) -> Unit) : BaseAdapter() {

    private var rows: List<Row> = emptyList()

    fun set(r: List<Row>) {
        rows = r
        notifyDataSetChanged()
    }

    override fun getCount(): Int = rows.size

    override fun getItem(position: Int): Row = rows[position]

    override fun getItemId(position: Int): Long = position.toLong()

    private class Holder(ctx: Context) {
        val root = LinearLayout(ctx)
        val avatar = TextView(ctx)
        val title = TextView(ctx)
        val sub = TextView(ctx)
        val call = TextView(ctx)

        init {
            root.orientation = LinearLayout.HORIZONTAL
            root.gravity = Gravity.CENTER_VERTICAL
            root.setPadding(ctx.dp(16), ctx.dp(8), ctx.dp(8), ctx.dp(8))

            avatar.gravity = Gravity.CENTER
            avatar.setTextColor(Color.WHITE)
            avatar.textSize = 18f
            avatar.typeface = Typeface.DEFAULT_BOLD
            root.addView(avatar, LinearLayout.LayoutParams(ctx.dp(44), ctx.dp(44)))

            val col = LinearLayout(ctx)
            col.orientation = LinearLayout.VERTICAL
            title.textSize = 16f
            title.setTextColor(INK)
            title.setSingleLine()
            title.ellipsize = TextUtils.TruncateAt.END
            sub.textSize = 13f
            sub.setSingleLine()
            sub.ellipsize = TextUtils.TruncateAt.END
            col.addView(title)
            col.addView(sub)
            val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
            lp.leftMargin = ctx.dp(14)
            root.addView(col, lp)

            call.text = "📞"
            call.textSize = 20f
            call.gravity = Gravity.CENTER
            root.addView(call, LinearLayout.LayoutParams(ctx.dp(48), ctx.dp(48)))
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val h: Holder
        val v: View
        if (convertView == null) {
            h = Holder(ctx)
            v = h.root
            v.tag = h
        } else {
            v = convertView
            h = convertView.tag as Holder
        }
        val row = rows[position]
        h.title.text = row.title
        h.sub.text = row.sub
        h.sub.setTextColor(row.subColor)

        h.avatar.background = circleBg(avatarColor(row.title))
        val ch = row.name?.trim()?.firstOrNull() ?: '#'
        h.avatar.text = ch.uppercaseChar().toString()

        h.call.visibility = if (row.number.isEmpty()) View.GONE else View.VISIBLE
        h.call.setOnClickListener { onCall(row) }
        return v
    }
}
