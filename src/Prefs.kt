package com.example.pixellauncher

import android.content.Context
import android.content.SharedPreferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class Prefs(c: Context) {
    private val sp = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private var l: SharedPreferences.OnSharedPreferenceChangeListener? = null

    private fun int(k: String, d: Int): ReadWriteProperty<Any?, Int> = object : ReadWriteProperty<Any?, Int> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Int = sp.getInt(k, d)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) { sp.edit().putInt(k, value).apply() }
    }

    private fun str(k: String): ReadWriteProperty<Any?, String> = object : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): String = sp.getString(k, "") ?: ""
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) { sp.edit().putString(k, value).apply() }
    }

    var radius by int("radius", 40)
    var speed by int("speed", 340)
    var pause by int("pause", 0)
    var blur by int("blur", 0)
    var blurR by int("blurR", 12)
    var zoom by int("zoom", 8)
    var curve by int("curve", 0)
    var preset by int("preset", 0)
    var shape by int("shape", 0)
    var cols by int("cols", 5)
    var rows by int("rows", 5)
    var dockBg by int("dockBg", 0)
    var dockR by int("dockR", 28)
    var iconSize by int("iconSize", 56)
    var labels by int("labels", 1)
    var search by int("search", 1)
    var landOn by int("landOn", 1)
    var landSpeed by int("landSpeed", 600)
    var landR by int("landR", 40)
    var drawer by int("drawer", 1)
    var dots by int("dots", 1)
    var iconStyle by int("iconStyle", 0)
    var iconSeed by int("iconSeed", 0)
    var iconAll by int("iconAll", 0)
    var badges by int("badges", 1)
    var searchStyle by int("searchStyle", 0)
    var iconTone by int("iconTone", 0)
    var searchColor by int("searchColor", 0)
    var searchSeed by int("searchSeed", 0)
    var searchIcon by int("searchIcon", 0)
    var pack by str("pack")
    var desk by str("desk")
    var lang by str("lang")

    fun applyPreset(i: Int) {
        val d = when (i) {
            1 -> intArrayOf(32, 420, 0, 1, 16, 6, 1)
            2 -> intArrayOf(40, 300, 0, 1, 12, 0, 0)
            3 -> intArrayOf(44, 400, 0, 0, 12, -10, 2)
            else -> intArrayOf(40, 340, 0, 0, 12, 8, 0)
        }
        preset = i
        radius = d[0]
        speed = d[1]
        pause = d[2]
        blur = d[3]
        blurR = d[4]
        zoom = d[5]
        curve = d[6]
    }

    fun listen(cb: (String) -> Unit) {
        val keys = setOf("shape", "cols", "rows", "pack", "lang", "dockBg", "dockR", "iconSize",
            "labels", "search", "drawer", "dots", "iconStyle", "iconSeed", "iconAll", "badges", "searchStyle", "iconTone", "searchColor", "searchSeed", "searchIcon")
        val x = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k != null && k in keys) cb(k)
        }
        l = x
        sp.registerOnSharedPreferenceChangeListener(x)
    }
}
