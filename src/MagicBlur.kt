package com.example.pixellauncher

import android.graphics.Bitmap

/**
 * Быстрое размытие для пресета MagicOS: три прохода скользящего окна (по горизонтали и вертикали),
 * считается один раз на уменьшенной копии экрана. Цвета размываются в предумноженном виде,
 * поэтому по краям значков и виджетов нет тёмной каймы.
 */
object MagicBlur {
    fun box(src: Bitmap, r: Int): Bitmap {
        val w = src.width
        val h = src.height
        val n = w * h
        val px = IntArray(n)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val a = FloatArray(n)
        val rr = FloatArray(n)
        val g = FloatArray(n)
        val b = FloatArray(n)
        for (i in 0 until n) {
            val c = px[i]
            val al = (c ushr 24) / 255f
            a[i] = al
            rr[i] = ((c shr 16) and 255) * al
            g[i] = ((c shr 8) and 255) * al
            b[i] = (c and 255) * al
        }
        val tmp = FloatArray(n)
        for (ch in arrayOf(a, rr, g, b)) {
            repeat(3) {
                pass(ch, tmp, w, h, r, true)
                pass(tmp, ch, w, h, r, false)
            }
        }
        val out = IntArray(n)
        for (i in 0 until n) {
            val al = a[i]
            if (al <= 0.002f) {
                out[i] = 0
            } else {
                val ia = 1f / al
                val ai = (al * 255f + 0.5f).toInt().coerceIn(0, 255)
                val ri = (rr[i] * ia + 0.5f).toInt().coerceIn(0, 255)
                val gi = (g[i] * ia + 0.5f).toInt().coerceIn(0, 255)
                val bi = (b[i] * ia + 0.5f).toInt().coerceIn(0, 255)
                out[i] = (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        }
        val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bm.setPixels(out, 0, w, 0, 0, w, h)
        return bm
    }

    private fun pass(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int, horiz: Boolean) {
        val len = if (horiz) w else h
        val lines = if (horiz) h else w
        val stride = if (horiz) 1 else w
        val win = (2 * r + 1).toFloat()
        for (line in 0 until lines) {
            val base = if (horiz) line * w else line
            var sum = 0f
            for (k in -r..r) sum += src[base + k.coerceIn(0, len - 1) * stride]
            for (i in 0 until len) {
                dst[base + i * stride] = sum / win
                val add = (i + r + 1).coerceAtMost(len - 1)
                val sub = (i - r).coerceAtLeast(0)
                sum += src[base + add * stride] - src[base + sub * stride]
            }
        }
    }
}
