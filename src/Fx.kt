package com.example.pixellauncher

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import java.util.Locale
import kotlin.math.pow

fun L(c: Context, ru: String, en: String): String {
    val l = Prefs(c).lang
    val isRu = if (l.isEmpty()) Locale.getDefault().language == "ru" else l == "ru"
    return if (isRu) ru else en
}

fun presetName(c: Context, i: Int): String = when (i) {
    1 -> "HyperOS  NEW"
    2 -> "Pixel  NEW"
    3 -> "iOS  Soon"
    4 -> "MagicOS  NEW"
    else -> L(c, "Стандарт", "Standard")
}

fun tagged(s: String): CharSequence {
    val tag = if (s.endsWith("NEW")) "NEW" else if (s.endsWith("Soon")) "Soon" else return s
    val sp = SpannableString(s)
    val st = s.length - tag.length
    sp.setSpan(StyleSpan(Typeface.BOLD), st, s.length, 0)
    sp.setSpan(RelativeSizeSpan(0.8f), st, s.length, 0)
    sp.setSpan(ForegroundColorSpan(if (tag == "NEW") 0xFF34C759.toInt() else 0xFF9AA0A6.toInt()), st, s.length, 0)
    return sp
}

// HyperOS: замеры по видео. Открытие p = 1 - (1 - u)^1.7, без пружинного перелёта.
fun curveOf(i: Int): Interpolator = when (i) {
    1 -> Interpolator { u -> 1f - (1f - u).pow(HyperSpec.OPEN_POWER) }
    2 -> PathInterpolator(0.22f, 1f, 0.36f, 1f)
    else -> PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
}

// Кривая закрытия: у HyperOS q = 1 - u^1.45 (разгон), у остальных как при открытии.
fun closeCurveOf(i: Int): Interpolator = when (i) {
    1 -> Interpolator { u -> u.pow(HyperSpec.CLOSE_POWER) }
    else -> curveOf(i)
}

object Fx {
    private var cur = 0f
    private var blurAnim: ValueAnimator? = null
    private var lastQ = -1f

    private fun hyper(p: Prefs): Boolean = p.preset == 1
    private fun pixel(p: Prefs): Boolean = p.preset == 2
    private fun magic(p: Prefs): Boolean = p.preset == MagicSpec.ID

    private fun setBlur(a: Activity, v: View, p: Prefs, r: Float) {
        if (Build.VERSION.SDK_INT < 31) return
        // не пересоздаём эффект, пока радиус не изменился хотя бы на полпикселя (меньше нагрузки на каждом кадре)
        val q = (r * 2f).toInt() / 2f
        if (q == lastQ) return
        lastQ = q
        if (!hyper(p) && !pixel(p) && !magic(p) && a.windowManager.isCrossWindowBlurEnabled) {
            a.window.setBackgroundBlurRadius(r.toInt())
        } else {
            v.setRenderEffect(if (r < 1f) null else RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP))
        }
    }

    private fun blurTo(a: Activity, v: View, p: Prefs, on: Boolean, ms: Long, delay: Long = 0L) {
        if (Build.VERSION.SDK_INT < 31) return
        val target = if (on && p.blur == 1) {
            if (pixel(p)) PixelSpec.BLUR_SIGMA_W * a.resources.displayMetrics.widthPixels
            else if (hyper(p)) HyperSpec.BLUR_SIGMA_W * a.resources.displayMetrics.widthPixels
            else p.blurR * a.resources.displayMetrics.density
        } else 0f
        blurAnim?.cancel()
        val va = ValueAnimator.ofFloat(cur, target)
        va.duration = ms
        va.startDelay = delay
        if (hyper(p) || pixel(p)) va.interpolator = DecelerateInterpolator(1f)
        va.addUpdateListener {
            cur = it.animatedValue as Float
            setBlur(a, v, p, cur)
        }
        blurAnim = va
        va.start()
    }

    // MagicOS: размытие, затемнение и зум фона ведёт MagicEngine (по прогрессу карточки, кадр за кадром)
    fun magicFrame(a: Activity, p: Prefs, home: View, pr: Float) {
        if (!magic(p)) return
        val k = MagicSpec.clamp01(pr)
        val z = 1f - p.zoom / 100f * k
        home.scaleX = z
        home.scaleY = z
        home.alpha = 1f - MagicSpec.HOME_DIM * k
        if (p.blur == 1) setBlur(a, home, p, MagicSpec.blurSigma(k, a.resources.displayMetrics.widthPixels.toFloat()))
    }

    fun out(a: Activity, p: Prefs, target: View, home: View) {
        if (magic(p)) return
        if (pixel(p)) {
            // Pixel: домашний экран размывается и темнеет, пока окно открыто (зум по настройке "Зум", по умолчанию 0)
            val z = 1f - p.zoom / 100f
            target.animate().scaleX(z).scaleY(z).alpha(PixelSpec.DIM).setStartDelay(0).setDuration(200)
                .setInterpolator(DecelerateInterpolator(1.5f)).start()
            blurTo(a, home, p, true, 200L, 0L)
            return
        }
        if (hyper(p)) {
            // Блюр и тусклость как в видео (за 87 мс), зум фона по настройке "Зум" идёт вместе с ростом карточки.
            val st = HyperSpec.BLUR_START.toLong()
            val z = 1f - p.zoom / 100f
            val d = (HyperSpec.OPEN_END - HyperSpec.CARD_IN).toLong()
            target.animate().scaleX(z).scaleY(z).alpha(HyperSpec.ICON_DIM_MIN)
                .setStartDelay(st).setDuration(d)
                .setInterpolator(DecelerateInterpolator(HyperSpec.OPEN_POWER / 2f)).start()
            blurTo(a, home, p, true, (HyperSpec.BLUR_END - HyperSpec.BLUR_START).toLong(), st)
            return
        }
        val s = 1f - p.zoom / 100f
        target.animate().scaleX(s).scaleY(s).alpha(0f).setStartDelay(0).setDuration(p.speed.toLong())
            .setInterpolator(curveOf(p.curve)).start()
        blurTo(a, home, p, true, p.speed.toLong())
    }

    fun back(a: Activity, p: Prefs, home: View, drawer: View, progress: Float) {
        if (magic(p)) return
        if (pixel(p)) {
            // Pixel: после закрытия окна (около 120 мс) фон за 130 мс становится резким
            val ip2 = DecelerateInterpolator(1.5f)
            val ms2 = PixelSpec.SETTLE_MS.toLong()
            home.animate().scaleX(1f - 0.08f * progress).scaleY(1f - 0.08f * progress).alpha(1f - progress)
                .setStartDelay(120).setDuration(ms2).setInterpolator(ip2).start()
            drawer.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(0).setDuration(ms2).setInterpolator(ip2).start()
            blurTo(a, home, p, false, ms2, 120L)
            return
        }
        val s = 1f - 0.08f * progress
        // Скорость возврата резкости в видео не попала в кадр, берём ту же длительность 100 мс.
        val ms = if (hyper(p)) (HyperSpec.CLOSE_MS * 1.35f).toLong() else (p.speed * 0.85f).toLong()
        val ip: Interpolator = if (hyper(p)) DecelerateInterpolator(1f) else curveOf(p.curve)
        home.animate().scaleX(s).scaleY(s).alpha(1f - progress).setStartDelay(0).setDuration(ms).setInterpolator(ip).start()
        drawer.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(0).setDuration(ms).setInterpolator(ip).start()
        blurTo(a, home, p, false, ms)
    }
}
