package com.example.pixellauncher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
    private var hyper = false
    private var tint = 0xFF121214.toInt()
    private var anim: ValueAnimator? = null
    private var ease: Interpolator = curveOf(0)
    private val density = c.resources.displayMetrics.density
    var cornerDp = 40
    var speed = 340
    var curve = 0
    var land = false

    init { visibility = INVISIBLE }

    private fun run(a: Float, b: Float, ms: Long, half: (() -> Unit)?, end: () -> Unit) {
        anim?.cancel()
        var fired = false
        val at = if (hyper) 0.9f else 0.7f
        val va = ValueAnimator.ofFloat(a, b)
        anim = va
        visibility = VISIBLE
        va.duration = ms
        va.interpolator = ease
        va.addUpdateListener {
            t = it.animatedValue as Float
            if (!fired && half != null && t > at) {
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

    private fun tintOf(v: View): Int {
        val base = 0x12
        if (v.width <= 0 || v.height <= 0) return Color.rgb(base, base, base + 2)
        val b = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        cv.scale(8f / v.width, 8f / v.height)
        v.draw(cv)
        var r = 0
        var g = 0
        var bl = 0
        var n = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val p = b.getPixel(x, y)
                if (Color.alpha(p) > 128) {
                    r += Color.red(p)
                    g += Color.green(p)
                    bl += Color.blue(p)
                    n++
                }
            }
        }
        b.recycle()
        if (n == 0) return Color.rgb(base, base, base + 2)
        val k = 0.22f
        return Color.rgb((base + (r / n - base) * k).toInt(),
            (base + (g / n - base) * k).toInt(), (base + (bl / n - base) * k).toInt())
    }

    fun expand(rect: RectF, v: View, half: () -> Unit) {
        hyper = Prefs(context).preset == 1
        if (hyper) tint = tintOf(v)
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
        if (hyper) drawHyper(cv) else drawStd(cv)
    }

    private fun drawStd(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val fw = if (land) h else w
        val fh = if (land) w else h
        val cx = from.centerX() + (w / 2f - from.centerX()) * t
        val cy = from.centerY() + (h / 2f - from.centerY()) * t
        val bw = from.width() + (fw - from.width()) * t
        val bh = from.height() + (fh - from.height()) * t
        val rad = maxOf(0f, from.width() * 0.3f * (1 - t) + cornerDp * density * t)
        paint.color = 0xFF202124.toInt()
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

    @Suppress("DEPRECATION")
    private fun layerAlpha(cv: Canvas, w: Float, h: Float, a: Int) {
        cv.saveLayerAlpha(0f, 0f, w, h, a)
    }

    private fun drawHyper(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val tc = t.coerceIn(0f, 1f)
        val fw = if (land) h else w
        val fh = if (land) w else h
        val cx = from.centerX() + (w / 2f - from.centerX()) * t
        val cy = from.centerY() + (h / 2f - from.centerY()) * t
        val bw = from.width() + (fw - from.width()) * t
        val bh = from.height() + (fh - from.height()) * t
        val rad = maxOf(0f, from.width() * 0.3f * (1 - tc) + cornerDp * density * tc)
        paint.color = tint
        paint.alpha = (255 * minOf(1f, tc * 6f)).toInt()
        cv.save()
        cv.translate(cx, cy)
        if (land) cv.rotate(90f * tc)
        box.set(-bw / 2f, -bh / 2f, bw / 2f, bh / 2f)
        cv.drawRoundRect(box, rad, rad, paint)
        cv.restore()
        val v = src ?: return
        if (v.width <= 0) return
        val sc = 1f + 0.3f * minOf(1f, tc / 0.12f)
        val fade = 1f - minOf(1f, maxOf(0f, (tc - 0.12f) / 0.25f))
        if (fade <= 0f) return
        val n = cv.save()
        cv.translate(from.centerX(), from.centerY())
        cv.scale(sc, sc)
        cv.translate(-v.width / 2f, -v.height / 2f)
        layerAlpha(cv, v.width.toFloat(), v.height.toFloat(), (255 * fade).toInt())
        v.draw(cv)
        cv.restoreToCount(n)
    }
}
