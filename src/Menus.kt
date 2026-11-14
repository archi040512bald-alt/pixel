package com.example.pixellauncher

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView

fun showPopup(a: Activity, root: View, x: Int, y: Int, onSettings: () -> Unit) {
    val tv = TextView(a)
    tv.text = "Настройки"
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

fun showSettings(a: Activity, p: Prefs, onShape: () -> Unit, onWall: () -> Unit, onGrid: () -> Unit) {
    val items = arrayOf("Анимации", "Иконки", "Сетка", "Обои")
    AlertDialog.Builder(a).setTitle("Настройки").setItems(items) { _, i ->
        when (i) {
            0 -> animDialog(a, p)
            1 -> shapeDialog(a, p, onShape)
            2 -> gridDialog(a, p, onGrid)
            else -> onWall()
        }
    }.show()
}

private fun slider(a: Activity, box: LinearLayout, name: String, min: Int, max: Int,
                   value: Int, unit: String, set: (Int) -> Unit) {
    val label = TextView(a)
    label.text = name + ": " + value + unit
    label.setPadding(0, a.dp(12), 0, 0)
    val bar = SeekBar(a)
    bar.max = max - min
    bar.progress = value - min
    bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) {
            label.text = name + ": " + (v + min) + unit
            set(v + min)
        }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    })
    box.addView(label)
    box.addView(bar)
}

private fun newBox(a: Activity): LinearLayout {
    val box = LinearLayout(a)
    box.orientation = LinearLayout.VERTICAL
    box.setPadding(a.dp(24), a.dp(8), a.dp(24), 0)
    return box
}

private fun animDialog(a: Activity, p: Prefs) {
    val box = newBox(a)
    slider(a, box, "Радиус углов", 0, 60, p.radius, " dp") { p.radius = it }
    slider(a, box, "Длительность", 150, 800, p.speed, " мс") { p.speed = it }
    slider(a, box, "Пауза перед запуском", 0, 500, p.pause, " мс") { p.pause = it }
    AlertDialog.Builder(a).setTitle("Анимации").setView(box).setPositiveButton("Готово", null).show()
}

private fun gridDialog(a: Activity, p: Prefs, onGrid: () -> Unit) {
    val box = newBox(a)
    slider(a, box, "Колонок", 3, 6, p.cols, "") { p.cols = it }
    slider(a, box, "Строк на рабочем столе", 3, 7, p.rows, "") { p.rows = it }
    AlertDialog.Builder(a).setTitle("Сетка").setView(box).setPositiveButton("Готово", null)
        .setOnDismissListener { onGrid() }.show()
}

private fun shapeDialog(a: Activity, p: Prefs, onShape: () -> Unit) {
    AlertDialog.Builder(a).setTitle("Форма иконок").setSingleChoiceItems(SHAPES, p.shape) { d, i ->
        p.shape = i
        d.dismiss()
        onShape()
    }.show()
}
