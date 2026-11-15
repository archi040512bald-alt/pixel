package com.example.pixellauncher

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class AppMenu(private val act: Activity, private val root: FrameLayout) {
    var targets: List<View> = emptyList()
    private var layer: FrameLayout? = null
    val showing: Boolean get() = layer != null

    private fun dp(v: Int): Int = act.dp(v)

    private fun blur(on: Boolean) {
        if (Build.VERSION.SDK_INT < 31) return
        val r = 16f * act.resources.displayMetrics.density
        for (v in targets) {
            v.setRenderEffect(if (on) RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP) else null)
        }
    }

    fun hide() {
        val l = layer ?: return
        layer = null
        root.removeView(l)
        blur(false)
    }

    fun show(app: LauncherApp, cell: View, onRemove: (() -> Unit)?) {
        hide()
        val loc = IntArray(2)
        cell.getLocationOnScreen(loc)
        val sw = root.width
        val sh = root.height
        val lay = FrameLayout(act)
        lay.setBackgroundColor(0x40000000)
        lay.setOnClickListener { hide() }

        val copy = makeCell(act, app, true) { _, _ -> }
        val cp = FrameLayout.LayoutParams(cell.width, cell.height)
        cp.leftMargin = loc[0]
        cp.topMargin = loc[1]
        lay.addView(copy, cp)

        val card = LinearLayout(act)
        card.orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable()
        bg.setColor(0xFFE6ECF2.toInt())
        bg.cornerRadius = dp(28).toFloat()
        card.background = bg
        card.setPadding(dp(8), dp(8), dp(8), dp(8))
        card.minimumWidth = dp(190)
        card.elevation = dp(8).toFloat()

        fun entry(kind: String, label: String, red: Boolean, tap: () -> Unit) {
            val row = LinearLayout(act)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(6), dp(6), dp(14), dp(6))
            val g = GlyphView(act, kind, Color.WHITE, if (red) 0xFFB3261E.toInt() else 0xFF2B3440.toInt())
            row.addView(g, LinearLayout.LayoutParams(dp(36), dp(36)))
            val t = TextView(act)
            t.text = label
            t.textSize = 15f
            t.setTextColor(if (red) 0xFFC62828.toInt() else 0xFF1C2229.toInt())
            t.setPadding(dp(14), 0, 0, 0)
            row.addView(t)
            row.setOnClickListener {
                hide()
                tap()
            }
            card.addView(row)
        }

        entry("info", L(act, "О приложении", "App info"), false) {
            act.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg)))
        }
        var system = false
        try {
            system = (act.packageManager.getApplicationInfo(app.pkg, 0).flags and
                (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
        } catch (e: Exception) {
        }
        if (!system) {
            entry("trash", L(act, "Удалить", "Uninstall"), true) {
                act.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg)))
            }
        }
        if (onRemove != null) {
            entry("close", L(act, "Убрать с экрана", "Remove from screen"), true) { onRemove() }
        }

        card.measure(View.MeasureSpec.makeMeasureSpec(dp(260), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val cw = card.measuredWidth
        val ch = card.measuredHeight
        var x = loc[0] + cell.width + dp(8)
        if (x + cw > sw - dp(12)) x = loc[0] - cw - dp(8)
        x = x.coerceIn(dp(12), maxOf(dp(12), sw - cw - dp(12)))
        var y = loc[1]
        if (y + ch > sh - dp(24)) y = sh - ch - dp(24)
        y = maxOf(dp(48), y)
        val lp = FrameLayout.LayoutParams(cw, ch)
        lp.leftMargin = x
        lp.topMargin = y
        lay.addView(card, lp)

        root.addView(lay, FrameLayout.LayoutParams(-1, -1))
        layer = lay
        blur(true)
        card.alpha = 0f
        card.scaleX = 0.8f
        card.scaleY = 0.8f
        card.pivotX = if (x > loc[0]) 0f else cw.toFloat()
        card.pivotY = 0f
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()
    }
}
