package com.example.pixellauncher

import android.app.Activity
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast

fun applyWallpaper(a: Activity, uri: Uri) {
    Toast.makeText(a, "Устанавливаю обои…", Toast.LENGTH_SHORT).show()
    Thread {
        var msg = "Обои установлены"
        try {
            val dm = a.resources.displayMetrics
            val sw = dm.widthPixels
            val sh = dm.heightPixels
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            a.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var sample = 1
            while (o.outWidth / (sample * 2) >= sw && o.outHeight / (sample * 2) >= sh) sample *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = sample
            val src = a.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
                ?: throw Exception("не удалось прочитать картинку")
            val target = sw.toFloat() / sh
            var w = src.width
            var h = src.height
            if (w.toFloat() / h > target) w = (h * target).toInt() else h = (w / target).toInt()
            val bmp = Bitmap.createBitmap(src, (src.width - w) / 2, (src.height - h) / 2, w, h)
            WallpaperManager.getInstance(a).setBitmap(bmp)
        } catch (e: Exception) {
            msg = "Не удалось: " + e.message
        }
        a.runOnUiThread { Toast.makeText(a, msg, Toast.LENGTH_LONG).show() }
    }.start()
}
