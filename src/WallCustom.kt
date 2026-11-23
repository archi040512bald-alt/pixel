package com.example.pixellauncher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Размытие через сжатие и растяжение: level 1 = мягкое, 2 = сильное. */
fun blurBitmap(src: Bitmap, level: Int): Bitmap {
    if (level <= 0) return src
    val div = if (level == 1) 10 else 26
    val w = maxOf(8, src.width / div)
    val h = maxOf(8, src.height / div)
    var b = Bitmap.createScaledBitmap(src, w, h, true)
    b = Bitmap.createScaledBitmap(b, w * 3, h * 3, true)
    return Bitmap.createScaledBitmap(b, src.width, src.height, true)
}

/** Обрезка по центру до пропорций w:h и масштаб до w x h. */
fun fitBitmap(src: Bitmap, w: Int, h: Int): Bitmap {
    val sr = src.width.toFloat() / src.height
    val tr = w.toFloat() / h
    val cw: Int
    val ch: Int
    if (sr > tr) {
        ch = src.height
        cw = (ch * tr).toInt()
    } else {
        cw = src.width
        ch = (cw / tr).toInt()
    }
    val c = Bitmap.createBitmap(src, (src.width - cw) / 2, (src.height - ch) / 2, cw, ch)
    return Bitmap.createScaledBitmap(c, w, h, true)
}

/** Иконка параметра: 0 = картинка, 1 = куб (3D), 2 = размытие. */
class OptIcon(c: Context, private val kind: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val d = c.resources.displayMetrics.density

    override fun onDraw(cv: Canvas) {
        p.color = Color.WHITE
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * d
        p.strokeJoin = Paint.Join.ROUND
        val w = width.toFloat()
        val h = height.toFloat()
        when (kind) {
            0 -> {
                p.style = Paint.Style.FILL
                r.set(w * 0.14f, h * 0.18f, w * 0.86f, h * 0.82f)
                cv.drawRoundRect(r, 4f * d, 4f * d, p)
                p.color = 0xFF6B6E75.toInt()
                cv.drawCircle(w * 0.36f, h * 0.38f, 2.6f * d, p)
                val path = android.graphics.Path()
                path.moveTo(w * 0.2f, h * 0.76f)
                path.lineTo(w * 0.42f, h * 0.52f)
                path.lineTo(w * 0.58f, h * 0.68f)
                path.lineTo(w * 0.7f, h * 0.56f)
                path.lineTo(w * 0.8f, h * 0.76f)
                path.close()
                cv.drawPath(path, p)
            }
            1 -> {
                val path = android.graphics.Path()
                path.moveTo(w * 0.5f, h * 0.1f)
                path.lineTo(w * 0.86f, h * 0.3f)
                path.lineTo(w * 0.86f, h * 0.7f)
                path.lineTo(w * 0.5f, h * 0.9f)
                path.lineTo(w * 0.14f, h * 0.7f)
                path.lineTo(w * 0.14f, h * 0.3f)
                path.close()
                cv.drawPath(path, p)
                path.reset()
                path.moveTo(w * 0.14f, h * 0.3f)
                path.lineTo(w * 0.5f, h * 0.5f)
                path.lineTo(w * 0.86f, h * 0.3f)
                path.moveTo(w * 0.5f, h * 0.5f)
                path.lineTo(w * 0.5f, h * 0.9f)
                cv.drawPath(path, p)
            }
            else -> {
                cv.drawCircle(w / 2f, h / 2f, w * 0.42f, p)
                cv.drawCircle(w / 2f, h / 2f, w * 0.27f, p)
                p.style = Paint.Style.FILL
                cv.drawCircle(w / 2f, h / 2f, w * 0.12f, p)
            }
        }
    }
}

class CheckIcon(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(cv: Canvas) {
        p.color = Color.WHITE
        p.style = Paint.Style.STROKE
        p.strokeWidth = width * 0.11f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        val path = android.graphics.Path()
        path.moveTo(width * 0.2f, height * 0.54f)
        path.lineTo(width * 0.42f, height * 0.74f)
        path.lineTo(width * 0.8f, height * 0.28f)
        cv.drawPath(path, p)
    }
}

/** Маленькие значки на превью экрана блокировки: 0 = фонарик, 1 = камера. */
class MiniGlyph(c: Context, private val kind: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(cv: Canvas) {
        p.color = Color.WHITE
        p.style = Paint.Style.FILL
        val w = width.toFloat()
        val h = height.toFloat()
        if (kind == 0) {
            r.set(w * 0.36f, h * 0.18f, w * 0.64f, h * 0.38f)
            cv.drawRoundRect(r, 2f, 2f, p)
            r.set(w * 0.4f, h * 0.38f, w * 0.6f, h * 0.84f)
            cv.drawRoundRect(r, 3f, 3f, p)
        } else {
            r.set(w * 0.16f, h * 0.3f, w * 0.84f, h * 0.78f)
            cv.drawRoundRect(r, 5f, 5f, p)
            p.color = 0xFF5F6368.toInt()
            cv.drawCircle(w / 2f, h * 0.54f, w * 0.15f, p)
        }
    }
}

/**
 * Настройка обоев (как в системном экране): две страницы "Экран блокировки" и "Рабочий стол",
 * снизу Изображение, 3D-эффект (только для экрана блокировки) и Размытие.
 */
class WallCustom(
    private val a: SettingsActivity,
    private val inT: Int,
    private val inB: Int,
    private val onDone: (Boolean) -> Unit
) : FrameLayout(a) {
    private class Tgt(var orig: Bitmap?, var blur: Int, var changed: Boolean)

    private val d = a.resources.displayMetrics.density
    private val dm = a.resources.displayMetrics
    private val wm = WallpaperManager.getInstance(a)
    private val prefs = Prefs(a)
    private val home = Tgt(null, prefs.homeBlur, false)
    private val lock = Tgt(null, prefs.lockBlur, false)
    private var lock3d = prefs.lock3d == 1
    private val backdrop = ImageView(a)
    private val title = TextView(a)
    private val pager = WallPager(a)
    private val previews = arrayOfNulls<ImageView>(2)
    private val homeLayer = FrameLayout(a)
    private var circleBlur: View? = null
    private var circle3d: View? = null
    private var blurLabel: TextView? = null
    private var label3d: TextView? = null
    private var page = 0

    private fun dp(v: Int): Int = (v * d).toInt()

    init {
        isClickable = true
        setBackgroundColor(0xFF1A1C1F.toInt())
        backdrop.scaleType = ImageView.ScaleType.CENTER_CROP
        addView(backdrop, LayoutParams(-1, -1))
        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(0, inT, 0, inB)
        addView(col, LayoutParams(-1, -1))
        col.addView(topBar(), LinearLayout.LayoutParams(-1, -2))
        buildPager()
        col.addView(pager, LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(options(), LinearLayout.LayoutParams(-1, -2))
        setPage(0)
        load()
    }

    private fun glass(radius: Float, fill: Int = 0x33FFFFFF, stroke: Int = 0x55FFFFFF): GradientDrawable {
        val g = GradientDrawable()
        g.cornerRadius = radius
        g.setColor(fill)
        g.setStroke(dp(1), stroke)
        return g
    }

    private fun topBar(): View {
        val bar = LinearLayout(a)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(16), dp(14), dp(16), dp(10))
        val cancel = TextView(a)
        cancel.text = L(a, "Отмена", "Cancel")
        cancel.setTextColor(Color.WHITE)
        cancel.textSize = 16f
        cancel.typeface = Typeface.DEFAULT_BOLD
        cancel.setPadding(dp(22), dp(12), dp(22), dp(12))
        cancel.background = glass(dp(24).toFloat())
        cancel.setOnClickListener { onDone(false) }
        bar.addView(cancel, LinearLayout.LayoutParams(-2, -2))
        title.setTextColor(Color.WHITE)
        title.textSize = 20f
        title.gravity = Gravity.CENTER
        bar.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val ok = FrameLayout(a)
        val og = GradientDrawable()
        og.shape = GradientDrawable.OVAL
        og.setColor(0xFF3478F6.toInt())
        og.setStroke(dp(1), 0x66FFFFFF)
        ok.background = og
        ok.addView(CheckIcon(a), FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
        ok.setOnClickListener { apply() }
        bar.addView(ok, LinearLayout.LayoutParams(dp(50), dp(50)))
        return bar
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
        val aspect = dm.heightPixels.toFloat() / dm.widthPixels
        var cw = dm.widthPixels * 0.615f
        var ch = cw * aspect
        val maxH = dm.heightPixels * 0.6f
        if (ch > maxH) {
            ch = maxH
            cw = ch / aspect
        }
        val step = (cw + dp(22)).toInt()
        pager.step = step
        val pad = ((dm.widthPixels - step) / 2f).toInt()
        pager.row.setPadding(pad, 0, pad, 0)
        for (i in 0..1) {
            val card = FrameLayout(a)
            card.setBackgroundColor(0xFF24262B.toInt())
            rounded(card, dp(36).toFloat())
            val iv = ImageView(a)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            card.addView(iv, LayoutParams(-1, -1))
            previews[i] = iv
            if (i == 0) lockOverlay(card, cw) else {
                card.addView(homeLayer, LayoutParams(-1, -1))
            }
            val cell = FrameLayout(a)
            cell.addView(card, LayoutParams(cw.toInt(), ch.toInt(), Gravity.CENTER))
            card.setOnClickListener { if (pager.nearest() != i) pager.goTo(i) }
            pager.row.addView(cell, LinearLayout.LayoutParams(step, -1))
        }
        pager.onIndex = { setPage(it) }
    }

    // превью экрана блокировки: дата, большие часы, кнопки внизу
    private fun lockOverlay(card: FrameLayout, cw: Float) {
        val date = TextView(a)
        date.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())
        date.setTextColor(Color.WHITE)
        date.textSize = 11f
        date.typeface = Typeface.DEFAULT_BOLD
        date.setPadding(dp(12), dp(5), dp(12), dp(5))
        date.background = glass(dp(14).toFloat(), 0x22FFFFFF, 0x77FFFFFF)
        val dl = LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        dl.topMargin = dp(70)
        card.addView(date, dl)
        val clockBox = FrameLayout(a)
        clockBox.background = glass(dp(18).toFloat(), 0x11FFFFFF, 0x66FFFFFF)
        val clock = TextView(a)
        clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        clock.setTextColor(0xBBFFFFFF.toInt())
        clock.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, cw * 0.46f)
        clock.includeFontPadding = false
        clock.gravity = Gravity.CENTER
        clockBox.addView(clock, LayoutParams(-1, -1))
        val cl = LayoutParams(-1, (cw * 0.7f).toInt(), Gravity.TOP)
        cl.setMargins(dp(14), dp(104), dp(14), 0)
        card.addView(clockBox, cl)
        for (k in 0..1) {
            val c = FrameLayout(a)
            c.background = glass(dp(22).toFloat(), 0x33FFFFFF, 0x55FFFFFF).also { it.shape = GradientDrawable.OVAL }
            c.addView(MiniGlyph(a, k), LayoutParams(dp(26), dp(26), Gravity.CENTER))
            val lp = LayoutParams(dp(44), dp(44), if (k == 0) Gravity.BOTTOM or Gravity.START else Gravity.BOTTOM or Gravity.END)
            lp.setMargins(dp(16), 0, dp(16), dp(20))
            card.addView(c, lp)
        }
    }

    private fun options(): View {
        val row = LinearLayout(a)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        row.setPadding(0, dp(14), 0, dp(26))
        row.addView(opt(0, L(a, "Изображение", "Image")) { pickImage() }.first)
        val o3 = opt(1, "3D")
        o3.first.setOnClickListener { toggle3d() }
        circle3d = o3.second
        label3d = o3.first.getChildAt(1) as TextView
        row.addView(o3.first)
        val sep = View(a)
        sep.setBackgroundColor(0x77FFFFFF)
        val sl = LinearLayout.LayoutParams(dp(1), dp(52))
        sl.setMargins(dp(8), dp(6), dp(8), 0)
        sl.gravity = Gravity.TOP
        row.addView(sep, sl)
        val ob = opt(2, L(a, "Размытие", "Blur"))
        ob.first.setOnClickListener { cycleBlur() }
        circleBlur = ob.second
        blurLabel = ob.first.getChildAt(1) as TextView
        row.addView(ob.first)
        return row
    }

    private fun opt(kind: Int, label: String, click: (() -> Unit)? = null): Pair<LinearLayout, View> {
        val box = LinearLayout(a)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        val c = FrameLayout(a)
        c.background = glass(dp(32).toFloat()).also { it.shape = GradientDrawable.OVAL }
        c.addView(OptIcon(a, kind), LayoutParams(dp(28), dp(28), Gravity.CENTER))
        box.addView(c, LinearLayout.LayoutParams(dp(64), dp(64)))
        val t = TextView(a)
        t.text = label
        t.setTextColor(Color.WHITE)
        t.textSize = 14f
        t.gravity = Gravity.CENTER
        t.setPadding(0, dp(8), 0, 0)
        box.addView(t, LinearLayout.LayoutParams(-2, -2))
        val lp = LinearLayout.LayoutParams(-2, -2)
        lp.setMargins(dp(10), 0, dp(10), 0)
        box.layoutParams = lp
        if (click != null) box.setOnClickListener { click() }
        return Pair(box, c)
    }

    private fun mark(c: View?, on: Boolean) {
        val g = glass(dp(32).toFloat(), if (on) 0x883478F6.toInt() else 0x33FFFFFF, if (on) 0xFF8AB4FF.toInt() else 0x55FFFFFF)
        g.shape = GradientDrawable.OVAL
        c?.background = g
    }

    private fun tgt(): Tgt = if (page == 0) lock else home

    private fun setPage(i: Int) {
        page = i
        title.text = if (i == 0) L(a, "Экран блокировки", "Lockscreen") else L(a, "Рабочий стол", "Home screen")
        updateButtons()
        updateBackdrop()
    }

    private fun updateButtons() {
        val lvl = tgt().blur
        mark(circleBlur, lvl > 0)
        blurLabel?.text = L(a, "Размытие", "Blur") + if (lvl == 1) " · 1" else if (lvl == 2) " · 2" else ""
        mark(circle3d, page == 0 && lock3d)
        circle3d?.alpha = if (page == 0) 1f else 0.4f
        label3d?.alpha = if (page == 0) 1f else 0.4f
    }

    private fun toggle3d() {
        if (page != 0) {
            Toast.makeText(a, L(a, "3D-эффект работает на экране блокировки", "3D effect works on the lockscreen"), Toast.LENGTH_SHORT).show()
            return
        }
        lock3d = !lock3d
        lock.changed = true
        updateButtons()
    }

    private fun cycleBlur() {
        val t = tgt()
        t.blur = (t.blur + 1) % 3
        t.changed = true
        updateButtons()
        refreshPreview(page)
    }

    // ---- загрузка и превью ----
    private fun bitmapOf(dr: Drawable?): Bitmap? {
        if (dr == null) return null
        if (dr is BitmapDrawable && dr.bitmap != null) return dr.bitmap
        val w = maxOf(1, dr.intrinsicWidth)
        val h = maxOf(1, dr.intrinsicHeight)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        dr.setBounds(0, 0, w, h)
        dr.draw(Canvas(b))
        return b
    }

    private fun readFile(name: String): Bitmap? {
        val f = File(a.filesDir, name)
        if (!f.exists()) return null
        return try {
            BitmapFactory.decodeFile(f.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    private fun load() {
        Thread {
            val cur = try {
                bitmapOf(wm.drawable)
            } catch (e: Exception) {
                null
            }
            home.orig = readFile("home_orig.jpg") ?: cur
            lock.orig = readFile("lock_orig.jpg") ?: cur
            val apps = try {
                queryApps(a)
            } catch (e: Exception) {
                emptyList()
            }
            a.runOnUiThread {
                refreshPreview(0)
                refreshPreview(1)
                buildHome(apps)
                updateBackdrop()
            }
        }.start()
    }

    private fun refreshPreview(i: Int) {
        val t = if (i == 0) lock else home
        val src = t.orig
        val iv = previews[i] ?: return
        if (src == null) {
            iv.setImageDrawable(GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF6E8FB5.toInt(), 0xFF2A3A5C.toInt())))
        } else {
            iv.setImageBitmap(blurBitmap(fitBitmap(src, 360, 780), t.blur))
        }
        if (i == page) updateBackdrop()
    }

    private fun updateBackdrop() {
        val t = tgt()
        val src = t.orig ?: return
        backdrop.setImageBitmap(blurBitmap(fitBitmap(src, 90, 195), 2))
    }

    // превью рабочего стола: значки приложений сеткой и док
    private fun buildHome(apps: List<LauncherApp>) {
        homeLayer.removeAllViews()
        val cw = (dm.widthPixels * 0.615f)
        val icon = (cw * 0.17f).toInt()
        val gap = ((cw - 4 * icon) / 5f).toInt()
        var n = 0
        for (r in 0 until 4) {
            for (c in 0 until 4) {
                if (n >= apps.size) break
                val v = ShapedIcon(a, apps[n].icon, prefs.shape)
                val lp = LayoutParams(icon, icon, Gravity.TOP or Gravity.START)
                lp.leftMargin = gap + c * (icon + gap)
                lp.topMargin = dp(70) + r * (icon + gap)
                homeLayer.addView(v, lp)
                n++
            }
        }
        for (c in 0 until 4) {
            if (n >= apps.size) break
            val v = ShapedIcon(a, apps[n].icon, prefs.shape)
            val lp = LayoutParams(icon, icon, Gravity.BOTTOM or Gravity.START)
            lp.leftMargin = gap + c * (icon + gap)
            lp.bottomMargin = dp(24)
            homeLayer.addView(v, lp)
            n++
        }
    }

    // ---- изображение ----
    private fun pickImage() {
        a.pickForCustom { uri -> loadUri(uri) }
    }

    private fun loadUri(uri: Uri) {
        Thread {
            val bmp: Bitmap? = try {
                val o = BitmapFactory.Options()
                o.inJustDecodeBounds = true
                a.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
                var s = 1
                while (o.outHeight / s > 2400) s *= 2
                val o2 = BitmapFactory.Options()
                o2.inSampleSize = s
                a.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
            } catch (e: Exception) {
                null
            }
            a.runOnUiThread {
                if (bmp == null) {
                    Toast.makeText(a, L(a, "Не удалось открыть картинку", "Could not open the image"), Toast.LENGTH_SHORT).show()
                } else {
                    val t = tgt()
                    t.orig = bmp
                    t.changed = true
                    refreshPreview(page)
                }
            }
        }.start()
    }

    // ---- применение ----
    private fun saveJpg(b: Bitmap, name: String) {
        FileOutputStream(File(a.filesDir, name)).use { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    }

    private fun apply() {
        val hc = home.changed
        val lc = lock.changed
        val ho = home.orig
        val lo = lock.orig
        Thread {
            var ok = true
            try {
                val w = dm.widthPixels
                val h = dm.heightPixels
                if (hc && ho != null) {
                    saveJpg(ho, "home_orig.jpg")
                    wm.setBitmap(blurBitmap(fitBitmap(ho, w, h), home.blur), null, true, WallpaperManager.FLAG_SYSTEM)
                }
                if (lc && lo != null) {
                    saveJpg(lo, "lock_orig.jpg")
                    val out = blurBitmap(fitBitmap(lo, w, h), lock.blur)
                    saveJpg(out, "lock_wall.jpg")
                    try {
                        wm.setBitmap(out, null, true, WallpaperManager.FLAG_LOCK)
                    } catch (e: Exception) {
                    }
                }
            } catch (e: Exception) {
                ok = false
            }
            prefs.homeBlur = home.blur
            prefs.lockBlur = lock.blur
            prefs.lock3d = if (lock3d) 1 else 0
            a.runOnUiThread {
                Toast.makeText(a, if (ok) L(a, "Обои сохранены", "Wallpapers saved") else L(a, "Не удалось сохранить обои", "Could not save wallpapers"),
                    Toast.LENGTH_SHORT).show()
                onDone(ok)
            }
        }.start()
    }
}
