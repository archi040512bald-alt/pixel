package com.example.pixellauncher

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Цветные (тематические) значки в стиле Material You.
 * style: 0 = оригинальные (отключено), 1 = цвета обоев (Android 12+), 2 = свой цвет из SEEDS.
 * Если у значка есть монохромный слой (Android 13+), используется он.
 * Иначе, при включённом "красить все", знак (глиф) вырезается из готового значка
 * по отличию от цвета его фона и перекрашивается.
 */
object IconTheme {
    const val OFF = 0
    const val WALLPAPER = 1
    const val CUSTOM = 2
    const val DARK = 3
    const val CLEAR = 4

    val SEEDS = intArrayOf(
        0xFF4285F4.toInt(), 0xFF34A853.toInt(), 0xFFEA4335.toInt(), 0xFFFBBC04.toInt(),
        0xFF9C27B0.toInt(), 0xFFFF6D00.toInt(), 0xFF00ACC1.toInt(), 0xFFE91E63.toInt()
    )

    fun styleNames(c: Context): List<String> = listOf(
        L(c, "Оригинальные", "Original"),
        L(c, "Цвета обоев", "Wallpaper colors"),
        L(c, "Свой цвет", "Custom color"),
        L(c, "Тёмные", "Dark"),
        L(c, "Прозрачные", "Clear")
    )

    fun seedNames(c: Context): List<String> = listOf(
        L(c, "Синий", "Blue"), L(c, "Зелёный", "Green"), L(c, "Красный", "Red"),
        L(c, "Жёлтый", "Yellow"), L(c, "Фиолетовый", "Purple"), L(c, "Оранжевый", "Orange"),
        L(c, "Бирюзовый", "Teal"), L(c, "Розовый", "Pink")
    )

    private fun night(c: Context): Boolean =
        (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    // true = тёмный оттенок: 1 = светлые, 2 = тёмные, иначе по системной теме
    private fun darkTone(c: Context): Boolean = when (Prefs(c).iconTone) {
        1 -> false
        2 -> true
        else -> night(c)
    }

    // [фон, значок]
    fun palette(c: Context, style: Int, seed: Int, hue: Int = -1): IntArray {
        if (style == DARK) return intArrayOf(0xFF1C1B1F.toInt(), 0xFFE6E1E5.toInt())
        val dark = darkTone(c)
        if (style == CLEAR) {
            return if (dark) intArrayOf(0x4DFFFFFF, 0xFFF1F3F4.toInt()) else intArrayOf(0x73FFFFFF, 0xFF30343B.toInt())
        }
        if (style == WALLPAPER && Build.VERSION.SDK_INT >= 31) {
            return if (dark) intArrayOf(c.getColor(android.R.color.system_accent1_800), c.getColor(android.R.color.system_accent1_100))
            else intArrayOf(c.getColor(android.R.color.system_accent1_100), c.getColor(android.R.color.system_accent1_800))
        }
        val hsv = FloatArray(3)
        Color.colorToHSV(SEEDS[seed.coerceIn(0, SEEDS.size - 1)], hsv)
        val h = if (hue >= 0) hue.toFloat() else hsv[0]
        return if (dark) intArrayOf(Color.HSVToColor(floatArrayOf(h, 0.45f, 0.28f)), Color.HSVToColor(floatArrayOf(h, 0.18f, 0.95f)))
        else intArrayOf(Color.HSVToColor(floatArrayOf(h, 0.22f, 0.97f)), Color.HSVToColor(floatArrayOf(h, 0.75f, 0.28f)))
    }

    private fun luma(r: Int, g: Int, b: Int): Float = 0.299f * r + 0.587f * g + 0.114f * b

    /**
     * Вырезает знак из готового значка: фон оценивается как самый частый цвет у края,
     * всё, что от него заметно отличается, считается знаком. Возвращает белый знак с альфа-маской.
     */
    private fun glyph(c: Context, src: Drawable): Drawable? {
        val n = 128
        val d = (src.constantState?.newDrawable() ?: src).mutate()
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, n, n)
        d.draw(Canvas(bmp))
        val px = IntArray(n * n)
        bmp.getPixels(px, 0, n, 0, 0, n, n)
        bmp.recycle()

        val lo = (n * 0.08f).toInt()
        val hi = (n * 0.14f).toInt()
        val sums = HashMap<Int, IntArray>()
        var best: IntArray? = null
        for (y in 0 until n) {
            for (x in 0 until n) {
                val dm = minOf(minOf(x, n - 1 - x), minOf(y, n - 1 - y))
                if (dm < lo || dm > hi) continue
                val p = px[y * n + x]
                if (Color.alpha(p) < 200) continue
                val key = ((Color.red(p) shr 4) shl 8) or ((Color.green(p) shr 4) shl 4) or (Color.blue(p) shr 4)
                val s = sums.getOrPut(key) { IntArray(4) }
                s[0]++
                s[1] += Color.red(p)
                s[2] += Color.green(p)
                s[3] += Color.blue(p)
                if (best == null || s[0] > best[0]) best = s
            }
        }
        val bs = best ?: return null
        val br = bs[1] / bs[0]
        val bg = bs[2] / bs[0]
        val bb = bs[3] / bs[0]
        val bl = luma(br, bg, bb)

        val out = IntArray(n * n)
        for (i in px.indices) {
            val p = px[i]
            val a = Color.alpha(p)
            if (a == 0) continue
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)
            val dl = abs(luma(r, g, b) - bl)
            val dr = (r - br).toFloat()
            val dg = (g - bg).toFloat()
            val db = (b - bb).toFloat()
            val dc = sqrt(dr * dr + dg * dg + db * db)
            val m = maxOf(dl, 0.45f * dc)
            val v = ((m - 28f) / 55f).coerceIn(0f, 1f) * (a / 255f)
            out[i] = ((v * 255f).toInt() shl 24) or 0x00FFFFFF
        }
        val mb = Bitmap.createBitmap(out, n, n, Bitmap.Config.ARGB_8888)
        val bd = BitmapDrawable(c.resources, mb)
        bd.isFilterBitmap = true
        return InsetDrawable(bd, 0.17f)
    }

    /** Возвращает тематический значок или исходный, если стиль выключен или перекрасить не удалось. */
    fun apply(c: Context, d: Drawable, style: Int, seed: Int, all: Boolean): Drawable {
        if (style == OFF || Build.VERSION.SDK_INT < 26) return d
        val pal = palette(c, style, seed, if (style == CUSTOM) Prefs(c).iconHue else -1)
        var fg: Drawable? = null
        if (Build.VERSION.SDK_INT >= 33 && d is AdaptiveIconDrawable) fg = d.monochrome
        if (fg == null && all) fg = glyph(c, d)
        if (fg == null) return d
        val f = (fg.constantState?.newDrawable() ?: fg).mutate()
        f.setTint(pal[1])
        return AdaptiveIconDrawable(ColorDrawable(pal[0]), f)
    }
}
