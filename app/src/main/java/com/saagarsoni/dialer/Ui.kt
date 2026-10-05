package com.saagarsoni.dialer

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

val GREEN: Int = Color.parseColor("#1B8A5A")
val INK: Int = Color.parseColor("#202124")
val GRAY: Int = Color.parseColor("#70757A")
val RED: Int = Color.parseColor("#D93025")

private val AVATAR_COLORS = intArrayOf(
    Color.parseColor("#1B8A5A"),
    Color.parseColor("#1A73E8"),
    Color.parseColor("#E8710A"),
    Color.parseColor("#9334E6"),
    Color.parseColor("#D93025"),
    Color.parseColor("#12857E"),
    Color.parseColor("#B06000")
)

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

data class Row(
    val title: String,
    val sub: String,
    val number: String,
    val name: String?,
    val subColor: Int = GRAY
)

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

        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(AVATAR_COLORS[(row.title.hashCode() and 0x7fffffff) % AVATAR_COLORS.size])
        h.avatar.background = bg
        val ch = row.name?.trim()?.firstOrNull() ?: '#'
        h.avatar.text = ch.uppercaseChar().toString()

        h.call.visibility = if (row.number.isEmpty()) View.GONE else View.VISIBLE
        h.call.setOnClickListener { onCall(row) }
        return v
    }
}
