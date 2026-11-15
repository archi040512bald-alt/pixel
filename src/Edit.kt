package com.example.pixellauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class EditGlyph(c: Context, private val kind: String) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    init { p.color = Color.WHITE }

    override fun onDraw(cv: Canvas) {
        val s = minOf(width, height).toFloat()
        cv.save()
        cv.translate((width - s) / 2f, (height - s) / 2f)
        cv.scale(s / 24f, s / 24f)
        if (kind == "gear") {
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = 3f
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val ca = cos(a).toFloat()
                val sa = sin(a).toFloat()
                cv.drawLine(12f + 6.4f * ca, 12f + 6.4f * sa, 12f + 8.8f * ca, 12f + 8.8f * sa, p)
            }
            p.strokeWidth = 2.2f
            cv.drawCircle(12f, 12f, 6.6f, p)
            cv.drawCircle(12f, 12f, 3.2f, p)
        } else {
            p.style = Paint.Style.FILL
            r.set(3f, 4f, 21f, 10f)
            cv.drawRoundRect(r, 1.8f, 1.8f, p)
            r.set(3f, 12f, 10f, 20f)
            cv.drawRoundRect(r, 1.8f, 1.8f, p)
            r.set(12f, 12f, 21f, 15.5f)
            cv.drawRoundRect(r, 1.6f, 1.6f, p)
            r.set(12f, 16.5f, 21f, 20f)
            cv.drawRoundRect(r, 1.6f, 1.6f, p)
        }
        cv.restore()
    }
}

class BadgeLayer(c: Context, private val desk: Desk) : View(c) {
    val picked = HashSet<DeskItem>()
    var onChange: () -> Unit = {}
    private val d = c.resources.displayMetrics.density
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dim = Paint(Paint.ANTI_ALIAS_FLAG)
    private val on = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG)
    private var down: DeskItem? = null

    init {
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = 2f * d
        ring.color = Color.WHITE
        dim.color = 0x33000000
        on.color = 0xFF3478F6.toInt()
        tick.style = Paint.Style.STROKE
        tick.strokeWidth = 2.2f * d
        tick.strokeCap = Paint.Cap.ROUND
        tick.color = Color.WHITE
    }

    private fun spots(): List<Triple<DeskItem, Float, Float>> {
        val me = IntArray(2)
        getLocationOnScreen(me)
        val out = ArrayList<Triple<DeskItem, Float, Float>>()
        for (i in 0 until desk.childCount) {
            val v = desk.getChildAt(i)
            val item = v.tag as? DeskItem ?: continue
            if (v.visibility != VISIBLE || v !is ViewGroup || v.childCount == 0) continue
            val ic = v.getChildAt(0)
            val a = IntArray(2)
            ic.getLocationOnScreen(a)
            val x = a[0] - me[0] + ic.width - 4f * d
            val y = a[1] - me[1] + 4f * d
            if (x < 0f || x > width) continue
            out.add(Triple(item, x, y))
        }
        return out
    }

    override fun onDraw(cv: Canvas) {
        picked.retainAll(desk.items.toSet())
        for (s in spots()) {
            val x = s.second
            val y = s.third
            if (s.first in picked) {
                cv.drawCircle(x, y, 10f * d, on)
                cv.drawLine(x - 4f * d, y, x - 1.2f * d, y + 3f * d, tick)
                cv.drawLine(x - 1.2f * d, y + 3f * d, x + 4.2f * d, y - 3f * d, tick)
            } else {
                cv.drawCircle(x, y, 10f * d, dim)
                cv.drawCircle(x, y, 10f * d, ring)
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                down = null
                for (s in spots()) {
                    if (hypot(e.x - s.second, e.y - s.third) < 22f * d) {
                        down = s.first
                        break
                    }
                }
                return down != null
            }
            MotionEvent.ACTION_UP -> {
                val item = down
                down = null
                if (item != null) {
                    if (!picked.remove(item)) picked.add(item)
                    invalidate()
                    onChange()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                down = null
                return true
            }
        }
        return down != null
    }
}

class EditOverlay(c: Context, desk: Desk) : FrameLayout(c) {
    private val d = c.resources.displayMetrics.density
    val badges = BadgeLayer(c, desk)
    var onDone: () -> Unit = {}
    var onRemove: () -> Unit = {}
    var onWall: () -> Unit = {}
    var onHome: () -> Unit = {}
    var onWidgets: () -> Unit = {}
    var onSettings: () -> Unit = {}
    private val dots = PageDots(c)
    private val done = TextView(c)
    private val remove = TextView(c)
    private val capsule = FrameLayout(c)
    private val column = LinearLayout(c)

    private fun shape(color: Int, r: Float, stroke: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = r * d
        if (stroke != 0) g.setStroke((1.2f * d).toInt(), stroke)
        return g
    }

    private fun button(glyph: View, label: String, tap: () -> Unit): View {
        val box = LinearLayout(context)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        val circle = FrameLayout(context)
        circle.background = shape(0x33FFFFFF, 32f, 0x55FFFFFF)
        circle.addView(glyph, FrameLayout.LayoutParams((28 * d).toInt(), (28 * d).toInt(), Gravity.CENTER))
        box.addView(circle, LinearLayout.LayoutParams((64 * d).toInt(), (64 * d).toInt()))
        val t = TextView(context)
        t.text = label
        t.setTextColor(Color.WHITE)
        t.textSize = 14f
        t.gravity = Gravity.CENTER
        t.setShadowLayer(4f, 0f, 1f, 0x66000000)
        val lp = LinearLayout.LayoutParams(-2, -2)
        lp.topMargin = (8 * d).toInt()
        box.addView(t, lp)
        box.setOnClickListener { tap() }
        return box
    }

    init {
        visibility = GONE
        addView(badges, FrameLayout.LayoutParams(-1, -1))

        done.text = L(c, "Готово", "Done")
        done.setTextColor(Color.WHITE)
        done.textSize = 19f
        done.typeface = Typeface.DEFAULT_BOLD
        done.setPadding((30 * d).toInt(), (14 * d).toInt(), (30 * d).toInt(), (14 * d).toInt())
        done.background = shape(0xFF3478F6.toInt(), 30f)
        done.setOnClickListener { onDone() }
        val dl = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END)
        dl.topMargin = (60 * d).toInt()
        dl.rightMargin = (20 * d).toInt()
        addView(done, dl)

        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER_HORIZONTAL
        remove.setTextColor(Color.WHITE)
        remove.textSize = 16f
        remove.typeface = Typeface.DEFAULT_BOLD
        remove.setPadding((24 * d).toInt(), (10 * d).toInt(), (24 * d).toInt(), (10 * d).toInt())
        remove.background = shape(0xFFD93025.toInt(), 22f)
        remove.visibility = GONE
        remove.setOnClickListener { onRemove() }
        val rl = LinearLayout.LayoutParams(-2, -2)
        rl.bottomMargin = (18 * d).toInt()
        column.addView(remove, rl)

        capsule.background = shape(0x33FFFFFF, 15f, 0x44FFFFFF)
        capsule.addView(dots, FrameLayout.LayoutParams(-1, -1))
        val cl = LinearLayout.LayoutParams((110 * d).toInt(), (30 * d).toInt())
        cl.bottomMargin = (28 * d).toInt()
        column.addView(capsule, cl)

        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(button(GlyphView(c, "image", Color.WHITE), L(c, "Обои", "Wallpaper")) { onWall() }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button(GlyphView(c, "home", Color.WHITE), L(c, "Главная", "Home")) { onHome() }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button(EditGlyph(c, "widgets"), L(c, "Виджеты", "Widgets")) { onWidgets() }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button(EditGlyph(c, "gear"), L(c, "Настройки", "Settings")) { onSettings() }, LinearLayout.LayoutParams(0, -2, 1f))
        column.addView(row, LinearLayout.LayoutParams(-1, -2))
        column.setPadding(0, 0, 0, (24 * d).toInt())
        addView(column, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    }

    fun setInsets(top: Int, bottom: Int) {
        (done.layoutParams as FrameLayout.LayoutParams).topMargin = top + (28 * d).toInt()
        done.requestLayout()
        column.setPadding(0, 0, 0, bottom + (24 * d).toInt())
    }

    fun page(n: Int, f: Float) {
        dots.set(n, f)
        capsule.visibility = if (n > 1) View.VISIBLE else View.INVISIBLE
        val lp = capsule.layoutParams as LinearLayout.LayoutParams
        val w = ((36 + 20 * n) * d).toInt()
        if (lp.width != w) {
            lp.width = w
            capsule.layoutParams = lp
        }
    }

    fun updateRemove(n: Int, allowed: Boolean) {
        remove.visibility = if (n > 0 && allowed) View.VISIBLE else View.GONE
        remove.text = L(context, "Убрать с экрана", "Remove from screen") + " (" + n + ")"
    }

    fun show() {
        animate().cancel()
        visibility = View.VISIBLE
        alpha = 0f
        animate().alpha(1f).setDuration(220).start()
        column.translationY = 80f * d
        column.animate().translationY(0f).setDuration(320).setInterpolator(curveOf(1)).start()
    }

    fun hide() {
        animate().cancel()
        animate().alpha(0f).setDuration(180).withEndAction { visibility = View.GONE }.start()
    }
}
