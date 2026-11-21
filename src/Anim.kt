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
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import kotlin.math.pow

class WindowCard(c: Context) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private var from = RectF()
    private var src: View? = null
    private var hidden: View? = null
    private var t = 0f
    private var shown = false
    private var hyper = false
    private var reach = 50
    private var a0 = 0f
    private var tint = 0xFF1B1B1D.toInt()
    private var anim: ValueAnimator? = null
    private var ease: Interpolator = curveOf(0)
    private val soft = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    private val density = c.resources.displayMetrics.density

    // HyperOS: время в мс от касания (по замерам видео, см. HyperSpec)
    private var tm = 0f
    private var cm = 0f
    private var closing = false
    private var p0 = 0f
    private var b0 = 1f
    private var ca0 = 0f
    private var is0 = 1f

    var hideView: View? = null
    var cornerDp = 40
    var speed = 340
    var curve = 0
    var land = false

    init { visibility = INVISIBLE }

    private fun easeFor(): Interpolator {
        val base = curveOf(curve)
        val k = ((50 - reach) / 50f).coerceIn(0f, 1f)
        if (k <= 0f) return base
        return Interpolator { x ->
            val e = base.getInterpolation(x)
            e + (soft.getInterpolation(x) - e) * k
        }
    }

    // При закрытии карточка должна дойти до самой иконки, поэтому стартовое смещение снимаем
    private fun flatten() {
        t = a0 + (1f - a0) * t
        a0 = 0f
    }

    // Настоящий значок прячем на время полёта карточки, чтобы он не двоился
    private fun restore() {
        hidden?.alpha = 1f
        hidden = null
    }

    private fun run(a: Float, b: Float, ms: Long, half: (() -> Unit)?, end: () -> Unit) {
        val old = anim
        anim = null
        old?.cancel()
        var fired = false
        val at = 0.7f
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

    // HyperOS: аниматор идёт по абсолютному времени в мс, формы кривых заданы в HyperSpec
    private fun runHyper(fromV: Float, toV: Float, half: (() -> Unit)?, end: () -> Unit) {
        val old = anim
        anim = null
        old?.cancel()
        var fired = false
        val va = ValueAnimator.ofFloat(fromV, toV)
        anim = va
        visibility = VISIBLE
        va.duration = (toV - fromV).toLong().coerceAtLeast(1L)
        va.interpolator = LinearInterpolator()
        va.addUpdateListener {
            val v = it.animatedValue as Float
            if (closing) cm = v else tm = v
            if (!fired && half != null && v >= HyperSpec.OPEN_END * HyperSpec.LAUNCH_AT) {
                fired = true
                post { half() }
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

    // Цвет карточки HyperOS: в видео это цвет самого приложения (у Quizlet фирменный синий).
    // Берём самый большой по площади насыщенный оттенок значка (не среднее, иначе у разноцветных значков
    // получается грязный цвет) и доводим его до полной яркости.
    // Если насыщенных пикселей мало (белые, серые, чёрные значки, Chrome, Google, Play), карточка тёмная.
    private fun hyperColor(v: View): Int {
        val dark = Color.rgb(0x1B, 0x1B, 0x1D)
        if (v.width <= 0 || v.height <= 0) return dark
        val b = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        cv.scale(8f / v.width, 8f / v.height)
        v.draw(cv)
        val cnt = IntArray(12)
        val sr = IntArray(12)
        val sg = IntArray(12)
        val sb = IntArray(12)
        var opaque = 0
        val hsv = FloatArray(3)
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val p = b.getPixel(x, y)
                if (Color.alpha(p) > 128) {
                    opaque++
                    Color.colorToHSV(p, hsv)
                    if (hsv[1] > 0.35f && hsv[2] > 0.25f) {
                        val k = ((hsv[0] / 30f).toInt()) % 12
                        cnt[k]++
                        sr[k] += Color.red(p)
                        sg[k] += Color.green(p)
                        sb[k] += Color.blue(p)
                    }
                }
            }
        }
        b.recycle()
        var best = 0
        for (k in 1 until 12) if (cnt[k] > cnt[best]) best = k
        if (opaque == 0 || cnt[best] < opaque * 0.4f) return dark
        Color.RGBToHSV(sr[best] / cnt[best], sg[best] / cnt[best], sb[best] / cnt[best], hsv)
        hsv[1] = minOf(1f, hsv[1] * 1.2f)
        hsv[2] = 1f
        return Color.HSVToColor(hsv)
    }

    fun expand(rect: RectF, v: View, half: () -> Unit) {
        val p = Prefs(context)
        hyper = p.preset == 1
        reach = p.reach
        a0 = reach / 100f * 0.92f
        if (hyper) tint = hyperColor(v)
        restore()
        hidden = hideView
        hideView = null
        hidden?.alpha = 0f
        from = rect
        src = v
        t = 0f
        tm = 0f
        cm = 0f
        closing = false
        shown = true
        if (hyper) {
            runHyper(0f, HyperSpec.OPEN_END, half) {}
        } else {
            ease = easeFor()
            run(0f, 1f, speed.toLong(), half) {}
        }
    }

    private fun hyperClose() {
        val tc = tm.coerceAtMost(HyperSpec.OPEN_END)
        p0 = HyperSpec.cardProgress(tc)
        b0 = HyperSpec.cardBrightness(tc)
        ca0 = HyperSpec.clamp01((tc - HyperSpec.CARD_IN) / 33f)
        is0 = HyperSpec.iconScale(tc)
        closing = true
        cm = 0f
        val total = HyperSpec.CLOSE_MS + (HyperSpec.ICON_BACK_1_0 - HyperSpec.CLOSE_END)
        runHyper(0f, total, null) {
            closing = false
            visibility = INVISIBLE
            restore()
        }
    }

    fun collapse() {
        if (!shown) return
        shown = false
        if (hyper) {
            hyperClose()
            return
        }
        ease = easeFor()
        flatten()
        run(t, 0f, (speed * 0.85f).toLong(), null) {
            visibility = INVISIBLE
            restore()
        }
    }

    fun cancel() {
        if (!shown) return
        shown = false
        if (hyper) {
            hyperClose()
            return
        }
        ease = curveOf(0)
        flatten()
        run(t, 0f, 220, null) {
            visibility = INVISIBLE
            restore()
        }
    }

    override fun onDraw(cv: Canvas) {
        if (hyper) drawHyper(cv) else drawStd(cv)
    }

    private fun drawStd(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val g = a0 + (1f - a0) * t
        val gc = g.coerceIn(0f, 1f)
        val fw = if (land) h else w
        val fh = if (land) w else h
        val cx = from.centerX() + (w / 2f - from.centerX()) * g
        val cy = from.centerY() + (h / 2f - from.centerY()) * g
        val bw = from.width() + (fw - from.width()) * g
        val bh = from.height() + (fh - from.height()) * g
        val rad = maxOf(0f, from.width() * 0.3f * (1 - gc) + cornerDp * density * gc)
        paint.color = 0xFF202124.toInt()
        paint.alpha = (255 * minOf(1f, maxOf(0f, t * (5f + 20f * a0)))).toInt()
        cv.save()
        cv.translate(cx, cy)
        if (land) cv.rotate(90f * gc)
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

    private fun drawIcon(cv: Canvas, sc: Float, alpha: Float) {
        val v = src ?: return
        if (v.width <= 0 || alpha <= 0f) return
        val n = cv.save()
        cv.translate(from.centerX(), from.centerY())
        cv.scale(sc, sc)
        cv.translate(-v.width / 2f, -v.height / 2f)
        layerAlpha(cv, v.width.toFloat(), v.height.toFloat(), (255 * alpha.coerceIn(0f, 1f)).toInt())
        v.draw(cv)
        cv.restoreToCount(n)
    }

    private fun drawHyper(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val p: Float
        val br: Float
        val cardA: Float
        val iconSc: Float
        val iconA: Float
        if (!closing) {
            // открытие: время tm в мс от касания
            val tc = tm.coerceAtMost(HyperSpec.OPEN_END)
            p = HyperSpec.cardProgress(tc)
            br = HyperSpec.cardBrightness(tc)
            cardA = HyperSpec.clamp01((tc - HyperSpec.CARD_IN) / 33f)
            iconSc = HyperSpec.iconScale(tc)
            iconA = 1f - cardA
        } else {
            val cardMs = HyperSpec.CLOSE_MS
            if (cm < cardMs) {
                // карточка схлопывается в значок (q = 1 - u^1.45), темнеет к концу
                val u = cm / cardMs
                p = p0 * (1f - u.pow(HyperSpec.CLOSE_POWER))
                br = b0 + (0.68f - b0) * HyperSpec.clamp01((u - 0.25f) / 0.62f)
                cardA = ca0
                iconSc = is0
                iconA = 1f - ca0
            } else {
                // карточка исчезла, значок x1.8 возвращается к 1.0
                p = 0f
                br = 1f
                cardA = 0f
                iconSc = HyperSpec.iconScale(HyperSpec.CLOSE_END + (cm - cardMs))
                iconA = 1f
            }
        }
        if (cardA > 0f) {
            val icon = floatArrayOf(from.left, from.top, from.right, from.bottom)
            val r = HyperSpec.cardRect(p, icon, w, h)
            val rad = HyperSpec.cornerRadius(p, w)
            paint.color = Color.rgb(
                (Color.red(tint) * br).toInt().coerceIn(0, 255),
                (Color.green(tint) * br).toInt().coerceIn(0, 255),
                (Color.blue(tint) * br).toInt().coerceIn(0, 255)
            )
            paint.alpha = (255 * cardA).toInt()
            box.set(r[0], r[1], r[2], r[3])
            cv.drawRoundRect(box, rad, rad, paint)
        }
        drawIcon(cv, iconSc, iconA)
    }
}
