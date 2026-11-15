package com.example.pixellauncher

import android.app.ActivityOptions
import android.content.*
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.hypot

class MainActivity : HomeBase() {
    private lateinit var grid: GridView
    private lateinit var dock: LinearLayout
    private lateinit var search: TextView
    private lateinit var col: LinearLayout
    private lateinit var dots: PageDots
    private lateinit var card: WindowCard
    private lateinit var desk: Desk
    private lateinit var trash: TextView
    private lateinit var menu: AppMenu
    private lateinit var edit: EditOverlay
    private lateinit var pinch: ScaleGestureDetector
    private var all = listOf<LauncherApp>()
    private var shown = listOf<LauncherApp>()
    private var dragApp: LauncherApp? = null
    private var dragItem: DeskItem? = null
    private var moved = false
    private var ox = 0f
    private var oy = 0f
    private var span0 = 0f
    private var editing = false
    private var launching = false
    private var stopped = false
    private var pending: Runnable? = null
    private val rcv = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { loadApps() }
    }

    private fun round(color: Int, r: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(r).toFloat()
        return g
    }

    @Suppress("DEPRECATION")
    private fun edge() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
    }

    @Suppress("DEPRECATION")
    private fun bottomOf(i: WindowInsets): Int =
        if (Build.VERSION.SDK_INT >= 30) i.getInsets(WindowInsets.Type.systemBars()).bottom
        else i.systemWindowInsetBottom

    @Suppress("DEPRECATION")
    private fun topOf(i: WindowInsets): Int =
        if (Build.VERSION.SDK_INT >= 30) i.getInsets(WindowInsets.Type.systemBars()).top
        else i.systemWindowInsetTop

    override fun pagingAllowed(): Boolean = progress == 0f
    override fun drawerAllowed(): Boolean = prefs.drawer == 1 && !editing
    override fun pageStart(): Float = if (::desk.isInitialized) desk.frac else 0f
    override fun pageMove(f: Float) = desk.setFrac(f)
    override fun pageEnd(vx: Float) = desk.snap(vx)

    private fun homePage(): Int = getSharedPreferences("launcher", MODE_PRIVATE).getInt("homePage", 0)

    private fun openSettings(screen: String?) {
        val i = Intent(this, SettingsActivity::class.java)
        if (screen != null) i.putExtra("s", screen)
        startActivity(i)
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        edge()
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        val root = FrameLayout(this)
        setContentView(root)

        home = FrameLayout(this)
        col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val glance = TextView(this)
        glance.text = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
        glance.setTextColor(Color.WHITE)
        glance.textSize = 28f
        glance.setPadding(dp(28), dp(16), 0, dp(8))
        desk = Desk(this, prefs, { x, v -> launch(x, v) }, { item, v ->
            startDrag(null, item, v)
            menuFor(null, item, v)
        })
        dots = PageDots(this)
        dock = LinearLayout(this)
        dock.gravity = Gravity.CENTER
        search = SearchBar(this)
        search.setOnClickListener {
            if (!editing) {
                if (prefs.drawer == 1) {
                    animateTo(1f)
                } else {
                    try {
                        startActivity(Intent(Intent.ACTION_WEB_SEARCH).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                    }
                }
            }
        }
        col.addView(glance)
        col.addView(desk, LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(dots, LinearLayout.LayoutParams(-1, dp(16)))
        col.addView(dock)
        val slp = LinearLayout.LayoutParams(-1, dp(56))
        slp.setMargins(dp(16), 0, dp(16), dp(16))
        col.addView(search, slp)
        home.addView(col, FrameLayout.LayoutParams(-1, -1))
        trash = TextView(this)
        trash.text = L(this, "Убрать с экрана", "Remove from screen")
        trash.setTextColor(Color.WHITE)
        trash.gravity = Gravity.CENTER
        trash.background = round(0x99000000.toInt(), 20)
        trash.visibility = View.GONE
        val tlp = FrameLayout.LayoutParams(-1, dp(56), Gravity.TOP)
        tlp.setMargins(dp(24), dp(48), dp(24), 0)
        home.addView(trash, tlp)
        root.addView(home)

        drawer = LinearLayout(this)
        drawer.orientation = LinearLayout.VERTICAL
        drawer.visibility = View.INVISIBLE
        drawer.isClickable = true
        val r = dp(28).toFloat()
        val bg = GradientDrawable()
        bg.setColor(0xF0202124.toInt())
        bg.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        drawer.background = bg
        val header = FrameLayout(this)
        val handle = View(this)
        handle.background = round(0x66FFFFFF, 2)
        header.addView(handle, FrameLayout.LayoutParams(dp(36), dp(4), Gravity.CENTER))
        val box = EditText(this)
        box.hint = L(this, "Поиск приложений", "Search apps")
        box.setHintTextColor(0x99FFFFFF.toInt())
        box.setTextColor(Color.WHITE)
        box.setSingleLine()
        box.setPadding(dp(20), 0, dp(20), 0)
        box.background = round(0x33FFFFFF, 28)
        box.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(e: Editable?) { filter(e.toString()) }
            override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
        })
        grid = GridView(this)
        grid.numColumns = prefs.cols
        grid.verticalSpacing = dp(12)
        grid.selector = ColorDrawable(Color.TRANSPARENT)
        grid.clipToPadding = false
        drawer.addView(header, LinearLayout.LayoutParams(-1, dp(28)))
        val blp = LinearLayout.LayoutParams(-1, dp(52))
        blp.setMargins(dp(16), 0, dp(16), 0)
        drawer.addView(box, blp)
        drawer.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        val dlp = FrameLayout.LayoutParams(-1, -1)
        dlp.topMargin = dp(40)
        root.addView(drawer, dlp)

        edit = EditOverlay(this, desk)
        root.addView(edit, FrameLayout.LayoutParams(-1, -1))
        edit.onDone = { exitEdit() }
        edit.onWall = {
            exitEdit()
            openSettings("wall")
        }
        edit.onHome = {
            getSharedPreferences("launcher", MODE_PRIVATE).edit().putInt("homePage", desk.page()).apply()
            Toast.makeText(this, L(this, "Страница " + (desk.page() + 1) + " теперь главная",
                "Page " + (desk.page() + 1) + " is now the home page"), Toast.LENGTH_SHORT).show()
        }
        edit.onWidgets = {
            Toast.makeText(this, L(this, "Виджеты скоро", "Widgets are coming soon"), Toast.LENGTH_SHORT).show()
        }
        edit.onSettings = {
            exitEdit()
            openSettings(null)
        }
        edit.onRemove = {
            for (i in edit.badges.picked.toList()) desk.removeItem(i)
            edit.badges.picked.clear()
            edit.updateRemove(0, false)
            edit.badges.invalidate()
        }
        edit.badges.onChange = { edit.updateRemove(edit.badges.picked.size, prefs.drawer == 1) }

        card = WindowCard(this)
        root.addView(card, FrameLayout.LayoutParams(-1, -1))
        menu = AppMenu(this, root)
        menu.targets = listOf(home, drawer)

        pinch = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
                span0 = d.currentSpan
                return true
            }

            override fun onScale(d: ScaleGestureDetector): Boolean {
                if (!editing && progress == 0f && !launching && span0 > 0f && d.currentSpan / span0 < 0.8f) enterEdit()
                return true
            }
        })
        val handler: (MotionEvent) -> Boolean = { e ->
            pinch.onTouchEvent(e)
            if (e.pointerCount > 1 || pinch.isInProgress) true else onSwipe(e)
        }
        desk.sink = handler
        desk.isSwiping = { swiping() || pinch.isInProgress }
        desk.onPage = { n, f ->
            dots.set(n, f)
            edit.page(n, f)
            edit.badges.invalidate()
        }
        home.setOnTouchListener { _, e -> handler(e) }
        header.setOnTouchListener { _, e -> onSwipe(e) }
        root.setOnDragListener { _, e ->
            when (e.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_LOCATION -> {
                    if (!moved && hypot(e.x - ox, e.y - oy) > dp(24)) {
                        moved = true
                        menu.hide()
                        if (dragItem != null && prefs.drawer == 1 && !editing) trash.visibility = View.VISIBLE
                        if (dragApp != null) animateTo(0f)
                    }
                    if (moved) desk.edgeHover(e.x)
                    true
                }
                DragEvent.ACTION_DROP -> {
                    if (moved) {
                        val di = dragItem
                        if (di != null && prefs.drawer == 1 && e.y < dp(110) && !editing) desk.removeItem(di)
                        else desk.drop(dragApp, di, e.x - desk.left, e.y - desk.top)
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    trash.visibility = View.GONE
                    dragApp = null
                    dragItem = null
                    desk.rebuild()
                    desk.endDrag()
                    true
                }
                else -> true
            }
        }
        root.setOnApplyWindowInsetsListener { _, ins ->
            col.setPadding(0, topOf(ins), 0, bottomOf(ins))
            grid.setPadding(dp(8), dp(12), dp(8), bottomOf(ins))
            edit.setInsets(topOf(ins), bottomOf(ins))
            ins
        }
        val f = IntentFilter()
        f.addAction(Intent.ACTION_PACKAGE_ADDED)
        f.addAction(Intent.ACTION_PACKAGE_REMOVED)
        f.addDataScheme("package")
        registerReceiver(rcv, f)
        prefs.listen { k -> if (k == "lang") recreate() else relayout() }
        look()
        loadApps()
        root.post {
            applyProgress()
            desk.setFrac(homePage().toFloat())
        }
    }

    private fun enterEdit() {
        editing = true
        menu.hide()
        edit.badges.picked.clear()
        edit.updateRemove(0, false)
        edit.show()
        for (v in listOf(col.getChildAt(0), dots, dock, search)) v.animate().alpha(0f).setDuration(200).start()
    }

    private fun exitEdit() {
        if (!editing) return
        editing = false
        edit.hide()
        edit.badges.picked.clear()
        for (v in listOf(col.getChildAt(0), dots, dock, search)) v.animate().alpha(1f).setDuration(220).start()
    }

    private fun look() {
        val lp = LinearLayout.LayoutParams(-1, dp(prefs.iconSize + 28))
        lp.setMargins(dp(12), 0, dp(12), if (prefs.search == 1) dp(8) else dp(12))
        dock.layoutParams = lp
        dock.background = if (prefs.dockBg == 1) round(0x40FFFFFF, prefs.dockR) else null
        search.visibility = if (prefs.search == 1) View.VISIBLE else View.GONE
        dots.visibility = if (prefs.dots == 1) View.VISIBLE else View.GONE
    }

    private fun relayout() {
        look()
        grid.numColumns = prefs.cols
        loadApps()
        desk.reconfigure()
    }

    private fun startDrag(a: LauncherApp?, item: DeskItem?, cell: View) {
        dragApp = a
        dragItem = item
        moved = false
        val loc = IntArray(2)
        cell.getLocationOnScreen(loc)
        ox = loc[0] + cell.width / 2f
        oy = loc[1] + cell.height / 2f
        val data = ClipData.newPlainText("app", "x")
        cell.startDragAndDrop(data, View.DragShadowBuilder(cell), null, 0)
    }

    private fun menuFor(a: LauncherApp?, item: DeskItem?, cell: View) {
        val app = a ?: (item?.let { if (it.folder) null else all.firstOrNull { x -> x.key == it.keys[0] } }) ?: return
        var remove: (() -> Unit)? = null
        if (item != null && prefs.drawer == 1) remove = { desk.removeItem(item) }
        try {
            menu.show(app, cell, remove)
        } catch (e: Throwable) {
            Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show()
        }
    }

    private fun loadApps() {
        all = queryApps(this)
        filter("")
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_VIEW, Uri.parse("sms:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE))
        val dockApps = ArrayList<LauncherApp>()
        for (intent in intents) {
            val ri = packageManager.resolveActivity(intent, 0) ?: continue
            val a = all.firstOrNull { it.pkg == ri.activityInfo.packageName } ?: continue
            dockApps.add(a)
        }
        desk.hide = dockApps.map { it.pkg }.toSet()
        desk.setApps(all)
        dock.removeAllViews()
        for (a in dockApps) {
            dock.addView(makeCell(this, a, false) { x, v -> launch(x, v) }, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun filter(q: String) {
        shown = if (q.isBlank()) all else all.filter { it.label.contains(q, true) }
        grid.adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(p: Int): Any = shown[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, v: View?, g: ViewGroup?): View =
                makeCell(this@MainActivity, shown[p], true, { x, cell ->
                    startDrag(x, null, cell)
                    menuFor(x, null, cell)
                }) { x, iv -> launch(x, iv) }
        }
    }

    private fun launch(a: LauncherApp, icon: View) {
        if (launching || editing) return
        launching = true
        val loc = IntArray(2)
        icon.getLocationOnScreen(loc)
        val rect = RectF(loc[0].toFloat(), loc[1].toFloat(),
            (loc[0] + icon.width).toFloat(), (loc[1] + icon.height).toFloat())
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(a.pkg, a.cls)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val land = a.land && prefs.landOn == 1
        card.cornerDp = if (land) prefs.landR else prefs.radius
        card.speed = if (land) prefs.landSpeed else prefs.speed
        card.curve = prefs.curve
        card.land = land
        card.expand(rect, sourceIcon(this, a, icon)) {
            val run = Runnable {
                launching = false
                pending = null
                try {
                    startActivity(i, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
                } catch (e: Exception) {
                    card.cancel()
                    Fx.back(this, prefs, home, drawer, progress)
                }
            }
            pending = run
            home.postDelayed(run, prefs.pause.toLong())
        }
        Fx.out(this, prefs, home, home)
    }

    private fun cancelLaunch(): Boolean {
        if (!launching) return false
        launching = false
        val p = pending
        if (p != null) home.removeCallbacks(p)
        pending = null
        card.cancel()
        Fx.back(this, prefs, home, drawer, progress)
        return true
    }

    override fun onNewIntent(intent: Intent?) {
        if (::menu.isInitialized && menu.showing) menu.hide()
        if (editing) {
            exitEdit()
            return
        }
        if (cancelLaunch()) return
        if (stopped) return
        super.onNewIntent(intent)
        if (::desk.isInitialized) desk.goTo(homePage())
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::menu.isInitialized && menu.showing) {
            menu.hide()
            return
        }
        if (editing) {
            exitEdit()
            return
        }
        if (!cancelLaunch()) super.onBackPressed()
    }

    override fun onStop() {
        super.onStop()
        stopped = true
    }

    override fun onResume() {
        super.onResume()
        stopped = false
        if (::card.isInitialized) card.collapse()
    }

    override fun onDestroy() {
        unregisterReceiver(rcv)
        super.onDestroy()
    }
}
