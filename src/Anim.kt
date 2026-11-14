package com.example.pixellauncher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.PathInterpolator

class WindowCard(c: Context) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private var from = RectF()
    private var icon: Drawable? = null
    private var t = 0f
    private var shown = false
    private val ease = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    private val corner = 40f * c.resources.displayMetrics.density

    init {
        visibility = INVISIBLE
        paint.color = 0xFF202124.toInt()
    }

    private fun run(a: Float, b: Float, ms: Long, half: (() -> Unit)?, end: () -> Unit) {
        var fired = false
        val va = ValueAnimator.ofFloat(a, b)
        va.duration = ms
        va.interpolator = ease
        va.addUpdateListener {
            t = it.animatedValue as Float
            if (!fired && half != null && t > 0.7f) {
                fired = true
                half()
            }
            invalidate()
        }
        va.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(x: Animator) { end() }
        })
        va.start()
    }

    fun expand(rect: RectF, ic: Drawable, half: () -> Unit) {
        from = rect
        icon = ic
        t = 0f
        shown = true
        visibility = VISIBLE
        run(0f, 1f, 340, half) {}
    }

    fun collapse() {
        if (!shown) return
        shown = false
        run(1f, 0f, 300, null) { visibility = INVISIBLE }
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        box.set(from.left * (1 - t), from.top * (1 - t),
            from.right + (w - from.right) * t, from.bottom + (h - from.bottom) * t)
        val rad = from.width() * 0.3f * (1 - t) + corner * t
        paint.alpha = (255 * minOf(1f, t * 5f)).toInt()
        cv.drawRoundRect(box, rad, rad, paint)
        val d = icon ?: return
        val cx = box.centerX().toInt()
        val cy = box.centerY().toInt()
        val hw = (from.width() / 2).toInt()
        val hh = (from.height() / 2).toInt()
        d.setBounds(cx - hw, cy - hh, cx + hw, cy + hh)
        d.draw(cv)
    }
}
