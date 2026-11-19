package com.nocebo.launcher // поправьте под свой пакет

import kotlin.math.pow

/**
 * Параметры анимации HyperOS, измеренные по видео
 * Screen_Recording_20261115_163712_Nocebo_Launcher-3.mp4
 * (480x1068, реально 60 к/с: 434 кадра за 7,18 с; в заголовке указано 120).
 *
 * Время t дано в мс от начала касания (t0 = 60 мс от начала записи).
 * Все размеры в долях ширины W и высоты H экрана.
 * Код не компилировался; это справочный слой, который подключается к WindowCard.
 */
object HyperSpec {
    // ---- Время (мс от касания) ----
    const val BLUR_START = 13f        // размытие/тусклость значков начинается
    const val BLUR_END = 100f         // резкость значков 1.0 -> ~0.1 (ease-out)
    const val POP_1_3_AT = 30f        // значок 1.0 -> 1.3
    const val POP_1_8_AT = 64f        // значок 1.3 -> 1.8
    const val CARD_IN = 96f           // карточка появляется вместо значка (1.8x)
    const val OPEN_END = 246f         // карточка достигла полного размера
    const val CLOSE_END = 380f        // карточка схлопнулась в значок
    const val ICON_BACK_1_3 = 429f    // значок после закрытия: 1.8 -> 1.3
    const val ICON_BACK_1_0 = 495f    //                         1.3 -> 1.0

    // ---- Размытие ----
    const val BLUR_SIGMA_W = 0.0046f  // сигма гауссова размытия в долях W (док и значки; обои не трогать)
    const val ICON_DIM_MIN = 0.90f    // значки тускнеют примерно на 10% к t=230

    // ---- Карточка ----
    const val POP_START_SCALE = 1.8f  // старт карточки = значок x1.8 (~0.31 W)
    const val TARGET_W = 0.73f
    const val TARGET_H = 0.71f
    const val TARGET_CY = 0.536f
    const val TARGET_CX_PULL = 0.18f  // cx = 0.5W + 0.18 * (iconCx - 0.5W)
    const val RADIUS_START_W = 0.125f // радиус углов в начале (доли W)
    const val RADIUS_END_W = 0.0625f  // у полного размера
    const val OPEN_POWER = 1.7f       // p = 1 - (1 - u)^1.7
    const val CLOSE_POWER = 1.45f     // q = 1 - u^1.45

    fun clamp01(x: Float) = x.coerceIn(0f, 1f)

    /** Прогресс карточки 0..1 (открытие, затем закрытие). */
    fun cardProgress(t: Float): Float = when {
        t < CARD_IN -> 0f
        t < OPEN_END -> 1f - (1f - clamp01((t - CARD_IN) / (OPEN_END - CARD_IN))).pow(OPEN_POWER)
        t < CLOSE_END && t >= OPEN_END ->
            1f - clamp01((t - OPEN_END) / (CLOSE_END - OPEN_END)).pow(CLOSE_POWER)
        else -> 0f
    }

    /** Масштаб настоящего значка. Пока карточка видна, значок спрятан. */
    fun iconScale(t: Float): Float = when {
        t < 0f -> 1f
        t < POP_1_3_AT -> 1f + 0.3f * (t / POP_1_3_AT)
        t < POP_1_8_AT -> 1.3f + 0.5f * ((t - POP_1_3_AT) / (POP_1_8_AT - POP_1_3_AT))
        t < CLOSE_END -> POP_START_SCALE
        t < ICON_BACK_1_3 -> 1.8f - 0.5f * ((t - CLOSE_END) / (ICON_BACK_1_3 - CLOSE_END))
        t < ICON_BACK_1_0 -> 1.3f - 0.3f * ((t - ICON_BACK_1_3) / (ICON_BACK_1_0 - ICON_BACK_1_3))
        else -> 1f
    }

    /** Яркость карточки (цвет значка * k). Открытие 0.65 -> 1.0 за 117 мс, закрытие 1.0 -> 0.68 за ~82 мс. */
    fun cardBrightness(t: Float): Float = when {
        t < CARD_IN -> 0.65f
        t < 213f -> 0.65f + 0.35f * (1f - (1f - (t - CARD_IN) / (213f - CARD_IN)).pow(1.5f))
        t < 280f -> 1f
        else -> (1f - 0.32f * clamp01((t - 280f) / 82f))
    }

    /** Сила размытия/тусклости фона 0..1. Держится, пока карточка видна. */
    fun blurAmount(t: Float): Float = when {
        t < BLUR_START -> 0f
        t < BLUR_END -> 1f - (1f - (t - BLUR_START) / (BLUR_END - BLUR_START)).pow(2f)
        else -> 1f // обратный ход после закрытия в видео не попал в кадр: см. примечание в отчёте
    }

    /** Левый/верхний/правый/нижний край карточки в пикселях. iconRect = (l, t, r, b) настоящего значка. */
    fun cardRect(p: Float, iconRect: FloatArray, w: Float, h: Float): FloatArray {
        val icx = (iconRect[0] + iconRect[2]) / 2f
        val icy = (iconRect[1] + iconRect[3]) / 2f
        val half = (iconRect[2] - iconRect[0]) / 2f * POP_START_SCALE
        val sl = icx - half; val sr = icx + half
        val st = icy - half; val sb = icy + half
        val cx = 0.5f * w + TARGET_CX_PULL * (icx - 0.5f * w)
        val cy = TARGET_CY * h
        val tl = cx - TARGET_W * w / 2f; val tr = cx + TARGET_W * w / 2f
        val tt = cy - TARGET_H * h / 2f; val tb = cy + TARGET_H * h / 2f
        fun lerp(a: Float, b: Float) = a + (b - a) * p
        return floatArrayOf(lerp(sl, tl), lerp(st, tt), lerp(sr, tr), lerp(sb, tb))
    }

    fun cornerRadius(p: Float, w: Float) = (RADIUS_START_W + (RADIUS_END_W - RADIUS_START_W) * p) * w
}
