package com.example.pixellauncher

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.os.Build

/**
 * Цветные (тематические) значки в стиле Material You.
 * style: 0 = оригинальные (отключено), 1 = цвета обоев (Android 12+), 2 = свой цвет из SEEDS.
 * Монохромный слой значка есть у приложений на Android 13+; остальные остаются как есть,
 * если не включено "красить все" (тогда делается силуэт по альфа-каналу).
 */
object IconTheme {
    const val OFF = 0
    const val WALLPAPER = 1
    const val CUSTOM = 2

    val SEEDS = intArrayOf(
        0xFF4285F4.toInt(), 0xFF34A853.toInt(), 0xFFEA4335.toInt(), 0xFFFBBC04.toInt(),
        0xFF9C27B0.toInt(), 0xFFFF6D00.toInt(), 0xFF00ACC1.toInt(), 0xFFE91E63.toInt()
    )

    fun styleNames(c: Context): List<String> = listOf(
        L(c, "Оригинальные", "Original"),
        L(c, "Цвета обоев", "Wallpaper colors"),
        L(c, "Свой цвет", "Custom color")
    )

    fun seedNames(c: Context): List<String> = listOf(
        L(c, "Синий", "Blue"), L(c, "Зелёный", "Green"), L(c, "Красный", "Red"),
        L(c, "Жёлтый", "Yellow"), L(c, "Фиолетовый", "Purple"), L(c, "Оранжевый", "Orange"),
        L(c, "Бирюзовый", "Teal"), L(c, "Розовый", "Pink")
    )

    private fun night(c: Context): Boolean =
        (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    // [фон, значок]
    private fun palette(c: Context, style: Int, seed: Int): IntArray {
        val dark = night(c)
        if (style == WALLPAPER && Build.VERSION.SDK_INT >= 31) {
            return if (dark) intArrayOf(c.getColor(android.R.color.system_accent1_800), c.getColor(android.R.color.system_accent1_100))
            else intArrayOf(c.getColor(android.R.color.system_accent1_100), c.getColor(android.R.color.system_accent1_800))
        }
        val hsv = FloatArray(3)
        Color.colorToHSV(SEEDS[seed.coerceIn(0, SEEDS.size - 1)], hsv)
        val h = hsv[0]
        return if (dark) intArrayOf(Color.HSVToColor(floatArrayOf(h, 0.45f, 0.28f)), Color.HSVToColor(floatArrayOf(h, 0.18f, 0.95f)))
        else intArrayOf(Color.HSVToColor(floatArrayOf(h, 0.22f, 0.97f)), Color.HSVToColor(floatArrayOf(h, 0.75f, 0.28f)))
    }

    /** Возвращает тематический значок или исходный, если стиль выключен или у значка нет монохромного слоя. */
    fun apply(c: Context, d: Drawable, style: Int, seed: Int, all: Boolean): Drawable {
        if (style == OFF || Build.VERSION.SDK_INT < 26) return d
        val pal = palette(c, style, seed)
        var fg: Drawable? = null
        if (Build.VERSION.SDK_INT >= 33 && d is AdaptiveIconDrawable) fg = d.monochrome
        if (fg == null && all) {
            fg = if (d is AdaptiveIconDrawable) d.foreground else InsetDrawable(d, 0.17f)
        }
        if (fg == null) return d
        val f = (fg.constantState?.newDrawable() ?: fg).mutate()
        f.setTint(pal[1])
        return AdaptiveIconDrawable(ColorDrawable(pal[0]), f)
    }
}
