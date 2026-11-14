package com.example.pixellauncher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.PathInterpolator

class WindowCard(c: Context) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private var from = RectF()
    private var src: View? = null
    private var t = 0f
    private var shown = false
    private val ease = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    private val density = c.resources.displayMetrics.density
    var cornerDp = 40
    var speed = 340

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

    fun expand(rect: RectF, v: View, half: () -> Unit) {
        from = rect
        src = v
        t = 0f
        shown = true
        visibility = VISIBLE
        run(0f, 1f, speed.toLong(), half) {}
    }

    fun collapse() {
        if (!shown) return
        shown = false
        run(1f, 0f, (speed * 0.85f).toLong(), null) { visibility = INVISIBLE }
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        box.set(from.left * (1 - t), from.top * (1 - t),
            from.right + (w - from.right) * t, from.bottom + (h - from.bottom) * t)
        val rad = from.width() * 0.3f * (1 - t) + cornerDp * density * t
        paint.alpha = (255 * minOf(1f, t * 5f)).toInt()
        cv.drawRoundRect(box, rad, rad, paint)
        val v = src ?: return
        if (v.width == 0) return
        cv.save()
        cv.translate(box.centerX() - v.width / 2f, box.centerY() - v.height / 2f)
        v.draw(cv)
        cv.restore()
    }
}
