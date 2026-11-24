package com.example.pixellauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout

/**
 * Ячейка папки на рабочем столе. В режиме правки (меню по щипку) в правом нижнем углу папки
 * появляется линия-ручка, вдоль угла плитки. Её можно тянуть: Desk считает размер в ячейках сетки.
 */
class FolderCell(c: Context, val item: DeskItem) : LinearLayout(c) {
    var desk: Desk? = null

    // отступы плитки внутри ячейки (задаёт Desk при создании)
    var insX = 0f
    var insY = 0f
    var extra = 0      // высота подписи вместе с отступом, 0 если подписей нет
    var r0 = 0f        // радиус углов плитки
    var sz = 0f        // сторона плитки 1x1

    private var editOn = false
    private var handleA = 0f
    private var grabV = 0f
    private var down = false
    private var sx = 0f
    private var sy = 0f
    private var aAnim: ValueAnimator? = null
    private var gAnim: ValueAnimator? = null
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = RectF()

    init {
        line.style = Paint.Style.STROKE
        line.strokeCap = Paint.Cap.ROUND
        line.color = Color.WHITE
        shade.style = Paint.Style.STROKE
        shade.strokeCap = Paint.Cap.ROUND
        shade.color = Color.BLACK
    }

    /** Плитка внутри ячейки размером w x h (в координатах ячейки). */
    fun tileFor(w: Int, h: Int): RectF {
        val pt = context.dp(6).toFloat()
        return RectF(insX, pt + insY, w - insX, h - pt - extra - insY)
    }

    fun setEdit(on: Boolean, animate: Boolean) {
        if (editOn == on && animate) return
        editOn = on
        aAnim?.cancel()
        val to = if (on) 1f else 0f
        if (!animate) {
            handleA = to
            invalidate()
            return
        }
        val va = ValueAnimator.ofFloat(handleA, to)
        va.duration = if (on) 320L else 160L
        va.interpolator = if (on) OvershootInterpolator(2f) else DecelerateInterpolator()
        va.addUpdateListener {
            handleA = it.animatedValue as Float
            invalidate()
        }
        aAnim = va
        va.start()
    }

    /** Ручку взяли или отпустили: линия становится толще и ярче. */
    fun grab(on: Boolean) {
        gAnim?.cancel()
        val va = ValueAnimator.ofFloat(grabV, if (on) 1f else 0f)
        va.duration = 160
        va.addUpdateListener {
            grabV = it.animatedValue as Float
            invalidate()
        }
        gAnim = va
        va.start()
    }

    private fun hitHandle(x: Float, y: Float): Boolean {
        val t = tileFor(width, height)
        val big = context.dp(40).toFloat()
        return x >= t.right - big && x <= t.right + insX + context.dp(4) &&
            y >= t.bottom - big && y <= t.bottom + context.dp(12)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            down = editOn && handleA > 0.6f && hitHandle(ev.x, ev.y)
        }
        return down
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!down) return super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                sx = ev.rawX
                sy = ev.rawY
                parent?.requestDisallowInterceptTouchEvent(true)
                desk?.resizeBegin(this)
            }
            MotionEvent.ACTION_MOVE -> desk?.resizeMove(ev.rawX - sx, ev.rawY - sy)
            MotionEvent.ACTION_UP -> {
                down = false
                desk?.resizeEnd(false)
            }
            MotionEvent.ACTION_CANCEL -> {
                down = false
                desk?.resizeEnd(true)
            }
        }
        return true
    }

    override fun dispatchDraw(cv: Canvas) {
        super.dispatchDraw(cv)
        if (handleA <= 0.01f) return
        val t = tileFor(width, height)
        val d = context.resources.displayMetrics.density
        val rad = maxOf(r0 - 5f * d, 9f * d)
        val cx = t.right - r0
        val cy = t.bottom - r0
        arc.set(cx - rad, cy - rad, cx + rad, cy + rad)
        val a = handleA.coerceIn(0f, 1f)
        val w = 3.2f * d * (1f + 0.5f * grabV) * (0.6f + 0.4f * a)
        shade.strokeWidth = w + 1.5f * d
        shade.alpha = (70 * a).toInt()
        line.strokeWidth = w
        line.alpha = (255 * a * (0.82f + 0.18f * grabV)).toInt()
        cv.drawArc(arc, 22f, 56f, false, shade)
        cv.drawArc(arc, 22f, 56f, false, line)
    }
}
