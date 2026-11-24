package com.example.pixellauncher

import android.animation.ValueAnimator
import android.appwidget.AppWidgetProviderInfo
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

class DeskItem(var col: Int, var row: Int, var folder: Boolean, var name: String,
               val keys: MutableList<String>, var page: Int = 0,
               var widget: Int = 0, var sc: Int = 1, var sr: Int = 1)

class FolderIcon(c: Context, private val icons: List<Drawable>,
                 private val insX: Float = 0f, private val insY: Float = 0f,
                 private val r0: Float = 0f, private val sz: Float = 0f) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    init { paint.color = 0x55FFFFFF }

    override fun onDraw(cv: Canvas) {
        val l = insX
        val t = insY
        val r = width - insX
        val b = height - insY
        val s = if (sz > 0f) sz else (r - l)
        val rad = if (r0 > 0f) r0 else s * 0.28f
        box.set(l, t, r, b)
        cv.drawRoundRect(box, rad, rad, paint)
        // мини-значки растут вместе с плиткой (по корню из площади), отступ остаётся прежним;
        // у папки 1x1 k = 1 и всё выглядит как раньше
        val tw = r - l
        val th = b - t
        val k = Math.sqrt((tw / s * th / s).toDouble()).toFloat().coerceAtLeast(1f)
        val pad = s * 0.14f
        val cell = s * 0.36f * k
        val ic = cell * 0.88f
        val cols = floor((tw - pad * 2) / cell + 0.02f).toInt().coerceAtLeast(1)
        val rows = floor((th - pad * 2) / cell + 0.02f).toInt().coerceAtLeast(1)
        val gx = l + (tw - cols * cell) / 2f
        val gy = t + (th - rows * cell) / 2f
        for (i in 0 until minOf(cols * rows, icons.size)) {
            val x = gx + (i % cols) * cell + (cell - ic) / 2f
            val y = gy + (i / cols) * cell + (cell - ic) / 2f
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
    var hide: Set<String> = emptySet()
    var sink: ((MotionEvent) -> Boolean)? = null
    var isSwiping: () -> Boolean = { false }
    var onPage: ((Int, Float) -> Unit)? = null
    var frac = 0f
        private set
    private var tempPages = 1
    private var flipAt = 0L
    private var fwd = 0L
    private var snapAnim: ValueAnimator? = null
    private val holders = HashMap<Int, WidgetHolder>()
    private val lastSize = HashMap<Int, Int>()

    init {
        for (s in prefs.desk.split(";")) {
            val p = s.split("|")
            if (p.size >= 5) {
                val col = p[0].toIntOrNull()
                val row = p[1].toIntOrNull()
                if (col != null && row != null) {
                    val pg = if (p.size > 5) (p[5].toIntOrNull() ?: 0) else 0
                    if (p[2] == "W") {
                        // виджет: в поле имени хранится "id.ширина.высота"
                        val w = p[3].split(".")
                        val id = w.getOrNull(0)?.toIntOrNull() ?: 0
                        if (id != 0) {
                            items.add(DeskItem(col, row, false, "", mutableListOf(), pg, id,
                                w.getOrNull(1)?.toIntOrNull() ?: 1, w.getOrNull(2)?.toIntOrNull() ?: 1))
                        }
                    } else {
                        val di = DeskItem(col, row, p[2] == "F", p[3], p[4].split(",").filter { it.isNotEmpty() }.toMutableList(), pg)
                        if (di.folder && p.size > 6) {
                            val sp = p[6].split(".")
                            di.sc = (sp.getOrNull(0)?.toIntOrNull() ?: 1).coerceAtLeast(1)
                            di.sr = (sp.getOrNull(1)?.toIntOrNull() ?: 1).coerceAtLeast(1)
                        }
                        items.add(di)
                    }
                }
            }
        }
    }

    private fun save() {
        prefs.desk = items.joinToString(";") {
            it.col.toString() + "|" + it.row + "|" + (if (it.widget != 0) "W" else if (it.folder) "F" else "A") + "|" +
                (if (it.widget != 0) it.widget.toString() + "." + it.sc + "." + it.sr else it.name.replace(Regex("[;|,]"), " ")) +
                "|" + it.keys.joinToString(",") + "|" + it.page +
                (if (it.folder && it.widget == 0 && (it.sc > 1 || it.sr > 1)) "|" + it.sc + "." + it.sr else "")
        }
    }

    private fun find(k: String): LauncherApp? = apps.firstOrNull { it.key == k }
    private fun minCell(): Int = context.dp(prefs.iconSize + (if (prefs.labels == 1) 38 else 18))
    private fun rowsEff(): Int = minOf(prefs.rows, maxOf(1, height / minCell()))

    // ячейка (c, r) занята элементом, учитывая размер виджета
    private fun occ(x: DeskItem, c: Int, r: Int): Boolean =
        c >= x.col && c < x.col + x.sc && r >= x.row && r < x.row + x.sr

    // ищет свободный прямоугольник sc x sr, ближайший к (col, row); страницы листаются вперёд
    private fun fitFree(sc: Int, sr: Int, col: Int, row: Int, page: Int, skip: DeskItem?): IntArray? {
        var pg = page
        while (pg < 20) {
            var best: IntArray? = null
            var bd = Int.MAX_VALUE
            for (rr in 0..(rowsEff() - sr)) {
                for (cc in 0..(prefs.cols - sc)) {
                    var ok = true
                    for (y in rr until rr + sr) {
                        for (x in cc until cc + sc) {
                            if (items.any { it !== skip && it.page == pg && occ(it, x, y) }) ok = false
                        }
                    }
                    if (!ok) continue
                    val d = (rr - row) * (rr - row) + (cc - col) * (cc - col)
                    if (d < bd) {
                        bd = d
                        best = intArrayOf(cc, rr, pg)
                    }
                }
            }
            if (best != null) return best
            pg++
        }
        return null
    }

    // поиск размера в ячейках по minWidth/minHeight виджета (в пикселях)
    fun spanOf(info: AppWidgetProviderInfo): IntArray {
        val cw = (width.coerceAtLeast(1) / prefs.cols).coerceAtLeast(1)
        val rh = (height.coerceAtLeast(1) / rowsEff()).coerceAtLeast(1)
        val sc = ceil((info.minWidth - 0.2f * cw) / cw).toInt()
        val sr = ceil((info.minHeight - 0.2f * rh) / rh).toInt()
        return intArrayOf(sc.coerceIn(1, prefs.cols), sr.coerceIn(1, rowsEff()))
    }

    fun addWidget(id: Int, sc: Int, sr: Int): Boolean {
        val pos = fitFree(sc, sr, 0, 0, page(), null) ?: return false
        items.add(DeskItem(pos[0], pos[1], false, "", mutableListOf(), pos[2], id, sc, sr))
        save()
        post { rebuild() }
        goTo(pos[2])
        return true
    }

    fun pages(): Int = maxOf(1, tempPages, (items.maxOfOrNull { it.page } ?: 0) + 1)
    fun page(): Int = frac.roundToInt().coerceIn(0, pages() - 1)

    fun setFrac(f: Float) {
        frac = f.coerceIn(0f, (pages() - 1).toFloat())
        applyScroll()
        onPage?.invoke(pages(), frac)
    }

    private fun applyScroll() {
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            val item = v.tag as DeskItem
            val off = item.page - frac
            v.translationX = off * width
            v.visibility = if (abs(off) < 1.05f) View.VISIBLE else View.INVISIBLE
        }
    }

    fun goTo(p: Int) {
        snapAnim?.cancel()
        val target = p.coerceIn(0, pages() - 1).toFloat()
        val va = ValueAnimator.ofFloat(frac, target)
        va.duration = 280
        va.interpolator = curveOf(0)
        va.addUpdateListener { setFrac(it.animatedValue as Float) }
        snapAnim = va
        va.start()
    }

    fun snap(vx: Float) {
        val t = when {
            vx < -700f -> floor(frac) + 1f
            vx > 700f -> ceil(frac) - 1f
            else -> frac.roundToInt().toFloat()
        }
        goTo(t.toInt())
    }

    fun edgeHover(x: Float) {
        val now = SystemClock.uptimeMillis()
        if (now - flipAt < 700) return
        val edge = context.dp(36)
        if (x < edge && page() > 0) {
            flipAt = now
            goTo(page() - 1)
        } else if (x > width - edge) {
            val p = page()
            if (p >= pages() - 1) {
                if (pages() >= 20 || items.none { it.page == p }) return
                tempPages = p + 2
            }
            flipAt = now
            goTo(p + 1)
        }
    }

    fun endDrag() {
        tempPages = 1
        goTo(minOf(page(), pages() - 1))
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val s = sink ?: return false
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) fwd = ev.downTime
        s(ev)
        return isSwiping()
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val s = sink ?: return false
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && ev.downTime == fwd) return true
        s(ev)
        return true
    }

    fun setApps(list: List<LauncherApp>) {
        apps = list
        rebuild()
    }

    private fun free(col: Int, row: Int, page: Int, skip: DeskItem?): IntArray {
        var pg = page
        while (true) {
            var best: IntArray? = null
            var bd = Int.MAX_VALUE
            for (rr in 0 until rowsEff()) {
                for (cc in 0 until prefs.cols) {
                    if (items.any { it !== skip && it.page == pg && occ(it, cc, rr) }) continue
                    val d = (rr - row) * (rr - row) + (cc - col) * (cc - col)
                    if (d < bd) {
                        bd = d
                        best = intArrayOf(cc, rr, pg)
                    }
                }
            }
            if (best != null) return best
            pg++
        }
    }

    private fun nextFree(): IntArray {
        var pg = 0
        while (true) {
            for (rr in 0 until rowsEff()) {
                for (cc in 0 until prefs.cols) {
                    if (items.none { it.page == pg && occ(it, cc, rr) }) return intArrayOf(cc, rr, pg)
                }
            }
            pg++
        }
    }

    fun reconfigure() {
        if (height == 0) return
        val old = items.toList()
        items.clear()
        for (x in old) {
            if (x.widget != 0) {
                x.sc = x.sc.coerceIn(1, prefs.cols)
                x.sr = x.sr.coerceIn(1, rowsEff())
                val wp = fitFree(x.sc, x.sr, x.col, x.row, x.page, null)
                if (wp != null) {
                    x.col = wp[0]
                    x.row = wp[1]
                    x.page = wp[2]
                    items.add(x)
                } else {
                    Widgets.delete(context, x.widget)
                }
                continue
            }
            if (x.folder && (x.sc > 1 || x.sr > 1)) {
                x.sc = x.sc.coerceIn(1, prefs.cols)
                x.sr = x.sr.coerceIn(1, rowsEff())
                val fp = fitFree(x.sc, x.sr, x.col, x.row, x.page, null)
                if (fp != null) {
                    x.col = fp[0]
                    x.row = fp[1]
                    x.page = fp[2]
                    items.add(x)
                    continue
                }
                x.sc = 1
                x.sr = 1
            }
            val pos = free(x.col, x.row, x.page, null)
            x.col = pos[0]
            x.row = pos[1]
            x.page = pos[2]
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
            if (x.widget != 0) {
                if (Widgets.mgr(context).getAppWidgetInfo(x.widget) == null) gone.add(x)
                continue
            }
            x.keys.removeAll { k -> find(k) == null }
            if (x.keys.isEmpty()) gone.add(x) else if (x.keys.size == 1) {
                x.folder = false
                x.sc = 1
                x.sr = 1
            }
        }
        items.removeAll(gone)
        if (prefs.drawer == 0 && height > 0) {
            val have = HashSet<String>()
            for (x in items) have.addAll(x.keys)
            var added = false
            for (a in apps) {
                if (a.key in have || a.pkg in hide) continue
                val pos = nextFree()
                items.add(DeskItem(pos[0], pos[1], false, "", mutableListOf(a.key), pos[2]))
                added = true
            }
            if (added) save()
        }
        for (item in items) {
            val v: View = if (item.widget != 0) {
                widgetHolder(item) ?: continue
            } else if (item.folder) folderCell(item) else {
                val a = find(item.keys[0])!!
                makeCell(context, a, true, { _, cell -> onDrag(item, cell) }, onOpen)
            }
            v.tag = item
            addView(v)
        }
        frac = frac.coerceIn(0f, (pages() - 1).toFloat())
        requestLayout()
        applyScroll()
        onPage?.invoke(pages(), frac)
    }

    private fun widgetHolder(item: DeskItem): View? {
        val info = Widgets.mgr(context).getAppWidgetInfo(item.widget) ?: return null
        var h = holders[item.widget]
        if (h == null) {
            val hv = try {
                Widgets.host(context).createView(context, item.widget, info)
            } catch (e: Exception) {
                return null
            }
            h = WidgetHolder(context, hv)
            holders[item.widget] = h
        }
        (h.parent as? ViewGroup)?.removeView(h)
        val holder = h
        holder.onLong = { onDrag(item, holder) }
        return holder
    }

    private fun folderCell(item: DeskItem): View {
        val sz = context.dp(prefs.iconSize)
        val cw = (width / prefs.cols).coerceAtLeast(1)
        val rh = (height / rowsEff()).coerceAtLeast(1)
        val ll = FolderCell(context, item)
        ll.desk = this
        ll.orientation = LinearLayout.VERTICAL
        ll.gravity = Gravity.CENTER
        ll.setPadding(0, context.dp(6), 0, context.dp(6))
        var lab: View? = null
        var extra = 0
        if (prefs.labels == 1) {
            lab = labelView(context, item.name)
            lab.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            extra = context.dp(4) + lab.measuredHeight
        }
        // плитка 1x1 ровно sz x sz по центру, большая папка растёт на целые ячейки
        val areaH1 = rh - context.dp(12) - extra
        ll.insX = if (width > 0) ((cw - sz) / 2f).coerceAtLeast(0f) else 0f
        ll.insY = if (height > 0) ((areaH1 - sz) / 2f).coerceAtLeast(0f) else 0f
        ll.extra = extra
        ll.sz = sz.toFloat()
        ll.r0 = sz * 0.28f
        val icon = FolderIcon(context, item.keys.mapNotNull { find(it)?.icon }, ll.insX, ll.insY, ll.r0, ll.sz)
        ll.addView(icon, LinearLayout.LayoutParams(-1, 0, 1f))
        if (lab != null) {
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.topMargin = context.dp(4)
            ll.addView(lab, lp)
        }
        ll.setOnClickListener { showFolder(item, ll.getChildAt(0)) }
        ll.setOnLongClickListener { onDrag(item, ll); true }
        ll.setEdit(editing, false)
        return ll
    }

    // ---------- растягивание папки за линию в углу (режим правки) ----------

    var editing = false
        set(v) {
            field = v
            for (i in 0 until childCount) (getChildAt(i) as? FolderCell)?.setEdit(v, true)
        }

    private var rsItem: DeskItem? = null
    private var rsCell: FolderCell? = null
    private var rsW0 = 0
    private var rsH0 = 0
    private var liveW = 0
    private var liveH = 0
    private var snapC = 1
    private var snapR = 1
    private var ending = false
    private val ghost = RectF()
    private var ghostOn = false
    private var ghostR = 0f
    private var ghostAnim: ValueAnimator? = null
    private val ghostFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ghostLine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ghostBox = RectF()

    init {
        ghostFill.color = 0x26FFFFFF
        ghostLine.style = Paint.Style.STROKE
        ghostLine.color = 0x80FFFFFF.toInt()
        ghostLine.strokeWidth = 2f * resources.displayMetrics.density
    }

    private fun areaFree(item: DeskItem, sc: Int, sr: Int): Boolean {
        for (y in item.row until item.row + sr) {
            for (x in item.col until item.col + sc) {
                if (items.any { it !== item && it.page == item.page && occ(it, x, y) }) return false
            }
        }
        return true
    }

    private fun ghostTarget(item: DeskItem, sc: Int, sr: Int): RectF {
        val cw = (width / prefs.cols).coerceAtLeast(1)
        val rh = (height / rowsEff()).coerceAtLeast(1)
        val w = cw * sc
        val h = rh * sr
        val t = rsCell?.tileFor(w, h) ?: RectF(0f, 0f, w.toFloat(), h.toFloat())
        val x0 = (item.col * cw).toFloat()
        val y0 = (item.row * rh).toFloat()
        return RectF(x0 + t.left, y0 + t.top, x0 + t.right, y0 + t.bottom)
    }

    private fun setGhost(item: DeskItem, sc: Int, sr: Int, animate: Boolean) {
        val to = ghostTarget(item, sc, sr)
        ghostAnim?.cancel()
        if (!animate || !ghostOn) {
            ghost.set(to)
            invalidate()
            return
        }
        val from = RectF(ghost)
        val va = ValueAnimator.ofFloat(0f, 1f)
        va.duration = 200
        va.interpolator = DecelerateInterpolator(1.6f)
        va.addUpdateListener {
            val f = it.animatedValue as Float
            ghost.set(from.left + (to.left - from.left) * f, from.top + (to.top - from.top) * f,
                from.right + (to.right - from.right) * f, from.bottom + (to.bottom - from.bottom) * f)
            invalidate()
        }
        ghostAnim = va
        va.start()
    }

    private fun band(v: Float, lo: Float, hi: Float, unit: Float): Float = when {
        v < lo -> lo - minOf((lo - v) * 0.3f, unit * 0.35f)
        v > hi -> hi + minOf((v - hi) * 0.3f, unit * 0.5f)
        else -> v
    }

    private fun layoutLive(cell: FolderCell, item: DeskItem) {
        val cw = (width / prefs.cols).coerceAtLeast(1)
        val rh = (height / rowsEff()).coerceAtLeast(1)
        cell.measure(MeasureSpec.makeMeasureSpec(liveW, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(liveH, MeasureSpec.EXACTLY))
        cell.layout(item.col * cw, item.row * rh, item.col * cw + liveW, item.row * rh + liveH)
        invalidate()
    }

    fun resizeBegin(cell: FolderCell) {
        if (ending) return
        val item = cell.item
        val cw = (width / prefs.cols).coerceAtLeast(1)
        val rh = (height / rowsEff()).coerceAtLeast(1)
        rsItem = item
        rsCell = cell
        rsW0 = cw * item.sc
        rsH0 = rh * item.sr
        liveW = rsW0
        liveH = rsH0
        snapC = item.sc
        snapR = item.sr
        ghostR = cell.r0
        bringChildToFront(cell)
        ghostOn = false
        setGhost(item, snapC, snapR, false)
        ghostOn = true
        cell.grab(true)
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun resizeMove(dx: Float, dy: Float) {
        val item = rsItem ?: return
        val cell = rsCell ?: return
        if (ending) return
        val cw = (width / prefs.cols).coerceAtLeast(1)
        val rh = (height / rowsEff()).coerceAtLeast(1)
        val fw = rsW0 + dx
        val fh = rsH0 + dy
        val maxC = prefs.cols - item.col
        val maxR = rowsEff() - item.row
        var sc = (fw / cw).roundToInt().coerceIn(1, maxC)
        var sr = (fh / rh).roundToInt().coerceIn(1, maxR)
        while (!areaFree(item, sc, sr)) {
            val ec = sc - fw / cw
            val er = sr - fh / rh
            if (sr > 1 && (er >= ec || sc == 1)) sr-- else if (sc > 1) sc-- else break
        }
        // сколько ячеек вправо и вниз вообще свободно: за этим пределом линия тянется с сопротивлением
        var reachC = 1
        while (reachC < maxC && areaFree(item, reachC + 1, 1)) reachC++
        var reachR = 1
        while (reachR < maxR && areaFree(item, 1, reachR + 1)) reachR++
        liveW = band(fw, cw.toFloat(), cw * maxOf(reachC, sc).toFloat(), cw.toFloat()).roundToInt().coerceAtLeast(cw / 2)
        liveH = band(fh, rh.toFloat(), rh * maxOf(reachR, sr).toFloat(), rh.toFloat()).roundToInt().coerceAtLeast(rh / 2)
        if (sc != snapC || sr != snapR) {
            snapC = sc
            snapR = sr
            setGhost(item, sc, sr, true)
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        layoutLive(cell, item)
    }

    fun resizeEnd(cancel: Boolean) {
        val item = rsItem ?: return
        val cell = rsCell ?: return
        if (ending) return
        ending = true
        val cw = (width / prefs.cols).coerceAtLeast(1)
        val rh = (height / rowsEff()).coerceAtLeast(1)
        val sc = if (cancel) item.sc else snapC
        val sr = if (cancel) item.sr else snapR
        if (cancel) setGhost(item, sc, sr, true)
        cell.grab(false)
        val w0 = liveW
        val h0 = liveH
        val w1 = cw * sc
        val h1 = rh * sr
        val va = ValueAnimator.ofFloat(0f, 1f)
        va.duration = 340
        va.interpolator = OvershootInterpolator(1.8f)
        va.addUpdateListener {
            val f = it.animatedValue as Float
            liveW = (w0 + (w1 - w0) * f).roundToInt().coerceAtLeast(1)
            liveH = (h0 + (h1 - h0) * f).roundToInt().coerceAtLeast(1)
            layoutLive(cell, item)
        }
        va.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: android.animation.Animator) {
                item.sc = sc
                item.sr = sr
                rsItem = null
                rsCell = null
                liveW = 0
                liveH = 0
                ghostOn = false
                ending = false
                save()
                requestLayout()
                invalidate()
            }
        })
        va.start()
    }

    override fun dispatchDraw(cv: Canvas) {
        val it = rsItem
        if (ghostOn && it != null) {
            cv.save()
            cv.translate((it.page - frac) * width, 0f)
            ghostBox.set(ghost)
            cv.drawRoundRect(ghostBox, ghostR, ghostR, ghostFill)
            cv.drawRoundRect(ghostBox, ghostR, ghostR, ghostLine)
            cv.restore()
        }
        super.dispatchDraw(cv)
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
        val pos = free(item.col, item.row, item.page, null)
        items.add(DeskItem(pos[0], pos[1], false, "", mutableListOf(a.key), pos[2]))
        save()
        post { rebuild() }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cw = ((r - l) / prefs.cols).coerceAtLeast(1)
        val rh = ((b - t) / rowsEff()).coerceAtLeast(1)
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            val item = v.tag as DeskItem
            val wc = if (item.widget != 0 || item.folder) item.sc else 1
            val wr = if (item.widget != 0 || item.folder) item.sr else 1
            val live = item === rsItem && liveW > 0
            val lw = if (live) liveW else cw * wc
            val lh = if (live) liveH else rh * wr
            v.measure(MeasureSpec.makeMeasureSpec(lw, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(lh, MeasureSpec.EXACTLY))
            v.layout(item.col * cw, item.row * rh, item.col * cw + lw, item.row * rh + lh)
            if (item.widget != 0 && v is WidgetHolder) {
                val key = (cw * wc) * 10007 + rh * wr
                if (lastSize[item.widget] != key) {
                    lastSize[item.widget] = key
                    val d = context.resources.displayMetrics.density
                    val wdp = (cw * wc / d).toInt()
                    val hdp = (rh * wr / d).toInt()
                    try {
                        v.view.updateAppWidgetSize(Bundle(), wdp, hdp, wdp, hdp)
                    } catch (e: Exception) {
                    }
                }
            }
        }
        applyScroll()
    }

    fun drop(app: LauncherApp?, item: DeskItem?, x: Float, y: Float) {
        val cw = (width / prefs.cols).coerceAtLeast(1)
        val rh = (height / rowsEff()).coerceAtLeast(1)
        val pg = page()
        if (item != null && (item.widget != 0 || (item.folder && (item.sc > 1 || item.sr > 1)))) {
            // виджет или большая папка: верхний левый угол выбирается так, чтобы палец был примерно в центре
            val sc = item.sc.coerceIn(1, prefs.cols)
            val sr = item.sr.coerceIn(1, rowsEff())
            val wcol = (x / cw - sc / 2f + 0.5f).toInt().coerceIn(0, prefs.cols - sc)
            val wrow = (y / rh - sr / 2f + 0.5f).toInt().coerceIn(0, rowsEff() - sr)
            val wp = fitFree(sc, sr, wcol, wrow, pg, item)
            if (wp != null) {
                item.col = wp[0]
                item.row = wp[1]
                item.page = wp[2]
                item.sc = sc
                item.sr = sr
            }
            save()
            post { rebuild() }
            return
        }
        val col = (x / cw).toInt().coerceIn(0, prefs.cols - 1)
        val row = (y / rh).toInt().coerceIn(0, rowsEff() - 1)
        var found = item
        if (found == null && app != null) {
            found = items.firstOrNull { it.widget == 0 && !it.folder && it.keys[0] == app.key }
            if (found == null) found = DeskItem(col, row, false, "", mutableListOf(app.key), pg)
        }
        val d = found ?: return
        val target = items.firstOrNull { it !== d && it.widget == 0 && it.page == pg && occ(it, col, row) }
        if (target != null && !d.folder) {
            if (!target.folder) {
                target.folder = true
                target.name = L(context, "Папка", "Folder")
            }
            target.keys.add(d.keys[0])
            items.remove(d)
        } else {
            val pos = free(col, row, pg, d)
            d.col = pos[0]
            d.row = pos[1]
            d.page = pos[2]
            if (!items.contains(d)) items.add(d)
        }
        save()
        post { rebuild() }
    }

    fun removeItem(item: DeskItem) {
        if (item.widget != 0) {
            Widgets.delete(context, item.widget)
            holders.remove(item.widget)
        }
        items.remove(item)
        save()
        post { rebuild() }
    }
}
