package com.example.pixellauncher

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView

class AuthPanel(private val a: Activity, private val sp: LockPrefs,
                private val ok: () -> Unit, private val cancel: () -> Unit) : LinearLayout(a) {
    private val title = TextView(a)
    private val dots = TextView(a)
    private var code = ""

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val g = GradientDrawable()
        g.setColor(0xF01E1519.toInt())
        val r = a.dp(28).toFloat()
        g.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        background = g
        isClickable = true
        title.setTextColor(Color.WHITE)
        title.textSize = 18f
        title.gravity = Gravity.CENTER
        addView(title, LinearLayout.LayoutParams(-1, -2))
        if (sp.type == 1) pin() else pattern()
        val back = TextView(a)
        back.text = L(a, "Отмена", "Cancel")
        back.setTextColor(0xFFFFA6D6.toInt())
        back.textSize = 16f
        back.setPadding(a.dp(24), a.dp(14), a.dp(24), a.dp(4))
        back.setOnClickListener { cancel() }
        addView(back)
    }

    private fun circle(color: Int): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(color)
        return g
    }

    private fun paint() {
        dots.text = "●".repeat(code.length) + "○".repeat(4 - code.length)
    }

    private fun shake() {
        dots.animate().translationX(-24f).setDuration(50).withEndAction {
            dots.animate().translationX(24f).setDuration(70).withEndAction {
                dots.animate().translationX(0f).setDuration(50).start()
            }.start()
        }.start()
    }

    private fun pin() {
        title.text = L(a, "Введите PIN-код", "Enter PIN")
        dots.setTextColor(Color.WHITE)
        dots.textSize = 26f
        dots.letterSpacing = 0.3f
        dots.gravity = Gravity.CENTER
        paint()
        val dl = LinearLayout.LayoutParams(-1, -2)
        dl.topMargin = a.dp(14)
        dl.bottomMargin = a.dp(10)
        addView(dots, dl)
        val grid = GridLayout(a)
        grid.columnCount = 3
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        for (k in keys) {
            val t = TextView(a)
            t.text = k
            t.textSize = 26f
            t.setTextColor(Color.WHITE)
            t.gravity = Gravity.CENTER
            if (k.isNotEmpty()) {
                t.background = circle(0x33FFFFFF)
                t.setOnClickListener { press(k) }
            }
            val p = GridLayout.LayoutParams()
            p.width = a.dp(72)
            p.height = a.dp(72)
            p.setMargins(a.dp(10), a.dp(5), a.dp(10), a.dp(5))
            grid.addView(t, p)
        }
        addView(grid)
    }

    private fun press(k: String) {
        if (k == "⌫") {
            if (code.isNotEmpty()) code = code.dropLast(1)
            paint()
            return
        }
        if (code.length >= 4) return
        code += k
        paint()
        if (code.length == 4) {
            if (sp.check(code)) {
                ok()
            } else {
                title.text = L(a, "Неверный PIN-код", "Wrong PIN")
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                shake()
                code = ""
                paint()
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
