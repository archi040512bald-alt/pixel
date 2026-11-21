package com.example.pixellauncher

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class LauncherApp(val label: String, val pkg: String, val cls: String, val icon: Drawable, val land: Boolean) {
    val key: String get() = pkg + "/" + cls
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun shapeNames(c: Context): List<String> = listOf(
    L(c, "Системная", "System"), L(c, "Круг", "Circle"),
    L(c, "Скруглённый квадрат", "Rounded square"), L(c, "Сквиркл", "Squircle"),
    L(c, "Капля", "Teardrop"), L(c, "Квадрат", "Square"))

fun queryApps(c: Context): List<LauncherApp> {
    val pm = c.packageManager
    val p = Prefs(c)
    val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val list = ArrayList<LauncherApp>()
    for (ri in pm.queryIntentActivities(q, 0)) {
        val pk = ri.activityInfo.packageName
        if (pk == c.packageName) continue
        // Иконпак важнее: цветные значки применяются только к системным значкам
        val packIcon = IconPacks.icon(c, pk, ri.activityInfo.name)
        val ic = packIcon ?: IconTheme.apply(c, ri.loadIcon(pm), p.iconStyle, p.iconSeed, p.iconAll == 1)
        val o = ri.activityInfo.screenOrientation
        val land = o == 0 || o == 6 || o == 8 || o == 11
        list.add(LauncherApp(ri.loadLabel(pm).toString(), pk, ri.activityInfo.name, ic, land))
    }
    return list.sortedBy { it.label.lowercase() }
}

class ShapedIcon(
    c: Context,
    private val d: Drawable,
    private val shape: Int,
    private val pkg: String = "",
    private val badgeOn: Boolean = false
) : View(c) {
    private val path = Path()
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeText = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (badgeOn) Badges.views.add(this)
    }

    override fun onDetachedFromWindow() {
        Badges.views.remove(this)
        super.onDetachedFromWindow()
    }

    private fun drawBadge(cv: Canvas, w: Int, n: Int) {
        val r = w * 0.19f
        val cx = w - r
        val cy = r
        badgePaint.color = 0xFFE53935.toInt()
        cv.drawCircle(cx, cy, r, badgePaint)
        val t = if (n > 99) "99+" else n.toString()
        badgeText.color = Color.WHITE
        badgeText.typeface = Typeface.DEFAULT_BOLD
        badgeText.textAlign = Paint.Align.CENTER
        badgeText.textSize = r * (if (t.length > 2) 0.85f else if (t.length > 1) 1.05f else 1.2f)
        cv.drawText(t, cx, cy - (badgeText.descent() + badgeText.ascent()) / 2f, badgeText)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        path.reset()
        val r = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val s = w.toFloat()
        val half = s / 2f
        when (shape) {
            1 -> path.addOval(r, Path.Direction.CW)
            2 -> path.addRoundRect(r, s * 0.28f, s * 0.28f, Path.Direction.CW)
            3 -> path.addRoundRect(r, s * 0.40f, s * 0.40f, Path.Direction.CW)
            4 -> path.addRoundRect(r, floatArrayOf(half, half, s * 0.08f, s * 0.08f, half, half, half, half), Path.Direction.CW)
            5 -> path.addRoundRect(r, s * 0.06f, s * 0.06f, Path.Direction.CW)
        }
    }

    override fun onDraw(cv: Canvas) {
        val w = width
        val h = height
        val sc = cv.save()
        if (shape != 0) cv.clipPath(path)
        if (shape != 0 && Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable) {
            val o = (w * 0.25f).toInt()
            val bg = d.background
            val fg = d.foreground
            bg?.setBounds(-o, -o, w + o, h + o)
            bg?.draw(cv)
            fg?.setBounds(-o, -o, w + o, h + o)
            fg?.draw(cv)
        } else {
            d.setBounds(0, 0, w, h)
            d.draw(cv)
        }
        cv.restoreToCount(sc)
        if (badgeOn) {
            val n = Badges.counts[pkg] ?: 0
            if (n > 0) drawBadge(cv, w, n)
        }
    }
}

fun labelView(c: Context, s: String): TextView {
    val t = TextView(c)
    t.text = s
    t.setTextColor(Color.WHITE)
    t.textSize = 12f
    t.maxLines = 1
    t.ellipsize = android.text.TextUtils.TruncateAt.END
    t.gravity = Gravity.CENTER
    return t
}

fun makeCell(c: Context, a: LauncherApp, label: Boolean,
             onLong: (LauncherApp, View) -> Unit = { _, _ -> },
             open: (LauncherApp, View) -> Unit): View {
    val p = Prefs(c)
    val ll = LinearLayout(c)
    ll.orientation = LinearLayout.VERTICAL
    ll.gravity = Gravity.CENTER
    ll.setPadding(0, c.dp(6), 0, c.dp(6))
    val icon = ShapedIcon(c, a.icon, p.shape, a.pkg, p.badges == 1)
    val sz = c.dp(p.iconSize)
    ll.addView(icon, LinearLayout.LayoutParams(sz, sz))
    if (label && p.labels == 1) {
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = c.dp(4)
        ll.addView(labelView(c, a.label), lp)
    }
    ll.setOnClickListener { open(a, icon) }
    ll.setOnLongClickListener { onLong(a, ll); true }
    return ll
}
