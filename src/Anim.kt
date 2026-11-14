package com.example.pixellauncher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.Interpolator

class WindowCard(c: Context) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private var from = RectF()
    private var src: View? = null
    private var t = 0f
    private var shown = false
    private var anim: ValueAnimator? = null
    private var ease: Interpolator = curveOf(0)
    private val density = c.resources.displayMetrics.density
    var cornerDp = 40
    var speed = 340
    var curve = 0
    var land = false

    init {
        visibility = INVISIBLE
        paint.color = 0xFF202124.toInt()
    }

    private fun run(a: Float, b: Float, ms: Long, half: (() -> Unit)?, end: () -> Unit) {
        anim?.cancel()
        var fired = false
        val va = ValueAnimator.ofFloat(a, b)
        anim = va
        visibility = VISIBLE
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
            override fun onAnimationEnd(x: Animator) {
                if (anim === va) end()
            }
        })
        va.start()
    }

    fun expand(rect: RectF, v: View, half: () -> Unit) {
        from = rect
        src = v
        t = 0f
        shown = true
        ease = curveOf(curve)
        run(0f, 1f, speed.toLong(), half) {}
    }

    fun collapse() {
        if (!shown) return
        shown = false
        ease = curveOf(curve)
        run(t, 0f, (speed * 0.85f).toLong(), null) { visibility = INVISIBLE }
    }

    fun cancel() {
        if (!shown) return
        shown = false
        ease = curveOf(0)
        run(t, 0f, 220, null) { visibility = INVISIBLE }
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val fw = if (land) h else w
        val fh = if (land) w else h
        val cx = from.centerX() + (w / 2f - from.centerX()) * t
        val cy = from.centerY() + (h / 2f - from.centerY()) * t
        val bw = from.width() + (fw - from.width()) * t
        val bh = from.height() + (fh - from.height()) * t
        val rad = maxOf(0f, from.width() * 0.3f * (1 - t) + cornerDp * density * t)
        paint.alpha = (255 * minOf(1f, maxOf(0f, t * 5f))).toInt()
        cv.save()
        cv.translate(cx, cy)
        if (land) cv.rotate(90f * t)
        box.set(-bw / 2f, -bh / 2f, bw / 2f, bh / 2f)
        cv.drawRoundRect(box, rad, rad, paint)
        val v = src
        if (v != null && v.width > 0) {
            cv.translate(-v.width / 2f, -v.height / 2f)
            v.draw(cv)
        }
        cv.restore()
    }
}
