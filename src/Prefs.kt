package com.example.pixellauncher

import android.content.Context
import android.content.SharedPreferences

class Prefs(c: Context) {
    private val sp = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private var l: SharedPreferences.OnSharedPreferenceChangeListener? = null

    var radius: Int
        get() = sp.getInt("radius", 40)
        set(v) { sp.edit().putInt("radius", v).apply() }
    var speed: Int
        get() = sp.getInt("speed", 340)
        set(v) { sp.edit().putInt("speed", v).apply() }
    var pause: Int
        get() = sp.getInt("pause", 0)
        set(v) { sp.edit().putInt("pause", v).apply() }
    var shape: Int
        get() = sp.getInt("shape", 0)
        set(v) { sp.edit().putInt("shape", v).apply() }
    var cols: Int
        get() = sp.getInt("cols", 5)
        set(v) { sp.edit().putInt("cols", v).apply() }
    var rows: Int
        get() = sp.getInt("rows", 5)
        set(v) { sp.edit().putInt("rows", v).apply() }
    var pack: String
        get() = sp.getString("pack", "") ?: ""
        set(v) { sp.edit().putString("pack", v).apply() }
    var desk: String
        get() = sp.getString("desk", "") ?: ""
        set(v) { sp.edit().putString("desk", v).apply() }

    fun listen(cb: () -> Unit) {
        val x = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k == "shape" || k == "cols" || k == "rows" || k == "pack") cb()
        }
        l = x
        sp.registerOnSharedPreferenceChangeListener(x)
    }
}
