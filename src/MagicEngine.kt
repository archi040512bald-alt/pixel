package com.example.pixellauncher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.max
import kotlin.math.min

/**
 * Новый движок анимаций (используется только пресетом MagicOS).
 *
 * Отличия от старых пресетов:
 *  - вся анимация описана одним состоянием p (прогресс карточки 0..1) и фазами OPEN / HOLD / CLOSE / POP;
 *  - любая фаза запускается с текущего p, поэтому прерывание (тап, потом сразу назад, потом снова тап)
 *    идёт без скачков, как на видео, где иконки нажимают много раз подряд;
 *  - размытие фона отдаётся наружу через onProgress(p) на каждом кадре, движок сам ничего не знает про домашний экран;
 *  - формы кривых берутся из MagicSpec, здесь только логика и рисование.
 */
class MagicEngine(private val host: View) {
    private enum class Ph { IDLE, OPEN, HOLD, CLOSE, POP }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private val from = RectF()
    private var src: View? = null
    private var ph = Ph.IDLE
    private var anim: ValueAnimator? = null

    private var p = 0f          // прогресс карточки
    private var sc0 = MagicSpec.START_SCALE   // масштаб значка, от которого считается нулевая точка карточки
    private var popSc = 1f
    private var surface = MagicSpec.SURFACE_LIGHT

    /** Прогресс карточки на каждом кадре (для размытия и затемнения фона). */
    var onProgress: ((Float) -> Unit)? = null

    private fun stop() {
        val old = anim
        anim = null
        old?.cancel()
    }

    private fun start(ms: Float, step: (Float) -> Unit, end: () -> Unit) {
        stop()
        val va = ValueAnimator.ofFloat(0f, 1f)
        anim = va
        va.duration = ms.toLong().coerceAtLeast(1L)
        va.interpolator = LinearInterpolator()
        va.addUpdateListener {
            step(it.animatedValue as Float)
            host.invalidate()
        }
        va.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(x: Animator) {
                if (anim === va) end()
            }
        })
        va.start()
    }

    private fun report() {
        onProgress?.invoke(if (ph == Ph.POP || ph == Ph.IDLE) 0f else p)
    }

    fun open(rect: RectF, icon: View?, surfaceColor: Int, half: (() -> Unit)?) {
        // если открываем поверх закрытия, продолжаем с текущего прогресса
        val cont = ph == Ph.OPEN || ph == Ph.HOLD || ph == Ph.CLOSE
        val p0 = if (cont) p else 0f
        val s0 = if (cont) sc0 else MagicSpec.START_SCALE
        from.set(rect)
        src = icon
        surface = surfaceColor
        ph = Ph.OPEN
        p = p0
        sc0 = s0
        val u0 = MagicSpec.openU(p0)
        var fired = false
        start(MagicSpec.OPEN_MS * (1f - u0), { f ->
            val u = u0 + (1f - u0) * f
            p = MagicSpec.openP(u)
            sc0 = s0 + (MagicSpec.START_SCALE - s0) * f
            if (!fired && half != null && p >= MagicSpec.LAUNCH_AT_P) {
                fired = true
                host.post { half() }
            }
            report()
        }, {
            p = 1f
            ph = Ph.HOLD
            if (!fired && half != null) {
                fired = true
                host.post { half() }
            }
            report()
        })
        report()
    }

    fun close(end: () -> Unit) {
        if (ph == Ph.IDLE) return
        if (ph == Ph.POP) return
        val p0 = p
        val s0 = sc0
        ph = Ph.CLOSE
        val ms = MagicSpec.CLOSE_MS * (MagicSpec.CLOSE_MIN_K + (1f - MagicSpec.CLOSE_MIN_K) * p0)
        var handed = false
        start(ms, { f ->
            p = p0 * MagicSpec.closeK(f)
            sc0 = s0 + (MagicSpec.END_SCALE - s0) * f
            if (!handed && p <= MagicSpec.HANDOFF_P) {
                handed = true
                host.post { pop(end) }
            }
            report()
        }, {
            if (!handed) {
                handed = true
                pop(end)
            }
        })
        report()
    }

    private fun pop(end: () -> Unit) {
        if (ph != Ph.CLOSE) return
        ph = Ph.POP
        p = 0f
        popSc = MagicSpec.POP_FROM
        report()
        start(MagicSpec.POP_MS, { f ->
            popSc = MagicSpec.popScale(f)
        }, {
            ph = Ph.IDLE
            popSc = 1f
            report()
            end()
        })
    }

    /** Сброс без анимации (например, при смене пресета). */
    fun reset() {
        stop()
        ph = Ph.IDLE
        p = 0f
        popSc = 1f
        report()
    }

    @Suppress("DEPRECATION")
    private fun layerAlpha(cv: Canvas, w: Float, h: Float, a: Int) {
        cv.saveLayerAlpha(0f, 0f, w, h, a)
    }

    private fun drawTile(cv: Canvas, cx: Float, cy: Float, size: Float, alpha: Float) {
        val v = src ?: return
        if (v.width <= 0 || alpha <= 0f) return
        val sc = size / v.width
        val n = cv.save()
        cv.translate(cx, cy)
        cv.scale(sc, sc)
        cv.translate(-v.width / 2f, -v.height / 2f)
        layerAlpha(cv, v.width.toFloat(), v.height.toFloat(), (255f * MagicSpec.clamp01(alpha)).toInt())
        v.draw(cv)
        cv.restoreToCount(n)
    }

    fun draw(cv: Canvas) {
        if (ph == Ph.IDLE) return
        val w = host.width.toFloat()
        val h = host.height.toFloat()
        val icx = from.centerX()
        val icy = from.centerY()
        if (ph == Ph.POP) {
            drawTile(cv, icx, icy, from.width() * popSc, 1f)
            return
        }
        val hw = from.width() / 2f * sc0
        val hh = from.height() / 2f * sc0
        val l = (icx - hw) * (1f - p)
        val t = (icy - hh) * (1f - p)
        val r = (icx + hw) + (w - (icx + hw)) * p
        val b = (icy + hh) + (h - (icy + hh)) * p
        val rad = max(0f, min(MagicSpec.RADIUS_MAX_W * w, MagicSpec.RADIUS_K * min(r - l, b - t)))
        paint.color = surface
        paint.alpha = 255
        box.set(l, t, r, b)
        cv.drawRoundRect(box, rad, rad, paint)
        // значок на карточке: при открытии гаснет, при закрытии проявляется
        val ia = if (ph == Ph.CLOSE) {
            MagicSpec.clamp01((MagicSpec.ICON_IN_FROM_P - p) / MagicSpec.ICON_IN_SPAN)
        } else {
            1f - MagicSpec.clamp01(p / MagicSpec.ICON_OUT_P)
        }
        if (ia > 0f) {
            val size = max(from.width() * 0.9f, MagicSpec.ICON_CARD_K * (r - l))
            drawTile(cv, (l + r) / 2f, (t + b) / 2f, size, ia)
        }
    }
}
