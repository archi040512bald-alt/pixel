package com.example.pixellauncher

import android.content.Context
import android.view.View

fun sourceIcon(c: Context, a: LauncherApp, like: View): View {
    val v = ShapedIcon(c, a.icon, Prefs(c).shape)
    v.layout(0, 0, like.width, like.height)
    return v
}
