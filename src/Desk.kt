package com.example.pixellauncher

import android.content.Context
import android.view.View
import android.widget.FrameLayout

class DeskItem(val pkg: String, val cls: String, var col: Int, var row: Int)

class Desk(c: Context, private val prefs: Prefs,
           private val onOpen: (LauncherApp, View) -> Unit,
           private val onDrag: (LauncherApp, View) -> Unit) : FrameLayout(c) {
    private val cols = 5
    private val items = ArrayList<DeskItem>()
    private var apps = listOf<LauncherApp>()
    private val rowH = c.dp(92)
    private val topPad = c.dp(120)

    init {
        for (s in prefs.desk.split(";")) {
            val p = s.split("|")
            if (p.size == 4) items.add(DeskItem(p[0], p[1], p[2].toInt(), p[3].toInt()))
        }
    }

    fun setApps(list: List<LauncherApp>) {
        apps = list
        rebuild()
    }

    private fun save() {
        prefs.desk = items.joinToString(";") { it.pkg + "|" + it.cls + "|" + it.col + "|" + it.row }
    }

    fun rebuild() {
        removeAllViews()
        for (item in items.toList()) {
            val a = apps.firstOrNull { x -> x.pkg == item.pkg && x.cls == item.cls }
            if (a == null) {
                items.remove(item)
                continue
            }
            val cell = makeCell(context, a, true, { x, v -> onDrag(x, v) }, onOpen)
            cell.tag = item
            addView(cell)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cw = (r - l) / cols
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            val item = v.tag as DeskItem
            v.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(rowH, MeasureSpec.EXACTLY))
            v.layout(item.col * cw, topPad + item.row * rowH,
                (item.col + 1) * cw, topPad + (item.row + 1) * rowH)
        }
    }

    fun drop(a: LauncherApp, x: Float, y: Float) {
        val cw = (width / cols).coerceAtLeast(1)
        val maxRow = ((height - context.dp(170) - topPad) / rowH - 1).coerceAtLeast(0)
        val col = (x / cw).toInt().coerceIn(0, cols - 1)
        val row = ((y - topPad) / rowH).toInt().coerceIn(0, maxRow)
        val existing = items.firstOrNull { it.pkg == a.pkg && it.cls == a.cls }
        var bestCol = -1
        var bestRow = -1
        var bd = Int.MAX_VALUE
        for (rr in 0..maxRow) {
            for (cc in 0 until cols) {
                if (items.any { it !== existing && it.col == cc && it.row == rr }) continue
                val d = (rr - row) * (rr - row) + (cc - col) * (cc - col)
                if (d < bd) {
                    bd = d
                    bestCol = cc
                    bestRow = rr
                }
            }
        }
        if (bestCol < 0) return
        if (existing != null) {
            existing.col = bestCol
            existing.row = bestRow
        } else {
            items.add(DeskItem(a.pkg, a.cls, bestCol, bestRow))
        }
        save()
        post { rebuild() }
    }

    fun removeApp(a: LauncherApp) {
        items.removeAll { it.pkg == a.pkg && it.cls == a.cls }
        save()
        post { rebuild() }
    }
}
