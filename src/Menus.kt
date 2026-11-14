package com.example.pixellauncher

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.PopupWindow
import android.widget.TextView

fun showPopup(a: Activity, root: View, x: Int, y: Int, onSettings: () -> Unit) {
    val tv = TextView(a)
    tv.text = L(a, "Настройки", "Settings")
    tv.textSize = 16f
    tv.setTextColor(Color.WHITE)
    tv.setPadding(a.dp(24), a.dp(14), a.dp(24), a.dp(14))
    val g = GradientDrawable()
    g.setColor(0xF0303134.toInt())
    g.cornerRadius = a.dp(20).toFloat()
    val pw = PopupWindow(tv, -2, -2, true)
    pw.setBackgroundDrawable(g)
    pw.elevation = a.dp(8).toFloat()
    tv.setOnClickListener {
        pw.dismiss()
        onSettings()
    }
    pw.showAtLocation(root, Gravity.NO_GRAVITY, x - a.dp(60), y - a.dp(70))
}
