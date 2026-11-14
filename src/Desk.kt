package com.example.pixellauncher

import android.content.Context
import android.view.View
import android.widget.FrameLayout

class DeskItem(val pkg: String, val cls: String, var col: Int, var row: Int)

class Desk(c: Context, private val prefs: Prefs,
           private val onOpen: (LauncherApp, View) -> Unit,
           private val onDrag: (LauncherApp, View) -> Unit) : FrameLayout(c) {
    private val items = ArrayList<DeskItem>()
    private var apps = listOf<LauncherApp>()
    private val topPad = c.dp(120)
    private val bottomPad = c.dp(170)

    init {
        for (s in prefs.desk.split(";")) {
            val p = s.split("|")
            if (p.size == 4) items.add(DeskItem(p[0], p[1], p[2].toInt(), p[3].toInt()))
        }
    }

    private fun rowH(): Int = ((height - topPad - bottomPad) / prefs.rows).coerceAtLeast(context.dp(64))

    fun setApps(list: List<LauncherApp>) {
        apps = list
        rebuild()
    }

    private fun save() {
        prefs.desk = items.joinToString(";") { it.pkg + "|" + it.cls + "|" + it.col + "|" + it.row }
    }

    private fun free(col: Int, row: Int, skip: DeskItem?): IntArray? {
        var best: IntArray? = null
        var bd = Int.MAX_VALUE
        for (rr in 0 until prefs.rows) {
            for (cc in 0 until prefs.cols) {
                if (items.any { it !== skip && it.col == cc && it.row == rr }) continue
                val d = (rr - row) * (rr - row) + (cc - col) * (cc - col)
                if (d < bd) {
                    bd = d
                    best = intArrayOf(cc, rr)
                }
            }
        }
        return best
    }

    fun reconfigure() {
        val old = items.toList()
        items.clear()
        for (x in old) {
            val pos = free(x.col, x.row, null) ?: continue
            x.col = pos[0]
            x.row = pos[1]
            items.add(x)
        }
        save()
        rebuild()
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
        requestLayout()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cw = ((r - l) / prefs.cols).coerceAtLeast(1)
        val rh = rowH()
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            val item = v.tag as DeskItem
            v.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(rh, MeasureSpec.EXACTLY))
            v.layout(item.col * cw, topPad + item.row * rh,
                (item.col + 1) * cw, topPad + (item.row + 1) * rh)
        }
    }

    fun drop(a: LauncherApp, x: Float, y: Float) {
        val cw = (width / prefs.cols).coerceAtLeast(1)
        val col = (x / cw).toInt().coerceIn(0, prefs.cols - 1)
        val row = ((y - topPad) / rowH()).toInt().coerceIn(0, prefs.rows - 1)
        val existing = items.firstOrNull { it.pkg == a.pkg && it.cls == a.cls }
        val pos = free(col, row, existing) ?: return
        if (existing != null) {
            existing.col = pos[0]
            existing.row = pos[1]
        } else {
            items.add(DeskItem(a.pkg, a.cls, pos[0], pos[1]))
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
