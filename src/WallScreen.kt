package com.example.pixellauncher

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.WallpaperManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.min

/** Три точки вертикально (кнопка "ещё"). */
class DotsIcon(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(cv: Canvas) {
        p.color = Color.WHITE
        val cx = width / 2f
        val r = width * 0.065f
        for (i in -1..1) cv.drawCircle(cx, height / 2f + i * width * 0.22f, r, p)
    }
}

/** Квадратная картинка для сетки фото. */
class SquareImage(c: Context) : ImageView(c) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}

/** Карусель карточек с прилипанием к центру. */
class WallPager(c: Context) : HorizontalScrollView(c) {
    val row = LinearLayout(c)
    var step = 0
    var onIndex: (Int) -> Unit = {}
    private var last = -1
    private var flung = false

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        row.orientation = LinearLayout.HORIZONTAL
        addView(row, LayoutParams(-2, -1))
    }

    fun count(): Int = row.childCount

    fun nearest(): Int = if (step <= 0) 0 else Math.round(scrollX / step.toFloat()).coerceIn(0, maxOf(0, count() - 1))

    fun goTo(i: Int) {
        smoothScrollTo(i * step, 0)
    }

    override fun onScrollChanged(l: Int, t: Int, ol: Int, ot: Int) {
        super.onScrollChanged(l, t, ol, ot)
        for (i in 0 until count()) {
            val v = row.getChildAt(i)
            val m = if (step > 0) min(abs(l - i * step) / step.toFloat(), 1f) else 0f
            v.scaleX = 1f - 0.08f * m
            v.scaleY = 1f - 0.08f * m
            v.alpha = 1f - 0.25f * m
        }
        val n = nearest()
        if (n != last) {
            last = n
            onIndex(n)
        }
    }

    override fun fling(velocityX: Int) {
        flung = true
        val cur = nearest()
        val t = when {
            velocityX > 700 -> cur + 1
            velocityX < -700 -> cur - 1
            else -> cur
        }.coerceIn(0, maxOf(0, count() - 1))
        smoothScrollTo(t * step, 0)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) flung = false
        val r = super.onTouchEvent(e)
        if ((e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) && !flung) {
            smoothScrollTo(nearest() * step, 0)
        }
        return r
    }
}

/**
 * Экран выбора обоев, как в системном выборе на Pixel: карусель превью, кнопка "+"
 * раскрывается в лист "Фото" с сеткой снимков, под каруселью кнопка действия.
 */
class WallScreen(private val a: SettingsActivity) : FrameLayout(a) {
    private val d = a.resources.displayMetrics.density
    private val wm = WallpaperManager.getInstance(a)
    private val pool = Executors.newFixedThreadPool(3)
    private val column = LinearLayout(a)
    private val pager = WallPager(a)
    private val pill = TextView(a)
    private var currentImg: ImageView? = null
    private var fab: View? = null
    private var sheet: View? = null
    private var gridHost: LinearLayout? = null
    private var custom: WallCustom? = null
    private var inT = 0
    private var inB = 0

    private val presets = listOf(
        intArrayOf(0xFFB8D8A4.toInt(), 0xFF5E9B6B.toInt(), 0xFF1F4D3A.toInt()),
        intArrayOf(0xFF9CC9F0.toInt(), 0xFF3B7DD8.toInt(), 0xFF152A5E.toInt()),
        intArrayOf(0xFFFFC38B.toInt(), 0xFFE8627A.toInt(), 0xFF4A2A6B.toInt()),
        intArrayOf(0xFFD4C2F5.toInt(), 0xFF8A6CE0.toInt(), 0xFF2D1B69.toInt()),
        intArrayOf(0xFF8A8D94.toInt(), 0xFF3C3F46.toInt(), 0xFF121316.toInt()),
        intArrayOf(0xFFFFD1DC.toInt(), 0xFFE58AA6.toInt(), 0xFF6B2740.toInt())
    )

    private fun dp(v: Int): Int = (v * d).toInt()

    private fun photoPerm(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasPerm(): Boolean = a.checkSelfPermission(photoPerm()) == PackageManager.PERMISSION_GRANTED

    init {
        setBackgroundColor(0xFF0F1114.toInt())
        isClickable = true
        column.orientation = LinearLayout.VERTICAL
        addView(column, LayoutParams(-1, -1))
        buildTopBar()
        buildPager()
        buildPill()
        refresh()
    }

    @Suppress("DEPRECATION")
    override fun onApplyWindowInsets(i: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= 30) {
            val s = i.getInsets(WindowInsets.Type.systemBars())
            inT = s.top
            inB = s.bottom
        } else {
            inT = i.systemWindowInsetTop
            inB = i.systemWindowInsetBottom
        }
        column.setPadding(0, inT, 0, inB)
        return super.onApplyWindowInsets(i)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestApplyInsets()
    }

    private fun circle(content: View, click: () -> Unit): FrameLayout {
        val f = FrameLayout(a)
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(0x33FFFFFF)
        g.setStroke(dp(1), 0x40FFFFFF)
        f.background = g
        f.addView(content, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        f.setOnClickListener { click() }
        return f
    }

    private fun buildTopBar() {
        val bar = LinearLayout(a)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(16), dp(10), dp(16), dp(10))
        bar.addView(circle(GlyphView(a, "back", Color.WHITE)) { a.finish() }, LinearLayout.LayoutParams(dp(44), dp(44)))
        val t = TextView(a)
        t.text = L(a, "Обои", "Wallpaper")
        t.setTextColor(0xFFE3E3E3.toInt())
        t.textSize = 20f
        t.setPadding(dp(18), 0, 0, 0)
        bar.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        val more = circle(DotsIcon(a)) { }
        more.setOnClickListener { menu(more) }
        bar.addView(more, LinearLayout.LayoutParams(dp(44), dp(44)))
        column.addView(bar, LinearLayout.LayoutParams(-1, -2))
    }

    private fun menu(anchor: View) {
        val pm = PopupMenu(a, anchor)
        pm.menu.add(0, 1, 0, L(a, "Выбрать из галереи", "Choose from gallery"))
        pm.menu.add(0, 2, 1, L(a, "Сбросить обои", "Reset wallpaper"))
        pm.setOnMenuItemClickListener {
            if (it.itemId == 1) {
                a.pickWallpaper()
            } else {
                try {
                    wm.clear()
                } catch (e: Exception) {
                }
                refresh()
            }
            true
        }
        pm.show()
    }

    private fun rounded(v: View, r: Float) {
        v.clipToOutline = true
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, o: Outline) {
                o.setRoundRect(0, 0, view.width, view.height, r)
            }
        }
    }

    private fun buildPager() {
        val dm = a.resources.displayMetrics
        val aspect = dm.heightPixels.toFloat() / dm.widthPixels
        var cw = dm.widthPixels * 0.59f
        var ch = cw * aspect
        val maxH = dm.heightPixels * 0.56f
        if (ch > maxH) {
            ch = maxH
            cw = ch / aspect
        }
        val step = (cw + dp(14)).toInt()
        pager.step = step
        val pad = ((dm.widthPixels - step) / 2f).toInt()
        pager.row.setPadding(pad, 0, pad, 0)
        val radius = dp(28).toFloat()

        // 0: добавить фото ("+")
        val addCard = FrameLayout(a)
        addCard.setBackgroundColor(0xFF24262B.toInt())
        rounded(addCard, radius)
        val plus = TextView(a)
        plus.text = "+"
        plus.setTextColor(Color.WHITE)
        plus.textSize = 30f
        plus.gravity = Gravity.CENTER
        val fg = GradientDrawable()
        fg.shape = GradientDrawable.OVAL
        fg.setColor(0xFF3478F6.toInt())
        plus.background = fg
        plus.setOnClickListener { showSheet() }
        addCard.addView(plus, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
        fab = plus
        cell(addCard, step, cw.toInt(), ch.toInt(), 0)

        // 1: текущие обои с синей рамкой
        val cur = FrameLayout(a)
        cur.setBackgroundColor(0xFF24262B.toInt())
        rounded(cur, radius)
        val img = ImageView(a)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        cur.addView(img, FrameLayout.LayoutParams(-1, -1))
        val ring = View(a)
        val rg = GradientDrawable()
        rg.cornerRadius = radius
        rg.setColor(Color.TRANSPARENT)
        rg.setStroke(dp(3), 0xFF2D6BFF.toInt())
        ring.background = rg
        cur.addView(ring, FrameLayout.LayoutParams(-1, -1))
        currentImg = img
        cell(cur, step, cw.toInt(), ch.toInt(), 1)

        // 2..: готовые обои
        for (i in presets.indices) {
            val f = FrameLayout(a)
            val iv = ImageView(a)
            iv.setImageDrawable(presetDrawable(i))
            f.addView(iv, FrameLayout.LayoutParams(-1, -1))
            rounded(f, radius)
            cell(f, step, cw.toInt(), ch.toInt(), 2 + i)
        }
        pager.onIndex = { updatePill(it) }
        val pl = LinearLayout.LayoutParams(-1, 0, 1f)
        column.addView(pager, pl)
    }

    private fun cell(card: View, step: Int, cw: Int, ch: Int, index: Int) {
        val c = FrameLayout(a)
        val lp = LayoutParams(cw, ch, Gravity.CENTER)
        c.addView(card, lp)
        card.setOnClickListener {
            if (pager.nearest() != index) pager.goTo(index) else action(index)
        }
        pager.row.addView(c, LinearLayout.LayoutParams(step, -1))
    }

    private fun presetDrawable(i: Int): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, presets[i])

    private fun buildPill() {
        pill.textSize = 16f
        pill.setTextColor(Color.WHITE)
        pill.typeface = Typeface.DEFAULT_BOLD
        pill.gravity = Gravity.CENTER
        val g = GradientDrawable()
        g.cornerRadius = dp(24).toFloat()
        g.setColor(0x33FFFFFF)
        g.setStroke(dp(1), 0x40FFFFFF)
        pill.background = g
        pill.setOnClickListener { action(pager.nearest()) }
        val lp = LinearLayout.LayoutParams(dp(210), dp(46))
        lp.gravity = Gravity.CENTER_HORIZONTAL
        lp.topMargin = dp(18)
        lp.bottomMargin = dp(26)
        column.addView(pill, lp)
        updatePill(0)
    }

    private fun updatePill(i: Int) {
        pill.text = when (i) {
            0 -> L(a, "Выбрать фото", "Choose photo")
            1 -> L(a, "Настроить", "Customize")
            else -> L(a, "Применить", "Apply")
        }
    }

    private fun action(i: Int) {
        when (i) {
            0 -> showSheet()
            1 -> customize()
            else -> applyPreset(i - 2)
        }
    }

    // ---- обои ----
    fun refresh() {
        val iv = currentImg ?: return
        Thread {
            val dr = try {
                wm.drawable
            } catch (e: Exception) {
                null
            }
            a.runOnUiThread { if (dr != null) iv.setImageDrawable(dr) }
        }.start()
    }

    private fun applyPreset(i: Int) {
        val dm = a.resources.displayMetrics
        val bw = 720
        val bh = (720f * dm.heightPixels / dm.widthPixels).toInt()
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val g = presetDrawable(i)
        g.setBounds(0, 0, bw, bh)
        g.draw(Canvas(bmp))
        Thread {
            var ok = true
            try {
                wm.setBitmap(bmp)
            } catch (e: Exception) {
                ok = false
            }
            a.runOnUiThread {
                Toast.makeText(a, if (ok) L(a, "Обои установлены", "Wallpaper set") else L(a, "Не удалось установить обои", "Could not set wallpaper"),
                    Toast.LENGTH_SHORT).show()
                if (ok) {
                    refresh()
                    pager.goTo(1)
                }
            }
        }.start()
    }

    private fun pickPhoto(uri: Uri) {
        applyWallpaper(a, uri)
        closeSheet(true)
        postDelayed({ refresh() }, 600)
        pager.goTo(1)
    }

    // ---- лист "Фото" ----
    private fun lerp(x: Float, y: Float, p: Float) = x + (y - x) * p

    fun showSheet() {
        if (sheet != null) return
        val w = width
        val h = height
        if (w == 0 || h == 0) return
        val sh = (h * 0.66f).toInt()
        val sw = w - dp(28)
        val box = LinearLayout(a)
        box.orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xF0B9BBC1.toInt(), 0xF0979AA0.toInt()))
        bg.cornerRadius = dp(36).toFloat()
        box.background = bg
        box.isClickable = true

        val head = LinearLayout(a)
        head.orientation = LinearLayout.VERTICAL
        head.gravity = Gravity.CENTER_HORIZONTAL
        head.setPadding(0, dp(10), 0, dp(14))
        val handle = View(a)
        val hg = GradientDrawable()
        hg.setColor(0x66FFFFFF)
        hg.cornerRadius = dp(2).toFloat()
        handle.background = hg
        head.addView(handle, LinearLayout.LayoutParams(dp(36), dp(4)))
        val title = TextView(a)
        title.text = L(a, "Фото", "Photos")
        title.setTextColor(0xFF22252B.toInt())
        title.textSize = 20f
        title.setPadding(0, dp(14), 0, 0)
        head.addView(title)
        box.addView(head, LinearLayout.LayoutParams(-1, -2))

        val sv = ScrollView(a)
        val host = LinearLayout(a)
        host.orientation = LinearLayout.VERTICAL
        host.setPadding(dp(10), 0, dp(10), dp(14))
        sv.addView(host)
        box.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        gridHost = host
        fillGrid()

        val lp = LayoutParams(sw, sh, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        lp.bottomMargin = inB + dp(10)
        addView(box, lp)
        sheet = box

        // тянуть вниз за шапку, чтобы закрыть
        var y0 = 0f
        head.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    y0 = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    box.translationY = maxOf(0f, e.rawY - y0)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (box.translationY > dp(100)) {
                        closeSheet(false)
                    } else {
                        box.animate().translationY(0f).setDuration(180).start()
                    }
                    true
                }
                else -> false
            }
        }

        // раскрытие из кнопки "+"
        val f = fab
        box.post {
            val fl = IntArray(2)
            val rl = IntArray(2)
            f?.getLocationOnScreen(fl)
            getLocationOnScreen(rl)
            val fcx = if (f != null) fl[0] - rl[0] + f.width / 2f else w / 2f
            val fcy = if (f != null) fl[1] - rl[1] + f.height / 2f else h / 2f
            val scx = w / 2f
            val scy = h - inB - dp(10) - sh / 2f
            val sx0 = (f?.width ?: dp(56)) / sw.toFloat()
            val sy0 = (f?.height ?: dp(56)) / sh.toFloat()
            f?.alpha = 0f
            val ev = ArgbEvaluator()
            val va = ValueAnimator.ofFloat(0f, 1f)
            va.duration = 300
            va.interpolator = DecelerateInterpolator(1.5f)
            va.addUpdateListener {
                val p = it.animatedValue as Float
                box.scaleX = lerp(sx0, 1f, p)
                box.scaleY = lerp(sy0, 1f, p)
                box.translationX = (fcx - scx) * (1f - p)
                box.translationY = (fcy - scy) * (1f - p)
                val t = (p / 0.6f).coerceIn(0f, 1f)
                bg.colors = intArrayOf(ev.evaluate(t, 0xFF3478F6.toInt(), 0xF0B9BBC1.toInt()) as Int,
                    ev.evaluate(t, 0xFF3478F6.toInt(), 0xF0979AA0.toInt()) as Int)
                bg.cornerRadius = dp(lerp(28f, 36f, p).toInt()).toFloat()
                val ca = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
                head.alpha = ca
                sv.alpha = ca
            }
            va.start()
        }
    }

    // сворачивает лист обратно в кнопку "+" (или вниз, если его утащили)
    private fun closeSheet(toFab: Boolean) {
        val box = sheet ?: return
        sheet = null
        gridHost = null
        val f = fab
        if (toFab && f != null) {
            val w = width
            val h = height
            val sh = box.height
            val sw = box.width
            val fl = IntArray(2)
            val rl = IntArray(2)
            f.getLocationOnScreen(fl)
            getLocationOnScreen(rl)
            val fcx = fl[0] - rl[0] + f.width / 2f
            val fcy = fl[1] - rl[1] + f.height / 2f
            val scy = h - inB - dp(10) - sh / 2f
            val sx0 = f.width / sw.toFloat()
            val sy0 = f.height / sh.toFloat()
            val va = ValueAnimator.ofFloat(1f, 0f)
            va.duration = 220
            va.interpolator = AccelerateInterpolator(1.2f)
            va.addUpdateListener {
                val p = it.animatedValue as Float
                box.scaleX = lerp(sx0, 1f, p)
                box.scaleY = lerp(sy0, 1f, p)
                box.translationX = (fcx - w / 2f) * (1f - p)
                box.translationY = (fcy - scy) * (1f - p)
                box.alpha = ((p - 0.05f) / 0.5f).coerceIn(0f, 1f)
            }
            va.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(x: android.animation.Animator) {
                    removeView(box)
                    f.alpha = 1f
                }
            })
            va.start()
        } else {
            box.animate().translationY(height.toFloat()).alpha(0f).setDuration(200).withEndAction {
                removeView(box)
                f?.alpha = 1f
            }.start()
        }
    }

    private fun customize() {
        if (custom != null) return
        val v = WallCustom(a, inT, inB) { applied ->
            val c = custom
            custom = null
            if (c != null) removeView(c)
            if (applied) refresh()
        }
        addView(v, LayoutParams(-1, -1))
        custom = v
    }

    fun onBack(): Boolean {
        val c = custom
        if (c != null) {
            custom = null
            removeView(c)
            return true
        }
        if (sheet != null) {
            closeSheet(true)
            return true
        }
        return false
    }

    fun onPermission() {
        fillGrid()
    }

    private fun fillGrid() {
        val host = gridHost ?: return
        host.removeAllViews()
        if (!hasPerm()) {
            val t = TextView(a)
            t.text = L(a, "Разрешите доступ к фото, чтобы выбрать снимок для обоев", "Allow photo access to choose a wallpaper")
            t.setTextColor(0xFF22252B.toInt())
            t.textSize = 16f
            t.gravity = Gravity.CENTER
            t.setPadding(dp(16), dp(24), dp(16), dp(12))
            host.addView(t)
            host.addView(sheetButton(L(a, "Разрешить", "Allow")) {
                a.requestPermissions(arrayOf(photoPerm()), REQ)
            })
            host.addView(sheetButton(L(a, "Открыть галерею", "Open gallery")) { a.pickWallpaper() })
            return
        }
        val uris = ArrayList<Uri>()
        try {
            val base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            a.contentResolver.query(base, arrayOf(MediaStore.Images.Media._ID), null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC")?.use {
                while (it.moveToNext() && uris.size < 90) uris.add(ContentUris.withAppendedId(base, it.getLong(0)))
            }
        } catch (e: Exception) {
        }
        if (uris.isEmpty()) {
            val t = TextView(a)
            t.text = L(a, "Фото не найдены", "No photos found")
            t.setTextColor(0xFF22252B.toInt())
            t.gravity = Gravity.CENTER
            t.setPadding(0, dp(24), 0, dp(12))
            host.addView(t)
            host.addView(sheetButton(L(a, "Открыть галерею", "Open gallery")) { a.pickWallpaper() })
            return
        }
        var row: LinearLayout? = null
        for (i in uris.indices) {
            if (i % 3 == 0) {
                row = LinearLayout(a)
                row.orientation = LinearLayout.HORIZONTAL
                host.addView(row, LinearLayout.LayoutParams(-1, -2))
            }
            val iv = SquareImage(a)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setBackgroundColor(0x22000000)
            rounded(iv, dp(18).toFloat())
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.setMargins(dp(4), dp(4), dp(4), dp(4))
            iv.setOnClickListener { pickPhoto(uris[i]) }
            row?.addView(iv, lp)
            loadThumb(uris[i], iv)
        }
    }

    private fun sheetButton(text: String, click: () -> Unit): TextView {
        val t = TextView(a)
        t.text = text
        t.setTextColor(Color.WHITE)
        t.textSize = 16f
        t.typeface = Typeface.DEFAULT_BOLD
        t.gravity = Gravity.CENTER
        val g = GradientDrawable()
        g.cornerRadius = dp(24).toFloat()
        g.setColor(0xFF3478F6.toInt())
        t.background = g
        t.setOnClickListener { click() }
        val lp = LinearLayout.LayoutParams(-1, dp(48))
        lp.setMargins(dp(12), dp(10), dp(12), 0)
        t.layoutParams = lp
        return t
    }

    private fun loadThumb(uri: Uri, iv: ImageView) {
        pool.execute {
            val bmp: Bitmap? = try {
                if (Build.VERSION.SDK_INT >= 29) a.contentResolver.loadThumbnail(uri, Size(320, 320), null)
                else MediaStore.Images.Media.getBitmap(a.contentResolver, uri)
            } catch (e: Exception) {
                null
            }
            if (bmp != null) iv.post { iv.setImageBitmap(bmp) }
        }
    }

    companion object {
        const val REQ = 77
    }
}
