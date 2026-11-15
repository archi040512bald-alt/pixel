package com.example.pixellauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

class GlyphView(c: Context, private val kind: String, private val tint: Int, private val disc: Int = 0) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val r = RectF()

    private fun stroke(w: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = w
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
    }

    private fun fill() {
        p.style = Paint.Style.FILL
    }

    private fun sq(cv: Canvas, l: Float, t: Float, rr: Float, b: Float, rad: Float) {
        r.set(l, t, rr, b)
        cv.drawRoundRect(r, rad, rad, p)
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (disc != 0) {
            fill()
            p.color = disc
            cv.drawCircle(w / 2f, h / 2f, minOf(w, h) / 2f, p)
        }
        val s = minOf(w, h) * (if (disc != 0) 0.56f else 1f)
        cv.save()
        cv.translate((w - s) / 2f, (h - s) / 2f)
        cv.scale(s / 24f, s / 24f)
        p.color = tint
        path.reset()
        when (kind) {
            "back" -> {
                stroke(2.2f)
                path.moveTo(19f, 12f); path.lineTo(5f, 12f)
                path.moveTo(11f, 6f); path.lineTo(5f, 12f); path.lineTo(11f, 18f)
                cv.drawPath(path, p)
            }
            "chev" -> {
                stroke(2.4f)
                path.moveTo(9f, 5f); path.lineTo(16f, 12f); path.lineTo(9f, 19f)
                cv.drawPath(path, p)
            }
            "close" -> {
                stroke(2.6f)
                cv.drawLine(6f, 6f, 18f, 18f, p)
                cv.drawLine(18f, 6f, 6f, 18f, p)
            }
            "info" -> {
                fill()
                cv.drawCircle(12f, 6.5f, 1.8f, p)
                sq(cv, 10.4f, 10f, 13.6f, 19f, 1.4f)
            }
            "trash" -> {
                fill()
                sq(cv, 6.5f, 8f, 17.5f, 20.5f, 1.6f)
                cv.drawRect(5f, 5.5f, 19f, 7.2f, p)
                cv.drawRect(9.5f, 3.5f, 14.5f, 5.5f, p)
            }
            "home" -> {
                fill()
                path.moveTo(12f, 3f); path.lineTo(2.5f, 11.5f); path.lineTo(5f, 11.5f); path.lineTo(5f, 20.5f)
                path.lineTo(10f, 20.5f); path.lineTo(10f, 15f); path.lineTo(14f, 15f); path.lineTo(14f, 20.5f)
                path.lineTo(19f, 20.5f); path.lineTo(19f, 11.5f); path.lineTo(21.5f, 11.5f); path.close()
                cv.drawPath(path, p)
            }
            "anim" -> {
                fill()
                sq(cv, 9f, 6f, 21f, 18f, 3.5f)
                stroke(1.8f)
                cv.drawLine(2f, 9f, 6f, 9f, p)
                cv.drawLine(1.5f, 12f, 6f, 12f, p)
                cv.drawLine(2f, 15f, 6f, 15f, p)
            }
            "shapes" -> {
                fill()
                sq(cv, 3f, 3f, 10.5f, 10.5f, 1.5f)
                path.moveTo(17.2f, 2.6f); path.lineTo(21.4f, 6.8f); path.lineTo(17.2f, 11f); path.lineTo(13f, 6.8f); path.close()
                cv.drawPath(path, p)
                sq(cv, 13f, 13.5f, 21f, 21f, 2.5f)
                stroke(2f)
                cv.drawCircle(6.7f, 17.3f, 3.4f, p)
            }
            "grid" -> {
                stroke(1.9f)
                sq(cv, 3.5f, 4.5f, 20.5f, 19.5f, 2.5f)
                cv.drawLine(3.5f, 9.5f, 20.5f, 9.5f, p)
                cv.drawLine(3.5f, 14.5f, 20.5f, 14.5f, p)
                cv.drawLine(9.2f, 9.5f, 9.2f, 19.5f, p)
                cv.drawLine(14.8f, 9.5f, 14.8f, 19.5f, p)
            }
            "image" -> {
                stroke(1.9f)
                sq(cv, 3.5f, 4f, 20.5f, 20f, 3f)
                fill()
                path.moveTo(6.5f, 17f); path.lineTo(10.5f, 11.5f); path.lineTo(13.5f, 15.2f)
                path.lineTo(15.8f, 12.5f); path.lineTo(19f, 17f); path.close()
                cv.drawPath(path, p)
                cv.drawCircle(16.5f, 8.3f, 1.5f, p)
            }
            "globe" -> {
                stroke(1.8f)
                cv.drawCircle(12f, 12f, 9f, p)
                r.set(8f, 3f, 16f, 21f)
                cv.drawOval(r, p)
                cv.drawLine(3f, 12f, 21f, 12f, p)
                cv.drawLine(4.6f, 7.3f, 19.4f, 7.3f, p)
                cv.drawLine(4.6f, 16.7f, 19.4f, 16.7f, p)
            }
            "launcher" -> {
                fill()
                sq(cv, 3f, 3f, 11f, 11f, 2.2f)
                sq(cv, 13f, 3f, 21f, 11f, 2.2f)
                sq(cv, 3f, 13f, 11f, 21f, 2.2f)
                sq(cv, 13f, 13f, 21f, 21f, 2.2f)
            }
        }
        cv.restore()
    }
}
