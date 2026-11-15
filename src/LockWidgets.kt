package com.example.pixellauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot

class PatternView(c: Context, private val dark: Boolean, private val done: (PatternView, String) -> Unit) : View(c) {
    private val d = c.resources.displayMetrics.density
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ln = Paint(Paint.ANTI_ALIAS_FLAG)
    private val seq = ArrayList<Int>()
    private var fx = 0f
    private var fy = 0f
    private var down = false
    var error = false
        set(v) {
            field = v
            invalidate()
        }

    init {
        ln.style = Paint.Style.STROKE
        ln.strokeCap = Paint.Cap.ROUND
    }

    private fun px(i: Int): Float = (i % 3 + 0.5f) * width / 3f
    private fun py(i: Int): Float = (i / 3 + 0.5f) * height / 3f

    private fun add(i: Int) {
        if (seq.isNotEmpty()) {
            val l = seq.last()
            val dr = abs(l / 3 - i / 3)
            val dc = abs(l % 3 - i % 3)
            if ((dr == 2 || dr == 0) && (dc == 2 || dc == 0)) {
                val mid = ((l / 3 + i / 3) / 2) * 3 + (l % 3 + i % 3) / 2
                if (mid !in seq) seq.add(mid)
            }
        }
        seq.add(i)
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun reset() {
        seq.clear()
        error = false
        down = false
        invalidate()
    }

    fun fail() {
        error = true
        postDelayed({ reset() }, 600)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                seq.clear()
                error = false
                down = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                down = false
                invalidate()
                if (seq.isNotEmpty()) done(this, seq.joinToString(""))
                return true
            }
        }
        fx = e.x
        fy = e.y
        for (i in 0 until 9) {
            if (i !in seq && hypot(fx - px(i), fy - py(i)) < 30f * d) {
                add(i)
                break
            }
        }
        invalidate()
        return true
    }

    override fun onDraw(cv: Canvas) {
        val base = if (dark) 0xFF6B5A63.toInt() else 0xCCFFFFFF.toInt()
        val hot = if (error) 0xFFFF5252.toInt() else 0xFFFF8FC8.toInt()
        ln.color = hot
        ln.strokeWidth = 5f * d
        for (k in 1 until seq.size) cv.drawLine(px(seq[k - 1]), py(seq[k - 1]), px(seq[k]), py(seq[k]), ln)
        if (down && seq.isNotEmpty()) cv.drawLine(px(seq.last()), py(seq.last()), fx, fy, ln)
        for (i in 0 until 9) {
            val on = i in seq
            dot.color = if (on) hot else base
            cv.drawCircle(px(i), py(i), (if (on) 9f else 6f) * d, dot)
        }
    }
}

class FingerprintView(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    var ring = 0f
        set(v) {
            field = v
            invalidate()
        }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.style = Paint.Style.FILL
        p.color = 0xE6F7E8EF.toInt()
        cv.drawCircle(w / 2f, h / 2f, w / 2f, p)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        if (ring > 0f) {
            p.strokeWidth = w * 0.05f
            p.color = 0xFFFF8FC8.toInt()
            r.set(w * 0.06f, w * 0.06f, w * 0.94f, w * 0.94f)
            cv.drawArc(r, -90f, 360f * ring, false, p)
        }
        p.strokeWidth = w * 0.032f
        p.color = 0xFF4A2F3B.toInt()
        val cx = w / 2f
        val cy = h / 2f
        val ks = floatArrayOf(0.12f, 0.2f, 0.28f, 0.36f)
        for (i in ks.indices) {
            val k = ks[i] * w
            r.set(cx - k, cy - k, cx + k, cy + k)
            cv.drawArc(r, 205f + i * 6f, 290f - i * 18f, false, p)
        }
        cv.drawLine(cx, cy - w * 0.02f, cx, cy + w * 0.06f, p)
    }
}
