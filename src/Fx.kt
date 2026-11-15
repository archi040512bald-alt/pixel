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
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import java.util.Locale

fun L(c: Context, ru: String, en: String): String {
    val l = Prefs(c).lang
    val isRu = if (l.isEmpty()) Locale.getDefault().language == "ru" else l == "ru"
    return if (isRu) ru else en
}

fun presetName(c: Context, i: Int): String = when (i) {
    1 -> "HyperOS  NEW"
    2 -> "iOS  Soon"
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

fun curveOf(i: Int): Interpolator = when (i) {
    1 -> PathInterpolator(0.2f, 1.1f, 0.3f, 1f)
    2 -> PathInterpolator(0.22f, 1f, 0.36f, 1f)
    else -> PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
}

object Fx {
    private var cur = 0f
    private var blurAnim: ValueAnimator? = null

    private fun hyper(p: Prefs): Boolean = p.preset == 1

    private fun setBlur(a: Activity, v: View, p: Prefs, r: Float) {
        if (Build.VERSION.SDK_INT < 31) return
        if (!hyper(p) && a.windowManager.isCrossWindowBlurEnabled) {
            a.window.setBackgroundBlurRadius(r.toInt())
        } else {
            v.setRenderEffect(if (r < 1f) null else RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP))
        }
    }

    private fun blurTo(a: Activity, v: View, p: Prefs, on: Boolean, ms: Long) {
        if (Build.VERSION.SDK_INT < 31) return
        val target = if (on && p.blur == 1) p.blurR * a.resources.displayMetrics.density else 0f
        blurAnim?.cancel()
        val va = ValueAnimator.ofFloat(cur, target)
        va.duration = ms
        va.addUpdateListener {
            cur = it.animatedValue as Float
            setBlur(a, v, p, cur)
        }
        blurAnim = va
        va.start()
    }

    fun out(a: Activity, p: Prefs, target: View, home: View) {
        val s = 1f - p.zoom / 100f
        val al = if (hyper(p)) 0.55f else 0f
        target.animate().scaleX(s).scaleY(s).alpha(al).setDuration(p.speed.toLong())
            .setInterpolator(curveOf(p.curve)).start()
        blurTo(a, home, p, true, p.speed.toLong())
    }

    fun back(a: Activity, p: Prefs, home: View, drawer: View, progress: Float) {
        val s = 1f - 0.08f * progress
        val ms = (p.speed * 0.85f).toLong()
        val ip = curveOf(p.curve)
        home.animate().scaleX(s).scaleY(s).alpha(1f - progress).setDuration(ms).setInterpolator(ip).start()
        drawer.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(ms).setInterpolator(ip).start()
        blurTo(a, home, p, false, ms)
    }
}
