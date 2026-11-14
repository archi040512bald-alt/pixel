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

class MainActivity : HomeBase() {
    private lateinit var grid: GridView
    private lateinit var dock: LinearLayout
    private lateinit var search: TextView
    private lateinit var col: LinearLayout
    private lateinit var card: WindowCard
    private lateinit var desk: Desk
    private lateinit var trash: TextView
    private var all = listOf<LauncherApp>()
    private var shown = listOf<LauncherApp>()
    private var dragApp: LauncherApp? = null
    private var dragItem: DeskItem? = null
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
        desk = Desk(this, prefs, { x, v -> launch(x, v) }, { item, v -> startDrag(null, item, v) })
        dock = LinearLayout(this)
        dock.gravity = Gravity.CENTER
        search = TextView(this)
        search.text = L(this, "G   Поиск", "G   Search")
        search.textSize = 16f
        search.setTextColor(0xFF444746.toInt())
        search.gravity = Gravity.CENTER_VERTICAL
        search.setPadding(dp(24), 0, dp(24), 0)
        search.background = round(0xF2F1F3F4.toInt(), 28)
        search.setOnClickListener { animateTo(1f) }
        col.addView(glance)
        col.addView(desk, LinearLayout.LayoutParams(-1, 0, 1f))
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

        card = WindowCard(this)
        root.addView(card, FrameLayout.LayoutParams(-1, -1))

        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                showPopup(this@MainActivity, home, e.rawX.toInt(), e.rawY.toInt()) {
                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                }
            }
        })
        home.setOnTouchListener { _, e ->
            gd.onTouchEvent(e)
            onSwipe(e)
        }
        header.setOnTouchListener { _, e -> onSwipe(e) }
        home.setOnDragListener { _, e ->
            when (e.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    trash.visibility = if (dragItem != null) View.VISIBLE else View.GONE
                    true
                }
                DragEvent.ACTION_DROP -> {
                    val di = dragItem
                    if (di != null && e.y < dp(110)) desk.removeItem(di)
                    else desk.drop(dragApp, di, e.x - desk.left, e.y - desk.top)
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    trash.visibility = View.GONE
                    dragApp = null
                    dragItem = null
                    desk.rebuild()
                    true
                }
                else -> true
            }
        }
        root.setOnApplyWindowInsetsListener { _, ins ->
            col.setPadding(0, topOf(ins), 0, bottomOf(ins))
            grid.setPadding(dp(8), dp(12), dp(8), bottomOf(ins))
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
        root.post { applyProgress() }
    }

    private fun look() {
        val lp = LinearLayout.LayoutParams(-1, dp(prefs.iconSize + 28))
        lp.setMargins(dp(12), 0, dp(12), if (prefs.search == 1) dp(8) else dp(12))
        dock.layoutParams = lp
        dock.background = if (prefs.dockBg == 1) round(0x40FFFFFF, prefs.dockR) else null
        search.visibility = if (prefs.search == 1) View.VISIBLE else View.GONE
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
        val data = ClipData.newPlainText("app", "x")
        cell.startDragAndDrop(data, View.DragShadowBuilder(cell), null, 0)
        if (item == null) animateTo(0f)
    }

    private fun loadApps() {
        all = queryApps(this)
        filter("")
        desk.setApps(all)
        dock.removeAllViews()
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_VIEW, Uri.parse("sms:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE))
        for (intent in intents) {
            val ri = packageManager.resolveActivity(intent, 0) ?: continue
            val a = all.firstOrNull { it.pkg == ri.activityInfo.packageName } ?: continue
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
                makeCell(this@MainActivity, shown[p], true, { x, cell -> startDrag(x, null, cell) }) { x, iv -> launch(x, iv) }
        }
    }

    private fun launch(a: LauncherApp, icon: View) {
        if (launching) return
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
        card.expand(rect, icon) {
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
        if (cancelLaunch()) return
        if (stopped) return
        super.onNewIntent(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
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
