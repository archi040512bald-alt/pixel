package com.example.pixellauncher

import kotlin.math.pow

object HyperSpec {
    const val BLUR_START = 13f
    const val BLUR_END = 100f
    const val POP_1_3_AT = 30f
    const val POP_1_8_AT = 64f
    const val CARD_IN = 96f
    const val OPEN_END = 246f
    const val CLOSE_END = 380f
    const val ICON_BACK_1_3 = 429f
    const val ICON_BACK_1_0 = 495f

    const val BLUR_SIGMA_W = 0.0046f
    const val ICON_DIM_MIN = 0.90f

    const val POP_START_SCALE = 1.8f
    const val TARGET_W = 0.73f
    const val TARGET_H = 0.71f
    const val TARGET_CY = 0.536f
    const val TARGET_CX_PULL = 0.18f
    const val RADIUS_START_W = 0.07f
    const val RADIUS_END_W = 0.0625f
    const val OPEN_POWER = 1.7f
    const val CLOSE_POWER = 1.45f

    fun clamp01(x: Float) = x.coerceIn(0f, 1f)

    fun cardProgress(t: Float): Float = when {
        t < CARD_IN -> 0f
        t < OPEN_END -> 1f - (1f - clamp01((t - CARD_IN) / (OPEN_END - CARD_IN))).pow(OPEN_POWER)
        t < CLOSE_END && t >= OPEN_END ->
            1f - clamp01((t - OPEN_END) / (CLOSE_END - OPEN_END)).pow(CLOSE_POWER)
        else -> 0f
    }

    fun iconScale(t: Float): Float = when {
        t < 0f -> 1f
        t < POP_1_3_AT -> 1f + 0.3f * (t / POP_1_3_AT)
        t < POP_1_8_AT -> 1.3f + 0.5f * ((t - POP_1_3_AT) / (POP_1_8_AT - POP_1_3_AT))
        t < CLOSE_END -> POP_START_SCALE
        t < ICON_BACK_1_3 -> 1.8f - 0.5f * ((t - CLOSE_END) / (ICON_BACK_1_3 - CLOSE_END))
        t < ICON_BACK_1_0 -> 1.3f - 0.3f * ((t - ICON_BACK_1_3) / (ICON_BACK_1_0 - ICON_BACK_1_3))
        else -> 1f
    }

    fun cardBrightness(t: Float): Float = when {
        t < CARD_IN -> 0.65f
        t < 213f -> 0.65f + 0.35f * (1f - (1f - (t - CARD_IN) / (213f - CARD_IN)).pow(1.5f))
        t < 280f -> 1f
        else -> (1f - 0.32f * clamp01((t - 280f) / 82f))
    }

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
