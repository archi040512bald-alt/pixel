package com.example.pixellauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.widget.TextView

class SearchBar(c: Context) : TextView(c) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val logo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private val path = Path()

    init {
        fill.color = 0xF2ECEBF1.toInt()
        line.style = Paint.Style.STROKE
        line.strokeCap = Paint.Cap.ROUND
        line.color = 0xFF1E88E5.toInt()
    }

    override fun setBackground(background: Drawable?) {}

    private fun band(cv: Canvas, color: Int, cx: Float, top: Float, y1: Float, y2: Float, side: Int) {
        val h1 = (y1 - top) / 2f
        val h2 = (y2 - top) / 2f + 0.6f
        val l1 = if (side == 1) cx else cx - h1
        val r1 = if (side == -1) cx else cx + h1
        val l2 = if (side == 1) cx else cx - h2
        val r2 = if (side == -1) cx else cx + h2
        path.reset()
        path.moveTo(l1, y1)
        path.lineTo(r1, y1)
        path.lineTo(r2, y2)
        path.lineTo(l2, y2)
        path.close()
        logo.color = color
        cv.drawPath(path, logo)
    }

    override fun onDraw(cv: Canvas) {
        val d = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        box.set(0f, 0f, w, h)
        cv.drawRoundRect(box, h / 2f, h / 2f, fill)

        val mx = 31f * d
        val my = h / 2f - 1.5f * d
        val r = 8f * d
        line.strokeWidth = 2.8f * d
        cv.drawCircle(mx, my, r, line)
        val o = r * 0.72f
        cv.drawLine(mx + o, my + o, mx + o + 6.5f * d, my + o + 6.5f * d, line)

        val s = 24f * d
        val lx = w - 31f * d
        val top = h / 2f - s / 2f
        band(cv, 0xFFF4517F.toInt(), lx, top, top, top + 0.42f * s, 0)
        band(cv, 0xFF2F9BFF.toInt(), lx, top, top + 0.42f * s, top + 0.72f * s, 0)
        band(cv, 0xFFFFC83D.toInt(), lx, top, top + 0.72f * s, top + s, -1)
        band(cv, 0xFF3DDC84.toInt(), lx, top, top + 0.72f * s, top + s, 1)
    }
}
