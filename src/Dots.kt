package com.example.pixellauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.abs

class PageDots(c: Context) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var n = 1
    private var frac = 0f

    init { paint.color = Color.WHITE }

    fun set(count: Int, f: Float) {
        n = count
        frac = f
        invalidate()
    }

    override fun onDraw(cv: Canvas) {
        if (n < 2) return
        val d = context.dp(7).toFloat()
        val gap = context.dp(8).toFloat()
        var total = (n - 1) * gap
        for (i in 0 until n) total += d + d * (1f - minOf(1f, abs(i - frac)))
        var x = (width - total) / 2f
        val cy = height / 2f
        for (i in 0 until n) {
            val a = 1f - minOf(1f, abs(i - frac))
            paint.alpha = (110 + 145 * a).toInt()
            val w = d + d * a
            cv.drawRoundRect(x, cy - d / 2f, x + w, cy + d / 2f, d / 2f, d / 2f, paint)
            x += w + gap
        }
    }
}
