package com.example.pixellauncher

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class CrashActivity : Activity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val d = resources.displayMetrics.density
        val t = intent.getStringExtra("t") ?: "нет данных"
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xFF101418.toInt())
        root.setPadding((16 * d).toInt(), (40 * d).toInt(), (16 * d).toInt(), (16 * d).toInt())
        val h = TextView(this)
        h.text = "Лаунчер упал. Сделайте скриншот этого экрана и пришлите его."
        h.setTextColor(0xFFFFFFFF.toInt())
        h.textSize = 18f
        root.addView(h)
        val sv = ScrollView(this)
        val tv = TextView(this)
        tv.text = t.take(3500)
        tv.setTextColor(0xFFFFB4AB.toInt())
        tv.textSize = 11f
        tv.setTextIsSelectable(true)
        sv.addView(tv)
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        val b = Button(this)
        b.text = "Закрыть"
        b.setOnClickListener { finish() }
        root.addView(b)
        setContentView(root)
    }
}
