package com.example.pixellauncher

import kotlin.math.pow

/**
 * Параметры пресета MagicOS, снятые покадрово с записи экрана 1000082914.mp4
 * (576x1262, 30 к/с, то есть шаг замера 33 мс).
 *
 * Что измерено по видео:
 *  - открытие: края карточки идут от значка к краям экрана, прогресс p = 1 - (1 - u)^1.9 за ~250 мс;
 *  - закрытие: карточка сжимается в значок, q = (1 - u)^1.4 от текущего прогресса за ~290 мс;
 *  - после схлопывания значок "выскакивает" крупным (x2.0) и за ~110 мс садится в x1.0;
 *  - домашний экран размывается целиком (обои, виджеты, значки) пропорционально размеру карточки
 *    (сигма около 0.05 W при полном окне) и чуть темнеет (~5%);
 *  - карточка белая (в тёмной теме тёмная), значок на ней виден только пока карточка маленькая.
 *
 * Тайминги грубые: шаг в видео 33 мс, поэтому все числа можно подправить ниже, ничего больше менять не нужно.
 * Код не компилировался.
 */
object MagicSpec {
    const val ID = 4

    // ---- Открытие ----
    const val OPEN_MS = 250f          // время до полного размера
    const val OPEN_POWER = 1.9f       // p = 1 - (1 - u)^OPEN_POWER
    const val LAUNCH_AT_P = 0.72f     // когда запускать приложение (доля прогресса карточки)
    const val START_SCALE = 1.15f     // стартовый прямоугольник карточки относительно значка

    // ---- Закрытие ----
    const val CLOSE_MS = 290f         // время закрытия с полного размера
    const val CLOSE_MIN_K = 0.35f     // закрытие с малого прогресса короче: длительность = CLOSE_MS * (K + (1-K) * p0)
    const val CLOSE_POWER = 1.4f      // p = p0 * (1 - u)^CLOSE_POWER
    const val END_SCALE = 1.7f        // карточка сжимается до значка x1.7
    const val HANDOFF_P = 0.10f       // на этом прогрессе карточку заменяет значок

    // ---- Выскок значка ----
    const val POP_FROM = 2.0f
    const val POP_MS = 110f
    const val POP_POWER = 2f          // s = 1 + (POP_FROM - 1) * (1 - v)^POP_POWER

    // ---- Форма ----
    const val RADIUS_MAX_W = 0.075f   // радиус углов у полного окна (доли W)
    const val RADIUS_K = 0.24f        // у маленькой карточки радиус = 0.24 от меньшей стороны

    // ---- Значок на карточке ----
    const val ICON_OUT_P = 0.30f      // при открытии значок гаснет к этому прогрессу
    const val ICON_IN_FROM_P = 0.62f  // при закрытии значок появляется, когда прогресс упал до этого
    const val ICON_IN_SPAN = 0.25f
    const val ICON_CARD_K = 0.36f     // размер значка = 0.36 от ширины карточки (но не меньше 0.9 значка)

    // ---- Фон ----
    const val BLUR_SIGMA_W = 0.05f    // сигма размытия при p = 1 (доли W)
    const val BLUR_POWER = 1.6f       // сигма = BLUR_SIGMA_W * p^1.6
    const val HOME_DIM = 0.05f        // затемнение домашнего экрана при p = 1

    const val SURFACE_LIGHT = 0xFFFFFFFF.toInt()
    const val SURFACE_DARK = 0xFF1F1F1F.toInt()

    fun clamp01(x: Float) = x.coerceIn(0f, 1f)

    /** Прогресс карточки при открытии, u = 0..1. */
    fun openP(u: Float): Float = 1f - (1f - clamp01(u)).pow(OPEN_POWER)

    /** Обратная функция: u по прогрессу p (чтобы открытие продолжалось с места прерванного закрытия). */
    fun openU(p: Float): Float = 1f - (1f - clamp01(p)).pow(1f / OPEN_POWER)

    /** Множитель закрытия, u = 0..1: 1 -> 0. */
    fun closeK(u: Float): Float = (1f - clamp01(u)).pow(CLOSE_POWER)

    /** Масштаб значка при выскоке, v = 0..1. */
    fun popScale(v: Float): Float = 1f + (POP_FROM - 1f) * (1f - clamp01(v)).pow(POP_POWER)

    /** Сигма размытия в пикселях при прогрессе p. */
    fun blurSigma(p: Float, w: Float): Float = BLUR_SIGMA_W * w * clamp01(p).pow(BLUR_POWER)
}
