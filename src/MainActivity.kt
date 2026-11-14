package com.example.pixellauncher

import android.app.ActivityOptions
import android.content.*
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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
    private lateinit var card: WindowCard
    private lateinit var desk: Desk
    private lateinit var prefs: Prefs
    private lateinit var trash: TextView
    private var all = listOf<LauncherApp>()
    private var shown = listOf<LauncherApp>()
    private var dragApp: LauncherApp? = null
    private var dragFromDesk = false
    private var launching = false
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

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        prefs = Prefs(this)
        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        val root = FrameLayout(this)
        setContentView(root)

        home = FrameLayout(this)
        desk = Desk(this, prefs, { x, v -> launch(x, v) }, { x, v -> startDrag(x, v, true) })
        home.addView(desk, FrameLayout.LayoutParams(-1, -1))
        val glance = TextView(this)
        glance.text = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
        glance.setTextColor(Color.WHITE)
        glance.textSize = 28f
        glance.setPadding(dp(28), dp(72), 0, 0)
        dock = LinearLayout(this)
        dock.gravity = Gravity.CENTER
        val search = TextView(this)
        search.text = "G   Search"
        search.textSize = 16f
        search.setTextColor(0xFF444746.toInt())
        search.gravity = Gravity.CENTER_VERTICAL
        search.setPadding(dp(24), 0, dp(24), 0)
        search.background = round(0xF2F1F3F4.toInt(), 28)
        search.setOnClickListener { setProgress(1f) }
        trash = TextView(this)
        trash.text = "Убрать с экрана"
        trash.setTextColor(Color.WHITE)
        trash.gravity = Gravity.CENTER
        trash.background = round(0x99000000.toInt(), 20)
        trash.visibility = View.GONE
        home.addView(glance)
        val kl = FrameLayout.LayoutParams(-1, dp(84), Gravity.BOTTOM)
        kl.bottomMargin = dp(80)
        home.addView(dock, kl)
        val slp = FrameLayout.LayoutParams(-1, dp(56), Gravity.BOTTOM)
        slp.setMargins(dp(16), 0, dp(16), dp(16))
        home.addView(search, slp)
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
        box.hint = "Search apps"
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
                    showSettings(this@MainActivity, prefs, { loadApps() }, { pickWallpaper() }, { applyGrid() })
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
                    trash.visibility = if (dragFromDesk) View.VISIBLE else View.GONE
                    dragApp != null
                }
                DragEvent.ACTION_DROP -> {
                    val a = dragApp
                    if (a != null) {
                        if (dragFromDesk && e.y < dp(110)) desk.removeApp(a) else desk.drop(a, e.x, e.y)
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    trash.visibility = View.GONE
                    dragApp = null
                    desk.rebuild()
                    true
                }
                else -> true
            }
        }
        root.setOnApplyWindowInsetsListener { _, ins ->
            val b = ins.getInsets(WindowInsets.Type.systemBars())
            home.setPadding(0, 0, 0, b.bottom)
            grid.setPadding(dp(8), dp(12), dp(8), b.bottom)
            ins
        }
        val f = IntentFilter()
        f.addAction(Intent.ACTION_PACKAGE_ADDED)
        f.addAction(Intent.ACTION_PACKAGE_REMOVED)
        f.addDataScheme("package")
        registerReceiver(rcv, f)
        loadApps()
        root.post { applyProgress() }
    }

    private fun applyGrid() {
        grid.numColumns = prefs.cols
        desk.reconfigure()
    }

    private fun pickWallpaper() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "image/*"
        startActivityForResult(i, 7)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        super.onActivityResult(rc, res, data)
        if (rc == 7 && res == RESULT_OK) {
            val u = data?.data
            if (u != null) applyWallpaper(this, u)
        }
    }

    private fun startDrag(a: LauncherApp, cell: View, fromDesk: Boolean) {
        dragApp = a
        dragFromDesk = fromDesk
        val data = ClipData.newPlainText("app", a.pkg)
        cell.startDragAndDrop(data, View.DragShadowBuilder(cell), null, 0)
        if (!fromDesk) setProgress(0f)
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
                makeCell(this@MainActivity, shown[p], true, { x, cell -> startDrag(x, cell, false) }) { x, iv -> launch(x, iv) }
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
        card.cornerDp = prefs.radius
        card.speed = prefs.speed
        card.expand(rect, icon) {
            val run = Runnable {
                launching = false
                pending = null
                try {
                    startActivity(i, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
                } catch (e: Exception) {
                    card.cancel()
                    restoreHome()
                }
            }
            pending = run
            home.postDelayed(run, prefs.pause.toLong())
        }
        val t: View = if (progress > 0.5f) drawer else home
        t.animate().scaleX(0.92f).scaleY(0.92f).alpha(0f).setDuration(prefs.speed.toLong()).setInterpolator(EMPH).start()
    }

    private fun restoreHome() {
        val s = 1f - 0.08f * progress
        home.animate().scaleX(s).scaleY(s).alpha(1f - progress).setDuration(250).setInterpolator(EMPH).start()
        drawer.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(250).setInterpolator(EMPH).start()
    }

    private fun cancelLaunch(): Boolean {
        if (!launching) return false
        launching = false
        val p = pending
        if (p != null) home.removeCallbacks(p)
        pending = null
        card.cancel()
        restoreHome()
        return true
    }

    override fun onNewIntent(intent: Intent?) {
        if (!cancelLaunch()) super.onNewIntent(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!cancelLaunch()) super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        if (::card.isInitialized) card.collapse()
    }

    override fun onDestroy() {
        unregisterReceiver(rcv)
        super.onDestroy()
    }
}
