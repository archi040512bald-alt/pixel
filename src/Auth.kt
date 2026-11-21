package com.example.pixellauncher

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView

/** Строка точек PIN-кода: заполненная точка "выскакивает" при вводе. */
class PinDots(c: Context, private val n: Int, private val fill: Int, private val empty: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = c.resources.displayMetrics.density
    private val gap = 30f * d
    private var count = 0
    private var pop = 1f

    fun set(k: Int) {
        val grew = k > count
        count = k
        if (grew) {
            val va = ValueAnimator.ofFloat(1.9f, 1f)
            va.duration = 240
            va.interpolator = OvershootInterpolator(2f)
            va.addUpdateListener {
                pop = it.animatedValue as Float
                invalidate()
            }
            va.start()
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((n * gap).toInt(), (30f * d).toInt())
    }

    override fun onDraw(cv: Canvas) {
        val cy = height / 2f
        for (i in 0 until n) {
            val cx = gap / 2f + i * gap
            if (i < count) {
                p.style = Paint.Style.FILL
                p.color = fill
                cv.drawCircle(cx, cy, 6f * d * (if (i == count - 1) pop else 1f), p)
            } else {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1.6f * d
                p.color = empty
                cv.drawCircle(cx, cy, 5f * d, p)
            }
        }
    }
}

class AuthPanel(private val a: Activity, private val sp: LockPrefs,
                private val ok: () -> Unit, private val cancel: () -> Unit) : LinearLayout(a) {
    // PIN рисуется на весь экран в цветах обоев, ключ остаётся нижней панелью (тёмной, чтобы точки были видны)
    private val pal = LockTheme.pal(a, sp.type != 1)
    private val title = TextView(a)
    private var dots: PinDots? = null
    private var code = ""
    private var busy = false

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        isClickable = true
        title.setTextColor(pal.ink)
        title.gravity = Gravity.CENTER
        if (sp.type == 1) {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(pal.top, pal.bottom))
            pin()
        } else {
            val g = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(pal.top, pal.bottom))
            val r = a.dp(28).toFloat()
            g.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            background = g
            title.textSize = 18f
            addView(title, LinearLayout.LayoutParams(-1, -2))
            pattern()
            val back = TextView(a)
            back.text = L(a, "Отмена", "Cancel")
            back.setTextColor(pal.ink)
            back.textSize = 16f
            back.setPadding(a.dp(24), a.dp(14), a.dp(24), a.dp(4))
            back.setOnClickListener { cancel() }
            addView(back)
        }
    }

    // Экран PIN занимает всю высоту родителя
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val ph = (parent as? View)?.height ?: 0
        if (sp.type == 1 && ph > 0) {
            super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(ph, View.MeasureSpec.EXACTLY))
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    private fun shake() {
        val d = dots ?: return
        val s = a.dp(10).toFloat()
        d.animate().translationX(-s).setDuration(50).withEndAction {
            d.animate().translationX(s).setDuration(70).withEndAction {
                d.animate().translationX(0f).setDuration(50).start()
            }.start()
        }.start()
    }

    private fun squish(bg: GradientDrawable, down: Boolean, base: Int) {
        val r0 = a.dp(36).toFloat()
        val r1 = a.dp(20).toFloat()
        val va = ValueAnimator.ofFloat(if (down) 0f else 1f, if (down) 1f else 0f)
        va.duration = if (down) 90 else 170
        val ev = ArgbEvaluator()
        va.addUpdateListener {
            val t = it.animatedValue as Float
            bg.cornerRadius = r0 + (r1 - r0) * t
            bg.setColor(ev.evaluate(t, base, pal.pressed) as Int)
        }
        va.start()
    }

    private fun key(k: String): View {
        val t = TextView(a)
        t.text = k
        t.textSize = 28f
        t.gravity = Gravity.CENTER
        t.setTextColor(pal.ink)
        if (k.isEmpty()) return t
        val base = if (k == "⌫") (pal.pressed and 0x00FFFFFF) else pal.key
        val bg = GradientDrawable()
        bg.setColor(base)
        bg.cornerRadius = a.dp(36).toFloat()
        t.background = bg
        t.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    squish(bg, true, base)
                }
                MotionEvent.ACTION_UP -> {
                    squish(bg, false, base)
                    if (e.x >= 0f && e.x <= v.width && e.y >= 0f && e.y <= v.height) press(k)
                }
                MotionEvent.ACTION_CANCEL -> squish(bg, false, base)
            }
            true
        }
        return t
    }

    private fun pill(text: String, click: () -> Unit): TextView {
        val t = TextView(a)
        t.text = text
        t.textSize = 14f
        t.setTextColor(pal.ink)
        t.gravity = Gravity.CENTER
        t.setPadding(a.dp(22), a.dp(12), a.dp(22), a.dp(12))
        val g = GradientDrawable()
        g.setColor(pal.key)
        g.cornerRadius = a.dp(24).toFloat()
        t.background = g
        t.setOnClickListener { click() }
        return t
    }

    private fun emergency() {
        try {
            a.startActivity(Intent("com.android.phone.EmergencyDialer.DIAL").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try {
                a.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (x: Exception) {
            }
        }
    }

    private fun pin() {
        title.text = L(a, "Введите PIN-код", "Enter your PIN")
        title.textSize = 16f
        // середина экрана: заголовок, точки, клавиатура; кнопки "Отмена" и "Экстренный вызов" прижаты к низу
        val mid = LinearLayout(a)
        mid.orientation = LinearLayout.VERTICAL
        mid.gravity = Gravity.CENTER
        mid.addView(title, LinearLayout.LayoutParams(-2, -2))
        val d = PinDots(a, 4, pal.ink, pal.dim)
        dots = d
        val dl = LinearLayout.LayoutParams(-2, -2)
        dl.topMargin = a.dp(26)
        dl.bottomMargin = a.dp(34)
        mid.addView(d, dl)
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", ""))
        for (row in rows) {
            val rl = LinearLayout(a)
            rl.orientation = LinearLayout.HORIZONTAL
            rl.gravity = Gravity.CENTER
            for (k in row) {
                val lp = LinearLayout.LayoutParams(a.dp(72), a.dp(72))
                lp.setMargins(a.dp(7), a.dp(6), a.dp(7), a.dp(6))
                rl.addView(key(k), lp)
            }
            mid.addView(rl)
        }
        addView(mid, LinearLayout.LayoutParams(-1, 0, 1f))
        val bar = LinearLayout(a)
        bar.orientation = LinearLayout.HORIZONTAL
        val bl = LinearLayout.LayoutParams(-2, -2)
        bl.setMargins(a.dp(6), 0, a.dp(6), 0)
        bar.addView(pill(L(a, "Отмена", "Cancel")) { cancel() }, bl)
        bar.addView(pill(L(a, "Экстренный вызов", "Emergency")) { emergency() }, bl)
        val barLp = LinearLayout.LayoutParams(-2, -2)
        barLp.topMargin = a.dp(8)
        barLp.bottomMargin = a.dp(8)
        addView(bar, barLp)
    }

    private fun press(k: String) {
        if (busy) return
        if (k == "⌫") {
            if (code.isNotEmpty()) {
                code = code.dropLast(1)
                dots?.set(code.length)
            }
            return
        }
        if (code.length >= 4) return
        code += k
        dots?.set(code.length)
        if (code.length == 4) {
            if (sp.check(code)) {
                ok()
            } else {
                busy = true
                title.text = L(a, "Неверный PIN-код", "Wrong PIN")
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                shake()
                postDelayed({
                    code = ""
                    dots?.set(0)
                    busy = false
                }, 280)
            }
        }
    }

    private fun pattern() {
        title.text = L(a, "Нарисуйте графический ключ", "Draw your pattern")
        val pv = PatternView(a, false) { v, c ->
            if (sp.check(c)) {
                ok()
            } else {
                title.text = L(a, "Неверный ключ", "Wrong pattern")
                v.fail()
            }
        }
        val lp = LinearLayout.LayoutParams(a.dp(260), a.dp(260))
        lp.topMargin = a.dp(18)
        addView(pv, lp)
    }
}
