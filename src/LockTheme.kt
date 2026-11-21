package com.example.pixellauncher

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color
import android.os.Build

/** Палитра экрана ввода пароля, считается из цветов обоев. */
object LockTheme {
    class Pal(
        val top: Int,
        val bottom: Int,
        val key: Int,
        val pressed: Int,
        val ink: Int,
        val dim: Int,
        val light: Boolean
    )

    private fun mk(h: Float, s: Float, v: Float, a: Int = 255): Int =
        Color.HSVToColor(a, floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))

    fun pal(c: Context, forceDark: Boolean = false): Pal {
        var primary = 0xFF5B7BB8.toInt()
        var secondary = primary
        var lightWall = true
        if (Build.VERSION.SDK_INT >= 27) {
            try {
                val wm = WallpaperManager.getInstance(c)
                val wc = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                    ?: wm.getWallpaperColors(WallpaperManager.FLAG_LOCK)
                if (wc != null) {
                    primary = wc.primaryColor.toArgb()
                    secondary = wc.secondaryColor?.toArgb() ?: primary
                    lightWall = (wc.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
                }
            } catch (e: Exception) {
            }
        }
        val p = FloatArray(3)
        val q = FloatArray(3)
        Color.colorToHSV(primary, p)
        Color.colorToHSV(secondary, q)
        // у серых обоев не подмешиваем случайный оттенок
        val sp = if (p[1] < 0.08f) 0f else p[1]
        val sq = if (q[1] < 0.08f) 0f else q[1]
        // экран PIN всегда светлый, цвет берётся от обоев; тёмный вариант только для панели графического ключа
        val light = !forceDark
        return if (light) {
            val ink = mk(p[0], if (sp == 0f) 0f else 0.35f + 0.4f * sp, 0.22f)
            Pal(
                mk(p[0], if (sp == 0f) 0f else 0.10f + 0.30f * sp, 0.95f),
                mk(q[0], if (sq == 0f) 0f else 0.14f + 0.30f * sq, 0.88f),
                Color.argb(102, 255, 255, 255),
                mk(p[0], if (sp == 0f) 0f else 0.30f + 0.25f * sp, 1f, 0xB0),
                ink,
                (ink and 0x00FFFFFF) or (0xAA shl 24),
                true
            )
        } else {
            val ink = mk(p[0], if (sp == 0f) 0f else 0.08f + 0.1f * sp, 0.96f)
            Pal(
                mk(p[0], if (sp == 0f) 0f else 0.35f + 0.3f * sp, 0.22f),
                mk(q[0], if (sq == 0f) 0f else 0.40f + 0.3f * sq, 0.12f),
                Color.argb(0x26, 255, 255, 255),
                mk(p[0], if (sp == 0f) 0f else 0.40f + 0.2f * sp, 0.55f, 0x99),
                ink,
                (ink and 0x00FFFFFF) or (0xAA shl 24),
                false
            )
        }
    }
}
