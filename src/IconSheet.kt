package com.example.pixellauncher

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/** Значок "развернуть" справа в шапке листа: тёмный квадрат с уголками. */
class CornerIcon(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val d = c.resources.displayMetrics.density

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        bg.color = 0xFF1B1C20.toInt()
        r.set(0f, 0f, w, h)
        cv.drawRoundRect(r, 9f * d, 9f * d, bg)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * d
        p.color = Color.WHITE
        r.set(w * 0.28f, h * 0.28f, w * 0.72f, h * 0.72f)
        cv.drawRoundRect(r, 3f * d, 3f * d, p)
    }
}

/** Переключатель из нескольких сегментов в тёмной "дорожке". */
class Segmented(c: Context, items: List<String>, sel: Int, private val onSel: (Int) -> Unit) : LinearLayout(c) {
    private val views = ArrayList<TextView>()
    private val d = c.resources.displayMetrics.density
    private var cur = sel

    init {
        orientation = HORIZONTAL
        val tr = GradientDrawable()
        tr.setColor(0x66000000)
        tr.cornerRadius = 22f * d
        background = tr
        setPadding((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
        for (i in items.indices) {
            val t = TextView(c)
            t.text = items[i]
            t.gravity = Gravity.CENTER
            t.textSize = 15f
            t.setOnClickListener {
                set(i)
                onSel(i)
            }
            addView(t, LayoutParams(0, (36 * d).toInt(), 1f))
            views.add(t)
        }
        paint()
    }

    fun set(i: Int) {
        cur = i
        paint()
    }

    private fun paint() {
        for (i in views.indices) {
            val t = views[i]
            if (i == cur) {
                val g = GradientDrawable()
                g.setColor(0xFF8E9197.toInt())
                g.cornerRadius = 18f * d
                t.background = g
                t.setTextColor(Color.WHITE)
                t.typeface = Typeface.DEFAULT_BOLD
            } else {
                t.background = null
                t.setTextColor(0xFFB5B8BE.toInt())
                t.typeface = Typeface.DEFAULT
            }
        }
    }
}

/**
 * Лист "Значки" (как в видео): стиль значков, оттенок, размер и "Больше настроек".
 * Меняет настройки сразу, главный экран за листом обновляется живьём.
 * Лист можно потянуть вниз за шапку: остаётся только заголовок.
 */
class IconSheet(
    private val a: Activity,
    private val p: Prefs,
    private val raw: Drawable,
    private val inB: Int,
    private val onBack: () -> Unit,
    private val onMore: () -> Unit
) : LinearLayout(a) {
    private val slop = ViewConfiguration.get(a).scaledTouchSlop
    private val styleIds = intArrayOf(IconTheme.OFF, IconTheme.DARK, IconTheme.CLEAR, IconTheme.WALLPAPER)
    private val sizes = intArrayOf(48, 56, 64, 72)
    private val tiles = ArrayList<FrameLayout>()
    private val labels = ArrayList<TextView>()
    private var tone: Segmented? = null
    private var dragging = false
    private var startInHeader = false
    private var dy0 = 0f
    private var base = 0f
    private var lastDown = false
    private var collapsed = false

    fun params(): FrameLayout.LayoutParams {
        val lp = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        lp.setMargins(a.dp(12), 0, a.dp(12), inB + a.dp(10))
        return lp
    }

    private fun styleIndex(): Int = when (p.iconStyle) {
        IconTheme.OFF -> 0
        IconTheme.DARK -> 1
        IconTheme.CLEAR -> 2
        else -> 3
    }

    init {
        orientation = VERTICAL
        val g = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xEE30343B.toInt(), 0xEE1E2126.toInt()))
        g.cornerRadius = a.dp(36).toFloat()
        g.setStroke(a.dp(1), 0x33FFFFFF)
        background = g
        isClickable = true
        setPadding(a.dp(20), a.dp(10), a.dp(20), a.dp(18))

        val handle = View(a)
        val hg = GradientDrawable()
        hg.setColor(0x66FFFFFF)
        hg.cornerRadius = a.dp(2).toFloat()
        handle.background = hg
        val hl = LayoutParams(a.dp(36), a.dp(4))
        hl.gravity = Gravity.CENTER_HORIZONTAL
        addView(handle, hl)

        val head = LinearLayout(a)
        head.orientation = HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(0, a.dp(14), 0, a.dp(14))
        val back = GlyphView(a, "back", Color.WHITE)
        back.setOnClickListener { onBack() }
        head.addView(back, LayoutParams(a.dp(26), a.dp(26)))
        val title = TextView(a)
        title.text = L(a, "Значки", "App Icons")
        title.setTextColor(Color.WHITE)
        title.textSize = 20f
        title.setPadding(a.dp(12), 0, 0, 0)
        title.setOnClickListener { toggle() }
        head.addView(title, LayoutParams(0, -2, 1f))
        val more = CornerIcon(a)
        more.setOnClickListener { onMore() }
        head.addView(more, LayoutParams(a.dp(34), a.dp(34)))
        addView(head, LayoutParams(-1, -2))

        val row = LinearLayout(a)
        row.orientation = HORIZONTAL
        val names = listOf(L(a, "Цветные", "Colourful"), L(a, "Тёмные", "Dark"), L(a, "Прозрачные", "Clear"), L(a, "С оттенком", "Tinted"))
        for (i in 0 until 4) {
            val cell = LinearLayout(a)
            cell.orientation = VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            val tile = FrameLayout(a)
            fillTile(tile, i)
            cell.addView(tile, LayoutParams(a.dp(64), a.dp(64)))
            val lb = TextView(a)
            lb.text = names[i]
            lb.textSize = 14f
            lb.gravity = Gravity.CENTER
            lb.setPadding(0, a.dp(6), 0, 0)
            cell.addView(lb)
            cell.setOnClickListener { chooseStyle(i) }
            tiles.add(tile)
            labels.add(lb)
            row.addView(cell, LayoutParams(0, -2, 1f))
        }
        addView(row, LayoutParams(-1, -2))

        val toneSel = when (p.iconTone) {
            1 -> 0
            2 -> 1
            else -> 2
        }
        val t = Segmented(a, listOf(L(a, "Светлые", "Light"), L(a, "Тёмные", "Dark"), L(a, "Авто", "Auto")), toneSel) {
            p.iconTone = intArrayOf(1, 2, 0)[it]
            for (k in 0 until 4) fillTile(tiles[k], k)
        }
        tone = t
        val tl = LayoutParams(-1, -2)
        tl.topMargin = a.dp(20)
        addView(t, tl)

        var sizeSel = 1
        var best = 999
        for (k in sizes.indices) {
            val dd = abs(sizes[k] - p.iconSize)
            if (dd < best) {
                best = dd
                sizeSel = k
            }
        }
        val s = Segmented(a, listOf(L(a, "Мелкие", "Small"), L(a, "Средние", "Medium"), L(a, "Крупные", "Large"), L(a, "Огромные", "Huge")), sizeSel) {
            p.iconSize = sizes[it]
        }
        val sl = LayoutParams(-1, -2)
        sl.topMargin = a.dp(12)
        addView(s, sl)

        val moreText = TextView(a)
        moreText.text = L(a, "Больше настроек…", "More Options…")
        moreText.setTextColor(0xFF4C8DFF.toInt())
        moreText.textSize = 17f
        moreText.gravity = Gravity.CENTER
        moreText.setPadding(0, a.dp(22), 0, a.dp(4))
        moreText.setOnClickListener { onMore() }
        addView(moreText, LayoutParams(-1, -2))

        refresh()
        translationY = 3000f
        post {
            translationY = (height + a.dp(40)).toFloat()
            animate().translationY(0f).setDuration(280).setInterpolator(DecelerateInterpolator(1.6f)).start()
        }
    }

    // превью значка в выбранном стиле (рисуется тем же кодом, что и значки на экране)
    private fun fillTile(tile: FrameLayout, i: Int) {
        tile.removeAllViews()
        val d = IconTheme.apply(a, raw, styleIds[i], p.iconSeed, true)
        val icon = ShapedIcon(a, d, p.shape)
        val lp = FrameLayout.LayoutParams(a.dp(56), a.dp(56), Gravity.CENTER)
        tile.addView(icon, lp)
    }

    private fun chooseStyle(i: Int) {
        p.iconStyle = styleIds[i]
        if (styleIds[i] != IconTheme.OFF) p.iconAll = 1
        refresh()
    }

    private fun refresh() {
        val sel = styleIndex()
        for (i in 0 until 4) {
            val ring = GradientDrawable()
            ring.cornerRadius = a.dp(20).toFloat()
            ring.setColor(Color.TRANSPARENT)
            if (i == sel) ring.setStroke(a.dp(3), 0xFF7F8CFF.toInt())
            tiles[i].background = ring
            labels[i].setTextColor(if (i == sel) Color.WHITE else 0xFFC9CCD2.toInt())
            labels[i].typeface = if (i == sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        tone?.alpha = if (p.iconStyle == IconTheme.OFF || p.iconStyle == IconTheme.DARK) 0.4f else 1f
    }

    private fun collapsedY(): Float = (height - a.dp(76)).toFloat()

    private fun toggle() {
        collapsed = !collapsed
        animate().translationY(if (collapsed) collapsedY() else 0f).setDuration(240)
            .setInterpolator(DecelerateInterpolator(1.6f)).start()
    }

    fun close(end: () -> Unit) {
        animate().translationY((height + a.dp(40)).toFloat()).setDuration(200).withEndAction(end).start()
    }

    // тянуть лист можно за шапку
    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dy0 = e.rawY
                startInHeader = e.y < a.dp(76)
                base = translationY
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (startInHeader && !dragging && abs(e.rawY - dy0) > slop) {
                    dragging = true
                    animate().cancel()
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (dragging) {
                val ny = (base + e.rawY - dy0).coerceIn(0f, collapsedY())
                lastDown = ny > translationY
                translationY = ny
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                dragging = false
                val f = if (collapsedY() > 0f) translationY / collapsedY() else 0f
                collapsed = if (lastDown) f > 0.3f else f > 0.7f
                animate().translationY(if (collapsed) collapsedY() else 0f).setDuration(220)
                    .setInterpolator(DecelerateInterpolator(1.6f)).start()
            }
        }
        return dragging || super.onTouchEvent(e)
    }
}
