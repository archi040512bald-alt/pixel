package com.example.pixellauncher

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.speech.RecognizerIntent
import android.view.MotionEvent
import android.widget.TextView

/** Панель поиска в стиле Google: значок G слева, микрофон и Lens справа. */
class GoogleBar(c: Context) : TextView(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val r2 = RectF()
    private val d = c.resources.displayMetrics.density
    private val night = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val pr = Prefs(c)
    private var bgColor = if (night) 0xFF303540.toInt() else 0xFFE9E8F0.toInt()
    private var ink = if (night) 0xFFC4C7CC.toInt() else 0xFF5F6368.toInt()

    private fun px(v: Float) = v * d
    private fun lensCx() = width - px(30f)
    private fun micCx() = lensCx() - px(44f)

    init {
        contentDescription = "Google"
        // цвет панели: 0 = по умолчанию, 1 = от обоев (Material You), 2 = свой цвет
        when (pr.searchColor) {
            1 -> {
                val pal = IconTheme.palette(c, IconTheme.WALLPAPER, 0)
                bgColor = pal[0]
                ink = pal[1]
            }
            2 -> {
                val pal = IconTheme.palette(c, IconTheme.CUSTOM, pr.searchSeed)
                bgColor = pal[0]
                ink = pal[1]
            }
        }
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.style = Paint.Style.FILL
        p.color = bgColor
        r.set(0f, 0f, w, h)
        cv.drawRoundRect(r, h / 2f, h / 2f, p)
        val cy = h / 2f
        when (pr.searchIcon) {
            0 -> drawG(cv, px(34f), cy, true)
            1 -> drawG(cv, px(34f), cy, false)
            2 -> drawGlass(cv, px(34f), cy)
        }
        drawMic(cv, micCx(), cy)
        drawLens(cv, lensCx(), cy)
    }

    private fun drawGlass(cv: Canvas, cx: Float, cy: Float) {
        p.color = ink
        p.style = Paint.Style.STROKE
        p.strokeWidth = px(2.4f)
        p.strokeCap = Paint.Cap.ROUND
        cv.drawCircle(cx - px(2f), cy - px(2f), px(7f), p)
        cv.drawLine(cx + px(3f), cy + px(3f), cx + px(9f), cy + px(9f), p)
    }

    private fun drawG(cv: Canvas, cx: Float, cy: Float, colorful: Boolean) {
        val sw = px(3.6f)
        val rad = px(10f) - sw / 2f
        r.set(cx - rad, cy - rad, cx + rad, cy + rad)
        p.style = Paint.Style.STROKE
        p.strokeWidth = sw
        p.strokeCap = Paint.Cap.BUTT
        p.color = if (colorful) 0xFFEA4335.toInt() else ink
        cv.drawArc(r, 215f, 100f, false, p)
        p.color = if (colorful) 0xFFFBBC05.toInt() else ink
        cv.drawArc(r, 135f, 80f, false, p)
        p.color = if (colorful) 0xFF34A853.toInt() else ink
        cv.drawArc(r, 45f, 90f, false, p)
        p.color = if (colorful) 0xFF4285F4.toInt() else ink
        cv.drawArc(r, 0f, 45f, false, p)
        p.color = if (colorful) 0xFF4285F4.toInt() else ink
        p.style = Paint.Style.FILL
        r2.set(cx, cy - sw / 2f, cx + rad + sw / 2f, cy + sw / 2f)
        cv.drawRect(r2, p)
    }

    private fun drawMic(cv: Canvas, cx: Float, cy: Float) {
        p.color = ink
        p.style = Paint.Style.FILL
        r.set(cx - px(4f), cy - px(10f), cx + px(4f), cy + px(2f))
        cv.drawRoundRect(r, px(4f), px(4f), p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = px(2f)
        p.strokeCap = Paint.Cap.ROUND
        r.set(cx - px(7f), cy - px(7f), cx + px(7f), cy + px(7f))
        cv.drawArc(r, 0f, 180f, false, p)
        cv.drawLine(cx, cy + px(7f), cx, cy + px(11f), p)
    }

    private fun drawLens(cv: Canvas, cx: Float, cy: Float) {
        p.color = ink
        p.style = Paint.Style.STROKE
        p.strokeWidth = px(2f)
        r.set(cx - px(9f), cy - px(9f), cx + px(9f), cy + px(9f))
        cv.drawRoundRect(r, px(5f), px(5f), p)
        cv.drawCircle(cx, cy, px(3.6f), p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            val hit = px(22f)
            val which = if (Math.abs(e.x - micCx()) < hit) 1 else if (Math.abs(e.x - lensCx()) < hit) 2 else 0
            if (which != 0) {
                val cancel = MotionEvent.obtain(e)
                cancel.action = MotionEvent.ACTION_CANCEL
                super.onTouchEvent(cancel)
                cancel.recycle()
                if (which == 1) voice() else lens()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    private fun voice() {
        try {
            context.startActivity(Intent(RecognizerIntent.ACTION_WEB_SEARCH).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
        }
    }

    private fun lens() {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("googleapp://lens"))
            .setPackage("com.google.android.googlequicksearchbox").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(i)
        } catch (e: Exception) {
            val l = context.packageManager.getLaunchIntentForPackage("com.google.ar.lens")
            if (l != null) {
                try {
                    context.startActivity(l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (x: Exception) {
                }
            }
        }
    }
}
