package com.example.pixellauncher

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Маленькая иконка пункта меню: 0 = сетка, 1 = квадрат, 2 = домик. */
class MiniIcon(c: Context, private val kind: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val r = RectF()
    private val d = c.resources.displayMetrics.density

    override fun onDraw(cv: Canvas) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.8f * d
        p.color = 0xFFE9EAEE.toInt()
        val w = width.toFloat()
        val h = height.toFloat()
        when (kind) {
            0 -> {
                val cell = w / 3f
                val s = cell * 0.62f
                for (i in 0 until 3) {
                    for (j in 0 until 3) {
                        val cx = cell * (i + 0.5f)
                        val cy = h / 3f * (j + 0.5f)
                        r.set(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f)
                        cv.drawRoundRect(r, 1.5f * d, 1.5f * d, p)
                    }
                }
            }
            1 -> {
                r.set(w * 0.1f, h * 0.1f, w * 0.9f, h * 0.9f)
                cv.drawRoundRect(r, 4f * d, 4f * d, p)
            }
            else -> {
                path.reset()
                path.moveTo(w * 0.1f, h * 0.48f)
                path.lineTo(w * 0.5f, h * 0.12f)
                path.lineTo(w * 0.9f, h * 0.48f)
                path.moveTo(w * 0.22f, h * 0.42f)
                path.lineTo(w * 0.22f, h * 0.88f)
                path.lineTo(w * 0.78f, h * 0.88f)
                path.lineTo(w * 0.78f, h * 0.42f)
                cv.drawPath(path, p)
            }
        }
    }
}

/**
 * Меню кнопки "Home" в режиме редактирования: Сетка, Значки, Сделать главной.
 * Появляется над панелью кнопок, раскрываясь из места кнопки.
 */
class HomeMenu(
    a: Activity,
    onLayout: () -> Unit,
    onIcons: () -> Unit,
    onHome: () -> Unit,
    private val dismiss: () -> Unit
) : FrameLayout(a) {
    private val card = LinearLayout(a)

    init {
        setOnClickListener { dismiss() }
        card.orientation = LinearLayout.VERTICAL
        val g = GradientDrawable()
        g.setColor(0xEE30343B.toInt())
        g.cornerRadius = a.dp(26).toFloat()
        g.setStroke(a.dp(1), 0x33FFFFFF)
        card.background = g
        card.setPadding(a.dp(8), a.dp(8), a.dp(8), a.dp(8))
        card.addView(item(a, 0, L(a, "Сетка", "Layout"), onLayout))
        card.addView(item(a, 1, L(a, "Значки", "App Icons"), onIcons))
        card.addView(item(a, 2, L(a, "Сделать главной", "Set as home"), onHome))
        val dm = a.resources.displayMetrics
        // по видео меню центрируется над кнопкой Home (центр на 39% ширины), низ меню на 84% высоты
        val lp = LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        lp.rightMargin = (dm.widthPixels * 0.22f).toInt()
        lp.bottomMargin = (dm.heightPixels * 0.16f).toInt()
        addView(card, lp)
        val below = dm.heightPixels * 0.05f
        card.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            // меню раскрывается из центра кнопки: она чуть ниже нижнего края меню
            v.pivotX = v.width / 2f
            v.pivotY = v.height + below
        }
        // старт: размытая "пилюля" размером с кнопку (0.26 ширины, 0.5 высоты меню), текст проявляется позже
        card.alpha = 0.5f
        card.scaleX = 0.26f
        card.scaleY = 0.5f
        for (i in 0 until card.childCount) card.getChildAt(i).alpha = 0f
        post {
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(270)
                .setInterpolator(DecelerateInterpolator(1.6f)).start()
            for (i in 0 until card.childCount) {
                card.getChildAt(i).animate().alpha(1f).setStartDelay(110L + 25L * i).setDuration(150).start()
            }
        }
    }

    private fun item(a: Activity, kind: Int, label: String, click: () -> Unit): View {
        val row = LinearLayout(a)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(a.dp(14), a.dp(13), a.dp(22), a.dp(13))
        row.minimumWidth = a.dp(210)
        row.addView(MiniIcon(a, kind), LinearLayout.LayoutParams(a.dp(22), a.dp(22)))
        val t = TextView(a)
        t.text = label
        t.setTextColor(Color.WHITE)
        t.textSize = 17f
        t.setPadding(a.dp(16), 0, 0, 0)
        row.addView(t)
        row.setOnClickListener { click() }
        return row
    }

    fun close(end: () -> Unit) {
        // в видео меню исчезает меньше чем за 130 мс, схлопываясь к кнопке
        for (i in 0 until card.childCount) card.getChildAt(i).animate().alpha(0f).setStartDelay(0).setDuration(60).start()
        card.animate().alpha(0f).scaleX(0.26f).scaleY(0.5f).setDuration(120)
            .setInterpolator(AccelerateInterpolator(1.2f)).withEndAction(end).start()
    }
}
