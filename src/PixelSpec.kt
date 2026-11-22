package com.example.pixellauncher

import android.view.animation.Interpolator
import android.view.animation.PathInterpolator

/**
 * Пресет "Pixel": параметры сняты с видео (Pixel 7 Pro, 576x1248, 30 к/с).
 *
 * Открытие: окно растёт из значка до полного экрана, за ~300 мс по кривой emphasized decelerate.
 * Прогресс по замерам ширины окна: 33 мс = 0.57, 67 мс = 0.80, 100 мс = 0.86, 133 мс = 0.90, 167 мс = 0.93.
 * Закрытие: окно сжимается в значок за ~200 мс с разгоном (0.09, 0.18, 0.28, 0.47 по 33 мс), потом значок
 * возвращается с небольшим "выскоком" и успокаивается. Домашний экран на время размывается и темнеет.
 */
object PixelSpec {
    const val OPEN_MS = 300f
    const val CLOSE_MS = 200f
    const val SETTLE_MS = 130f
    const val LAUNCH_AT = 0.4f          // приложение запускается, когда прошло 40% времени открытия
    const val BLUR_SIGMA_W = 0.003f     // сигма размытия домашнего экрана в долях ширины
    const val DIM = 0.88f               // домашний экран темнеет до 88%
    const val DEVICE_R_W = 0.085f       // радиус углов окна около экранного
    const val SPLASH_ICON_W = 0.26f     // значок на заставке окна (0.26 ширины экрана)
    const val ICON_POP = 1.18f          // значок после закрытия: 1.18 -> 1.0

    val OPEN: Interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)

    /** Разгон при закрытии (подогнан под замеры: почти линейно, потом резкий конец). */
    fun closeEase(u: Float): Float = 0.56f * u + 0.44f * u * u * u
}
