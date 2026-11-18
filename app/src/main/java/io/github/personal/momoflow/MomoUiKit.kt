package io.github.personal.momoflow

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.EditText
import android.text.InputType

/** Native reusable UI primitives; all four pages share spacing, palette and controls. */
internal enum class MomoPage(val title: String, val subtitle: String, val navLabel: String) {
    STUDY("今日学习", "单词、进度与练习", "学习"),
    FLUID("流体云", "背词会话与通知控制", "流体云"),
    VERIFY("反馈核验", "只读对照与最近结果", "核验"),
    CONNECTIONS("数据连接", "开放 API 与 Cortex 授权", "连接")
}

internal class MomoUiKit(private val context: Context) {
    val ink = Color.parseColor("#18273D")
    val muted = Color.parseColor("#68798C")
    val accent = Color.parseColor("#3767CF")
    val background = Color.parseColor("#F5F7FB")
    val subtle = Color.parseColor("#EEF3FB")
    val border = Color.parseColor("#E0E7F1")
    val warning = Color.parseColor("#A8522A")
    val positive = Color.parseColor("#168163")

    fun dp(v: Int) = (context.resources.displayMetrics.density * v).toInt()

    fun gap(v: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(v))
    }

    fun text(value: String, sp: Float = 15f, color: Int = ink, bold: Boolean = false): TextView =
        TextView(context).apply {
            text = value
            textSize = sp
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = true
            setLineSpacing(dp(2).toFloat(), 1f)
        }

    fun rounded(color: Int, radius: Int = 18, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    fun card(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Color.WHITE, 22, border)
        setPadding(dp(18), dp(18), dp(18), dp(18))
    }

    fun button(title: String, filled: Boolean = true, action: () -> Unit): Button =
        Button(context).apply {
            text = title
            textSize = 14f
            isAllCaps = false
            setTextColor(if (filled) Color.WHITE else accent)
            background = rounded(if (filled) accent else subtle, 15)
            minHeight = dp(50)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            maxLines = 2
            setOnClickListener { action() }
        }

    fun row(vararg children: View): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        children.forEachIndexed { index, child ->
            if (index > 0) addView(View(context), LinearLayout.LayoutParams(dp(8), dp(1)))
            addView(child, LinearLayout.LayoutParams(0, dp(56), 1f))
        }
    }

    fun input(placeholder: String, password: Boolean = false): EditText =
        EditText(context).apply {
            hint = placeholder
            textSize = 14f
            setSingleLine(true)
            inputType = if (password)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            else InputType.TYPE_CLASS_TEXT
            background = rounded(subtle, 14, border)
            setTextColor(ink)
            setHintTextColor(muted)
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }

    fun status(message: String, positiveState: Boolean = false, warningState: Boolean = false): TextView =
        text(
            message, 13f,
            if (positiveState) positive else if (warningState) warning else muted,
            true
        ).apply {
            background = rounded(
                if (positiveState) Color.parseColor("#E8F7F0")
                else if (warningState) Color.parseColor("#FFF3EA")
                else subtle,
                12
            )
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

    fun bottomNav(selected: MomoPage, onSwitch: (MomoPage) -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.WHITE)
            MomoPage.entries.forEach { page ->
                val active = page == selected
                val item = text(page.navLabel, 13f, if (active) accent else muted, active).apply {
                    gravity = Gravity.CENTER
                    minHeight = dp(48)
                    background = rounded(if (active) subtle else Color.TRANSPARENT, 13)
                    setOnClickListener { onSwitch(page) }
                    isClickable = true
                    isFocusable = true
                    contentDescription = "切换到${page.title}"
                }
                addView(item, LinearLayout.LayoutParams(0, dp(48), 1f))
            }
        }
}
