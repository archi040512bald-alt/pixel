package com.example.pixellauncher

import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.*
import android.net.Uri
import android.os.Build
import android.renderscript.*
import android.view.*
import android.widget.*
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

private fun wallPrefs(c: Context) = c.getSharedPreferences("wall_prefs", Context.MODE_PRIVATE)

@Suppress("DEPRECATION")
fun blurBitmap(c: Context, src: Bitmap, level: Int): Bitmap {
    if (level <= 0) return src
    val sc = if (level == 1) 0.35f else 0.2f
    val w = maxOf(16, (src.width * sc).toInt())
    val h = maxOf(16, (src.height * sc).toInt())
    val small = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
    try {
        val rs = RenderScript.create(c)
        val input = Allocation.createFromBitmap(rs, small)
        val output = Allocation.createTyped(rs, input.type)
        val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        script.setRadius(if (level == 1) 12f else 25f)
        script.setInput(input)
        script.forEach(output)
        output.copyTo(small)
        rs.destroy()
    } catch (_: Exception) {}
    return Bitmap.createScaledBitmap(small, src.width, src.height, true)
}

fun fitBitmap(src: Bitmap, w: Int, h: Int): Bitmap {
    val sr = src.width.toFloat() / src.height
    val tr = w.toFloat() / h
    val cw = if (sr > tr) (src.height * tr).toInt() else src.width
    val ch = if (sr > tr) src.height else (src.width / tr).toInt()
    val c = Bitmap.createBitmap(src, (src.width - cw) / 2, (src.height - ch) / 2, cw, ch)
    return Bitmap.createScaledBitmap(c, w, h, true)
}

class OptIcon(c: Context, private val kind: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val d = c.resources.displayMetrics.density
    override fun onDraw(cv: Canvas) {
        p.color = Color.WHITE; p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d; p.strokeJoin = Paint.Join.ROUND
        val w = width.toFloat(); val h = height.toFloat()
        when (kind) {
            0 -> {
                p.style = Paint.Style.FILL; r.set(w * 0.14f, h * 0.18f, w * 0.86f, h * 0.82f)
                cv.drawRoundRect(r, 4f * d, 4f * d, p); p.color = 0xFF6B6E75.toInt()
                cv.drawCircle(w * 0.36f, h * 0.38f, 2.6f * d, p)
                val path = Path().apply { moveTo(w * 0.2f, h * 0.76f); lineTo(w * 0.42f, h * 0.52f); lineTo(w * 0.58f, h * 0.68f); lineTo(w * 0.7f, h * 0.56f); lineTo(w * 0.8f, h * 0.76f); close() }
                cv.drawPath(path, p)
            }
            1 -> {
                val path = Path().apply { moveTo(w * 0.5f, h * 0.1f); lineTo(w * 0.86f, h * 0.3f); lineTo(w * 0.86f, h * 0.7f); lineTo(w * 0.5f, h * 0.9f); lineTo(w * 0.14f, h * 0.7f); lineTo(w * 0.14f, h * 0.3f); close(); moveTo(w * 0.14f, h * 0.3f); lineTo(w * 0.5f, h * 0.5f); lineTo(w * 0.86f, h * 0.3f); moveTo(w * 0.5f, h * 0.5f); lineTo(w * 0.5f, h * 0.9f) }
                cv.drawPath(path, p)
            }
            else -> {
                cv.drawCircle(w / 2f, h / 2f, w * 0.42f, p); cv.drawCircle(w / 2f, h / 2f, w * 0.27f, p)
                p.style = Paint.Style.FILL; cv.drawCircle(w / 2f, h / 2f, w * 0.12f, p)
            }
        }
    }
}

class CheckIcon(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(cv: Canvas) {
        p.color = Color.WHITE; p.style = Paint.Style.STROKE; p.strokeWidth = width * 0.11f; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        cv.drawPath(Path().apply { moveTo(width * 0.2f, height * 0.54f); lineTo(width * 0.42f, height * 0.74f); lineTo(width * 0.8f, height * 0.28f) }, p)
    }
}

class MiniGlyph(c: Context, private val kind: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG); private val r = RectF()
    override fun onDraw(cv: Canvas) {
        p.color = Color.WHITE; p.style = Paint.Style.FILL; val w = width.toFloat(); val h = height.toFloat()
        if (kind == 0) { r.set(w * 0.36f, h * 0.18f, w * 0.64f, h * 0.38f); cv.drawRoundRect(r, 2f, 2f, p); r.set(w * 0.4f, h * 0.38f, w * 0.6f, h * 0.84f); cv.drawRoundRect(r, 3f, 3f, p) }
        else { r.set(w * 0.16f, h * 0.3f, w * 0.84f, h * 0.78f); cv.drawRoundRect(r, 5f, 5f, p); p.color = 0xFF5F6368.toInt(); cv.drawCircle(w / 2f, h * 0.54f, w * 0.15f, p) }
    }
}

@Suppress("DEPRECATION")
class ImagePickFragment : Fragment() {
    var onPicked: ((Uri) -> Unit)? = null
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        if (req == 7711 && res == Activity.RESULT_OK) data?.data?.let { onPicked?.invoke(it) }
    }
}
class WallCustom(private val a: SettingsActivity, inT: Int, inB: Int, private val onDone: (Boolean) -> Unit) : FrameLayout(a) {
    private class Tgt(var orig: Bitmap?, var blur: Int, var changed: Boolean)
    private val d = a.resources.displayMetrics.density; private val dm = a.resources.displayMetrics
    private val wm = WallpaperManager.getInstance(a); private val prefs = Prefs(a)
    private val home = Tgt(null, wallPrefs(a).getInt("homeBlur", 0), false)
    private val lock = Tgt(null, wallPrefs(a).getInt("lockBlur", 0), false)
    private var lock3d = wallPrefs(a).getInt("lock3d", 0) == 1
    private val backdrop = ImageView(a); private val title = TextView(a); private val pager = WallPager(a)
    private val previews = arrayOfNulls<ImageView>(2); private val homeLayer = FrameLayout(a)
    private var circleBlur: View? = null; private var circle3d: View? = null
    private var blurLabel: TextView? = null; private var label3d: TextView? = null; private var page = 0
    private fun dp(v: Int) = (v * d).toInt()

    init {
        isClickable = true; setBackgroundColor(0xFF1A1C1F.toInt())
        backdrop.scaleType = ImageView.ScaleType.CENTER_CROP; addView(backdrop, LayoutParams(-1, -1))
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(0, inT, 0, inB) }
        addView(col, LayoutParams(-1, -1))
        col.addView(topBar(), LinearLayout.LayoutParams(-1, -2))
        buildPager(); col.addView(pager, LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(options(), LinearLayout.LayoutParams(-1, -2))
        setPage(0); load()
    }

    private fun glass(r: Float, fill: Int = 0x33FFFFFF, stroke: Int = 0x55FFFFFF) = GradientDrawable().apply { cornerRadius = r; setColor(fill); setStroke(dp(1), stroke) }

    private fun topBar() = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(10))
        addView(TextView(a).apply {
            text = L(a, "Отмена", "Cancel"); setTextColor(Color.WHITE); textSize = 16f; typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(22), dp(12), dp(22), dp(12)); background = glass(dp(24).toFloat()); setOnClickListener { onDone(false) }
        }, LinearLayout.LayoutParams(-2, -2))
        title.apply { setTextColor(Color.WHITE); textSize = 20f; gravity = Gravity.CENTER }
        addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        addView(FrameLayout(a).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF3478F6.toInt()); setStroke(dp(1), 0x66FFFFFF) }
            addView(CheckIcon(a), LayoutParams(dp(26), dp(26), Gravity.CENTER)); setOnClickListener { apply() }
        }, LinearLayout.LayoutParams(dp(50), dp(50)))
    }

    private fun rounded(v: View, r: Float) {
        v.clipToOutline = true
        v.outlineProvider = object : ViewOutlineProvider() { override fun getOutline(view: View, o: Outline) = o.setRoundRect(0, 0, view.width, view.height, r) }
    }

    private fun buildPager() {
        val aspect = dm.heightPixels.toFloat() / dm.widthPixels
        var cw = dm.widthPixels * 0.615f; var ch = cw * aspect
        if (ch > dm.heightPixels * 0.6f) { ch = dm.heightPixels * 0.6f; cw = ch / aspect }
        val step = (cw + dp(22)).toInt(); pager.step = step
        val pad = ((dm.widthPixels - step) / 2f).toInt(); pager.row.setPadding(pad, 0, pad, 0)
        for (i in 0..1) {
            val card = FrameLayout(a).apply { setBackgroundColor(0xFF24262B.toInt()); rounded(this, dp(36).toFloat()) }
            val iv = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            card.addView(iv, LayoutParams(-1, -1)); previews[i] = iv
            if (i == 0) lockOverlay(card, cw) else card.addView(homeLayer, LayoutParams(-1, -1))
            val cell = FrameLayout(a).apply { addView(card, LayoutParams(cw.toInt(), ch.toInt(), Gravity.CENTER)) }
            card.setOnClickListener { if (pager.nearest() != i) pager.goTo(i) }
            pager.row.addView(cell, LinearLayout.LayoutParams(step, -1))
        }
        pager.onIndex = { setPage(it) }
    }

    private fun lockOverlay(card: FrameLayout, cw: Float) {
        card.addView(TextView(a).apply {
            text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()); setTextColor(Color.WHITE); textSize = 11f; typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(5), dp(12), dp(5)); background = glass(dp(14).toFloat(), 0x22FFFFFF, 0x77FFFFFF)
        }, LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(70) })
        card.addView(FrameLayout(a).apply {
            background = glass(dp(18).toFloat(), 0x11FFFFFF, 0x66FFFFFF)
            addView(TextView(a).apply {
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()); setTextColor(0xBBFFFFFF.toInt())
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD); setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, cw * 0.46f); includeFontPadding = false; gravity = Gravity.CENTER
            }, LayoutParams(-1, -1))
        }, LayoutParams(-1, (cw * 0.7f).toInt(), Gravity.TOP).apply { setMargins(dp(14), dp(104), dp(14), 0) })
        for (k in 0..1) card.addView(FrameLayout(a).apply {
            background = glass(dp(22).toFloat(), 0x33FFFFFF, 0x55FFFFFF).also { it.shape = GradientDrawable.OVAL }
            addView(MiniGlyph(a, k), LayoutParams(dp(26), dp(26), Gravity.CENTER))
        }, LayoutParams(dp(44), dp(44), if (k == 0) Gravity.BOTTOM or Gravity.START else Gravity.BOTTOM or Gravity.END).apply { setMargins(dp(16), 0, dp(16), dp(20)) })
    }

    private fun options() = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(26))
        addView(opt(0, L(a, "Изображение", "Image")) { pickImage() }.first)
        val o3 = opt(1, "3D").also { it.first.setOnClickListener { toggle3d() }; circle3d = it.second; label3d = it.first.getChildAt(1) as TextView }
        addView(o3.first)
        addView(View(a).apply { setBackgroundColor(0x77FFFFFF) }, LinearLayout.LayoutParams(dp(1), dp(52)).apply { setMargins(dp(8), dp(6), dp(8), 0); gravity = Gravity.TOP })
        val ob = opt(2, L(a, "Размытие", "Blur")).also { it.first.setOnClickListener { cycleBlur() }; circleBlur = it.second; blurLabel = it.first.getChildAt(1) as TextView }
        addView(ob.first)
    }

    private fun opt(kind: Int, label: String, click: (() -> Unit)? = null): Pair<LinearLayout, View> {
        val c = FrameLayout(a).apply { background = glass(dp(32).toFloat()).also { it.shape = GradientDrawable.OVAL }; addView(OptIcon(a, kind), LayoutParams(dp(28), dp(28), Gravity.CENTER)) }
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; addView(c, LinearLayout.LayoutParams(dp(64), dp(64)))
            addView(TextView(a).apply { text = label; setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) }, LinearLayout.LayoutParams(-2, -2))
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(10), 0, dp(10), 0) }
            if (click != null) setOnClickListener { click() }
        }
        return Pair(box, c)
    }

    private fun mark(c: View?, on: Boolean) { c?.background = glass(dp(32).toFloat(), if (on) 0x883478F6.toInt() else 0x33FFFFFF, if (on) 0xFF8AB4FF.toInt() else 0x55FFFFFF).also { it.shape = GradientDrawable.OVAL } }
    private fun tgt() = if (page == 0) lock else home
    private fun setPage(i: Int) { page = i; title.text = if (i == 0) L(a, "Экран блокировки", "Lockscreen") else L(a, "Рабочий стол", "Home screen"); updateButtons(); updateBackdrop() }
    private fun updateButtons() {
        val lvl = tgt().blur; mark(circleBlur, lvl > 0); blurLabel?.text = L(a, "Размытие", "Blur") + if (lvl == 1) " · 1" else if (lvl == 2) " · 2" else ""
        mark(circle3d, page == 0 && lock3d); circle3d?.alpha = if (page == 0) 1f else 0.4f; label3d?.alpha = if (page == 0) 1f else 0.4f
    }
    private fun toggle3d() { if (page != 0) Toast.makeText(a, L(a, "3D-эффект работает на экране блокировки", "3D effect works on the lockscreen"), Toast.LENGTH_SHORT).show() else { lock3d = !lock3d; lock.changed = true; updateButtons() } }
    private fun cycleBlur() { val t = tgt(); t.blur = (t.blur + 1) % 3; t.changed = true; updateButtons(); refreshPreview(page) }

    private fun readFile(name: String) = File(a.filesDir, name).let { if (it.exists()) try { BitmapFactory.decodeFile(it.absolutePath) } catch (_: Exception) { null } else null }
    private fun load() {
        Thread {
            val cur = try { (wm.drawable as? BitmapDrawable)?.bitmap } catch (_: Exception) { null }
            home.orig = readFile("home_orig.jpg") ?: cur; lock.orig = readFile("lock_orig.jpg") ?: cur
            val apps = try { queryApps(a) } catch (_: Exception) { emptyList() }
            a.runOnUiThread { refreshPreview(0); refreshPreview(1); buildHome(apps); updateBackdrop() }
        }.start()
    }
    private fun refreshPreview(i: Int) {
        val t = if (i == 0) lock else home; val src = t.orig; val iv = previews[i] ?: return
        if (src == null) iv.setImageDrawable(GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF6E8FB5.toInt(), 0xFF2A3A5C.toInt())))
        else Thread { val b = blurBitmap(a, fitBitmap(src, 300, 650), t.blur); a.runOnUiThread { iv.setImageBitmap(b); if (i == page) updateBackdrop() } }.start()
    }
    private fun updateBackdrop() { val src = tgt().orig ?: return; Thread { val b = blurBitmap(a, fitBitmap(src, 90, 195), 2); a.runOnUiThread { backdrop.setImageBitmap(b) } }.start() }

    private fun buildHome(apps: List<LauncherApp>) {
        homeLayer.removeAllViews(); val cw = dm.widthPixels * 0.615f; val icon = (cw * 0.17f).toInt(); val gap = ((cw - 4 * icon) / 5f).toInt(); var n = 0
        for (r in 0..3) for (c in 0..3) if (n < apps.size) homeLayer.addView(ShapedIcon(a, apps[n++].icon, prefs.shape), LayoutParams(icon, icon, Gravity.TOP or Gravity.START).apply { leftMargin = gap + c * (icon + gap); topMargin = dp(70) + r * (icon + gap) })
        for (c in 0..3) if (n < apps.size) homeLayer.addView(ShapedIcon(a, apps[n++].icon, prefs.shape), LayoutParams(icon, icon, Gravity.BOTTOM or Gravity.START).apply { leftMargin = gap + c * (icon + gap); bottomMargin = dp(24) })
    }

    @Suppress("DEPRECATION")
    private fun pickImage() {
        val fm = a.fragmentManager; var frag = fm.findFragmentByTag("wall_pick") as? ImagePickFragment
        if (frag == null) { frag = ImagePickFragment(); fm.beginTransaction().add(frag, "wall_pick").commitNow() }
        frag.onPicked = { loadUri(it) }
        frag.startActivityForResult(Intent.createChooser(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }, ""), 7711)
    }

    private fun loadUri(uri: Uri) {
        Thread {
            val bmp = try {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                a.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
                var s = 1; while (o.outHeight / s > 1800) s *= 2
                a.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
            } catch (_: Exception) { null }
            a.runOnUiThread {
                if (bmp == null) Toast.makeText(a, L(a, "Не удалось открыть картинку", "Could not open the image"), Toast.LENGTH_SHORT).show()
                else { val t = tgt(); t.orig = bmp; t.changed = true; refreshPreview(page) }
            }
        }.start()
    }

    private fun saveJpg(b: Bitmap, name: String) = FileOutputStream(File(a.filesDir, name)).use { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    private fun apply() {
        val hc = home.changed; val lc = lock.changed; val ho = home.orig; val lo = lock.orig
        Thread {
            var ok = true
            try {
                val w = dm.widthPixels; val h = dm.heightPixels
                if (hc && ho != null) { saveJpg(ho, "home_orig.jpg"); val out = blurBitmap(a, fitBitmap(ho, w, h), home.blur); saveJpg(out, "home_wall.jpg"); wm.setBitmap(out, null, true, WallpaperManager.FLAG_SYSTEM) }
                if (lc && lo != null) { saveJpg(lo, "lock_orig.jpg"); val out = blurBitmap(a, fitBitmap(lo, w, h), lock.blur); saveJpg(out, "lock_wall.jpg"); try { wm.setBitmap(out, null, true, WallpaperManager.FLAG_LOCK) } catch (_: Exception) {} }
            } catch (_: Exception) { ok = false }
            wallPrefs(a).edit().putInt("homeBlur", home.blur).putInt("lockBlur", lock.blur).putInt("lock3d", if (lock3d) 1 else 0).apply()
            a.runOnUiThread { Toast.makeText(a, if (ok) L(a, "Обои сохранены", "Wallpapers saved") else L(a, "Не удалось сохранить обои", "Could not save wallpapers"), Toast.LENGTH_SHORT).show(); onDone(ok) }
        }.start()
    }
}
