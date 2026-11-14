package com.example.pixellauncher

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout

class DeskItem(var col: Int, var row: Int, var folder: Boolean, var name: String, val keys: MutableList<String>)

class FolderIcon(c: Context, private val icons: List<Drawable>) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    init { paint.color = 0x55FFFFFF }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        box.set(0f, 0f, w, height.toFloat())
        cv.drawRoundRect(box, w * 0.28f, w * 0.28f, paint)
        val pad = w * 0.14f
        val cell = (w - pad * 2) / 2f
        val ic = cell * 0.88f
        for (i in 0 until minOf(4, icons.size)) {
            val x = pad + (i % 2) * cell + (cell - ic) / 2f
            val y = pad + (i / 2) * cell + (cell - ic) / 2f
            val d = icons[i]
            d.setBounds(x.toInt(), y.toInt(), (x + ic).toInt(), (y + ic).toInt())
            d.draw(cv)
        }
    }
}

class Desk(c: Context, private val prefs: Prefs,
           private val onOpen: (LauncherApp, View) -> Unit,
           private val onDrag: (DeskItem, View) -> Unit) : FrameLayout(c) {
    val items = ArrayList<DeskItem>()
    private var apps = listOf<LauncherApp>()

    init {
        for (s in prefs.desk.split(";")) {
            val p = s.split("|")
            if (p.size == 5) {
                val col = p[0].toIntOrNull()
                val row = p[1].toIntOrNull()
                if (col != null && row != null) {
                    items.add(DeskItem(col, row, p[2] == "F", p[3], p[4].split(",").filter { it.isNotEmpty() }.toMutableList()))
                }
            }
        }
    }

    private fun save() {
        prefs.desk = items.joinToString(";") {
            it.col.toString() + "|" + it.row + "|" + (if (it.folder) "F" else "A") + "|" +
                it.name.replace(Regex("[;|,]"), " ") + "|" + it.keys.joinToString(",")
        }
    }

    private fun find(k: String): LauncherApp? = apps.firstOrNull { it.key == k }
    private fun minCell(): Int = context.dp(prefs.iconSize + (if (prefs.labels == 1) 38 else 18))
    private fun rowsEff(): Int = minOf(prefs.rows, maxOf(1, height / minCell()))

    fun setApps(list: List<LauncherApp>) {
        apps = list
        rebuild()
    }

    private fun free(col: Int, row: Int, skip: DeskItem?): IntArray? {
        var best: IntArray? = null
        var bd = Int.MAX_VALUE
        for (rr in 0 until rowsEff()) {
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

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (h > 0 && h != oh) post { reconfigure() }
    }

    fun rebuild() {
        removeAllViews()
        if (apps.isEmpty()) return
        val gone = ArrayList<DeskItem>()
        for (x in items) {
            x.keys.removeAll { k -> find(k) == null }
            if (x.keys.isEmpty()) gone.add(x) else if (x.keys.size == 1) x.folder = false
        }
        items.removeAll(gone)
        for (item in items) {
            val v: View = if (item.folder) folderCell(item) else {
                val a = find(item.keys[0])!!
                makeCell(context, a, true, { _, cell -> onDrag(item, cell) }, onOpen)
            }
            v.tag = item
            addView(v)
        }
        requestLayout()
    }

    private fun folderCell(item: DeskItem): View {
        val ll = LinearLayout(context)
        ll.orientation = LinearLayout.VERTICAL
        ll.gravity = Gravity.CENTER
        ll.setPadding(0, context.dp(6), 0, context.dp(6))
        val sz = context.dp(prefs.iconSize)
        ll.addView(FolderIcon(context, item.keys.mapNotNull { find(it)?.icon }), LinearLayout.LayoutParams(sz, sz))
        if (prefs.labels == 1) {
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.topMargin = context.dp(4)
            ll.addView(labelView(context, item.name), lp)
        }
        ll.setOnClickListener { showFolder(item, ll.getChildAt(0)) }
        ll.setOnLongClickListener { onDrag(item, ll); true }
        return ll
    }

    private fun showFolder(item: DeskItem, origin: View) {
        val box = LinearLayout(context)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(8))
        val bg = GradientDrawable()
        bg.setColor(0xF0202124.toInt())
        bg.cornerRadius = context.dp(28).toFloat()
        box.background = bg
        val et = EditText(context)
        et.setText(item.name)
        et.setSingleLine()
        et.setTextColor(Color.WHITE)
        et.gravity = Gravity.CENTER
        et.textSize = 18f
        box.addView(et)
        val g = GridLayout(context)
        g.columnCount = 4
        box.addView(g)
        val dlg = AlertDialog.Builder(context).setView(box).create()
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dlg.window?.setWindowAnimations(0)
        for (k in item.keys.toList()) {
            val a = find(k) ?: continue
            val cell = makeCell(context, a, true, { x, _ -> dlg.dismiss(); takeOut(item, x) }) { x, _ ->
                dlg.dismiss()
                onOpen(x, origin)
            }
            val lp = GridLayout.LayoutParams()
            lp.width = context.dp(72)
            g.addView(cell, lp)
        }
        dlg.setOnDismissListener {
            val n = et.text.toString().trim()
            val nn = if (n.isEmpty()) L(context, "Папка", "Folder") else n
            if (nn != item.name) {
                item.name = nn
                save()
                post { rebuild() }
            }
        }
        box.alpha = 0f
        dlg.show()
        box.post {
            val a = IntArray(2)
            val o = IntArray(2)
            box.getLocationOnScreen(a)
            origin.getLocationOnScreen(o)
            box.pivotX = o[0] + origin.width / 2f - a[0]
            box.pivotY = o[1] + origin.height / 2f - a[1]
            box.scaleX = 0.3f
            box.scaleY = 0.3f
            box.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(prefs.speed.toLong())
                .setInterpolator(curveOf(prefs.curve)).start()
        }
    }

    private fun takeOut(item: DeskItem, a: LauncherApp) {
        item.keys.remove(a.key)
        val pos = free(item.col, item.row, null)
        if (pos != null) items.add(DeskItem(pos[0], pos[1], false, "", mutableListOf(a.key)))
        save()
        post { rebuild() }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cw = ((r - l) / prefs.cols).coerceAtLeast(1)
        val rh = ((b - t) / rowsEff()).coerceAtLeast(1)
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            val item = v.tag as DeskItem
            v.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(rh, MeasureSpec.EXACTLY))
            v.layout(item.col * cw, item.row * rh, (item.col + 1) * cw, (item.row + 1) * rh)
        }
    }

    fun drop(app: LauncherApp?, item: DeskItem?, x: Float, y: Float) {
        val cw = (width / prefs.cols).coerceAtLeast(1)
        val rh = (height / rowsEff()).coerceAtLeast(1)
        val col = (x / cw).toInt().coerceIn(0, prefs.cols - 1)
        val row = (y / rh).toInt().coerceIn(0, rowsEff() - 1)
        var found = item
        if (found == null && app != null) {
            found = items.firstOrNull { !it.folder && it.keys[0] == app.key }
            if (found == null) found = DeskItem(col, row, false, "", mutableListOf(app.key))
        }
        val d = found ?: return
        val target = items.firstOrNull { it !== d && it.col == col && it.row == row }
        if (target != null && !d.folder) {
            if (!target.folder) {
                target.folder = true
                target.name = L(context, "Папка", "Folder")
            }
            target.keys.add(d.keys[0])
            items.remove(d)
        } else {
            val pos = free(col, row, d) ?: return
            d.col = pos[0]
            d.row = pos[1]
            if (!items.contains(d)) items.add(d)
        }
        save()
        post { rebuild() }
    }

    fun removeItem(item: DeskItem) {
        items.remove(item)
        save()
        post { rebuild() }
    }
}
