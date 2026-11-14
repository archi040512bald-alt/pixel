package com.example.pixellauncher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class LauncherApp(val label: String, val pkg: String, val cls: String, val icon: Drawable)

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun queryApps(c: Context): List<LauncherApp> {
    val pm = c.packageManager
    val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val list = ArrayList<LauncherApp>()
    for (ri in pm.queryIntentActivities(q, 0)) {
        if (ri.activityInfo.packageName == c.packageName) continue
        list.add(LauncherApp(ri.loadLabel(pm).toString(), ri.activityInfo.packageName, ri.activityInfo.name, ri.loadIcon(pm)))
    }
    return list.sortedBy { it.label.lowercase() }
}

fun makeCell(c: Context, a: LauncherApp, label: Boolean, open: (LauncherApp, View) -> Unit): View {
    val ll = LinearLayout(c)
    ll.orientation = LinearLayout.VERTICAL
    ll.gravity = Gravity.CENTER
    ll.setPadding(0, c.dp(6), 0, c.dp(6))
    val icon = ImageView(c)
    icon.setImageDrawable(a.icon)
    ll.addView(icon, LinearLayout.LayoutParams(c.dp(56), c.dp(56)))
    if (label) {
        val t = TextView(c)
        t.text = a.label
        t.setTextColor(Color.WHITE)
        t.textSize = 12f
        t.maxLines = 1
        t.ellipsize = android.text.TextUtils.TruncateAt.END
        t.gravity = Gravity.CENTER
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = c.dp(4)
        ll.addView(t, lp)
    }
    ll.setOnClickListener { open(a, icon) }
    ll.setOnLongClickListener {
        c.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)))
        true
    }
    return ll
}
