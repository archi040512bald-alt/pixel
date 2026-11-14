package com.example.pixellauncher

import android.app.ActivityOptions
import android.content.*
import android.graphics.Color
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
    private var all = listOf<LauncherApp>()
    private var shown = listOf<LauncherApp>()
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
        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        val root = FrameLayout(this)
        setContentView(root)

        home = FrameLayout(this)
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
        home.addView(glance)
        home.addView(dock, FrameLayout.LayoutParams(-1, dp(84), Gravity.BOTTOM))
        val slp = FrameLayout.LayoutParams(-1, dp(56), Gravity.BOTTOM)
        slp.setMargins(dp(16), 0, dp(16), dp(16))
        home.addView(search, slp)
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
        grid.numColumns = 5
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

        val swipe = View.OnTouchListener { _, e -> onSwipe(e) }
        home.setOnTouchListener(swipe)
        header.setOnTouchListener(swipe)
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

    private fun loadApps() {
        all = queryApps(this)
        filter("")
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
                makeCell(this@MainActivity, shown[p], true) { x, iv -> launch(x, iv) }
        }
    }

    private fun launch(a: LauncherApp, icon: View) {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(a.pkg, a.cls)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val opts = ActivityOptions.makeClipRevealAnimation(icon, 0, 0, icon.width, icon.height)
        startActivity(i, opts.toBundle())
        val t: View = if (progress > 0.5f) drawer else home
        t.animate().scaleX(0.9f).scaleY(0.9f).alpha(0f).setDuration(300).setInterpolator(EMPH).start()
    }

    override fun onDestroy() {
        unregisterReceiver(rcv)
        super.onDestroy()
    }
}
