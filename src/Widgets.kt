package com.example.pixellauncher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

/** Хост системных и сторонних виджетов. */
object Widgets {
    const val HOST_ID = 1042
    private var host: AppWidgetHost? = null

    fun host(c: Context): AppWidgetHost {
        var h = host
        if (h == null) {
            h = AppWidgetHost(c.applicationContext, HOST_ID)
            host = h
        }
        return h
    }

    fun mgr(c: Context): AppWidgetManager = AppWidgetManager.getInstance(c.applicationContext)

    fun start(c: Context) {
        try {
            host(c).startListening()
        } catch (e: Exception) {
        }
    }

    fun stop(c: Context) {
        try {
            host(c).stopListening()
        } catch (e: Exception) {
        }
    }

    fun delete(c: Context, id: Int) {
        try {
            host(c).deleteAppWidgetId(id)
        } catch (e: Exception) {
        }
    }

    fun providers(c: Context): List<AppWidgetProviderInfo> = try {
        mgr(c).installedProviders
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Контейнер вокруг виджета. Долгое нажатие перехватывается здесь, потому что
 * виджеты сами нажатия на дочерних вью съедают и долгое нажатие не получилось бы поймать.
 */
class WidgetHolder(c: Context, val view: AppWidgetHostView) : FrameLayout(c) {
    var onLong: () -> Unit = {}
    private val slop = ViewConfiguration.get(c).scaledTouchSlop
    private val timeout = ViewConfiguration.getLongPressTimeout().toLong()
    private var x0 = 0f
    private var y0 = 0f
    private var fired = false
    private val fire = Runnable {
        fired = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onLong()
    }

    init {
        addView(view, LayoutParams(-1, -1))
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                x0 = e.rawX
                y0 = e.rawY
                fired = false
                postDelayed(fire, timeout)
            }
            MotionEvent.ACTION_MOVE -> if (abs(e.rawX - x0) > slop || abs(e.rawY - y0) > slop) removeCallbacks(fire)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(fire)
        }
        return fired
    }

    override fun onTouchEvent(e: MotionEvent): Boolean = true
}

/** Лист "Виджеты": список всех виджетов установленных приложений с превью и размером в ячейках. */
class WidgetSheet(
    private val a: Activity,
    private val inB: Int,
    list: List<AppWidgetProviderInfo>,
    private val span: (AppWidgetProviderInfo) -> IntArray,
    private val onPick: (AppWidgetProviderInfo) -> Unit,
    private val onClose: () -> Unit
) : LinearLayout(a) {

    fun params(): FrameLayout.LayoutParams {
        val h = (a.resources.displayMetrics.heightPixels * 0.72f).toInt()
        val lp = FrameLayout.LayoutParams(-1, h, Gravity.BOTTOM)
        lp.setMargins(a.dp(12), 0, a.dp(12), inB + a.dp(10))
        return lp
    }

    private fun appLabel(info: AppWidgetProviderInfo): String = try {
        val pm = a.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
    } catch (e: Exception) {
        info.provider.packageName
    }

    private fun widgetLabel(info: AppWidgetProviderInfo): String = try {
        info.loadLabel(a.packageManager)
    } catch (e: Exception) {
        info.provider.shortClassName
    }

    init {
        orientation = VERTICAL
        val g = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xF030343B.toInt(), 0xF01E2126.toInt()))
        g.cornerRadius = a.dp(32).toFloat()
        g.setStroke(a.dp(1), 0x33FFFFFF)
        background = g
        isClickable = true
        setPadding(a.dp(16), a.dp(10), a.dp(16), a.dp(12))

        val handle = View(a)
        val hg = GradientDrawable()
        hg.setColor(0x66FFFFFF)
        hg.cornerRadius = a.dp(2).toFloat()
        handle.background = hg
        val hl = LayoutParams(a.dp(36), a.dp(4))
        hl.gravity = Gravity.CENTER_HORIZONTAL
        addView(handle, hl)

        val head = LinearLayout(a)
        head.orientation = HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(a.dp(4), a.dp(12), 0, a.dp(10))
        val back = GlyphView(a, "back", Color.WHITE)
        back.setOnClickListener { onClose() }
        head.addView(back, LayoutParams(a.dp(26), a.dp(26)))
        val title = TextView(a)
        title.text = L(a, "Виджеты", "Widgets")
        title.setTextColor(Color.WHITE)
        title.textSize = 20f
        title.setPadding(a.dp(12), 0, 0, 0)
        head.addView(title)
        addView(head, LayoutParams(-1, -2))

        val sv = ScrollView(a)
        val col = LinearLayout(a)
        col.orientation = VERTICAL
        sv.addView(col)
        addView(sv, LayoutParams(-1, 0, 1f))

        val sorted = list.sortedWith(compareBy({ appLabel(it).lowercase() }, { widgetLabel(it).lowercase() }))
        if (sorted.isEmpty()) {
            val t = TextView(a)
            t.text = L(a, "Виджеты не найдены", "No widgets found")
            t.setTextColor(0xFFC9CCD2.toInt())
            t.setPadding(a.dp(8), a.dp(16), 0, 0)
            col.addView(t)
        }
        for (info in sorted) col.addView(row(info))

        translationY = 3000f
        post {
            translationY = (height + a.dp(40)).toFloat()
            animate().translationY(0f).setDuration(300).setInterpolator(DecelerateInterpolator(1.5f)).start()
        }
    }

    private fun row(info: AppWidgetProviderInfo): View {
        val r = LinearLayout(a)
        r.orientation = HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(a.dp(10), a.dp(10), a.dp(12), a.dp(10))
        val bg = GradientDrawable()
        bg.setColor(0x22FFFFFF)
        bg.cornerRadius = a.dp(18).toFloat()
        r.background = bg
        val lp = LayoutParams(-1, -2)
        lp.bottomMargin = a.dp(8)
        r.layoutParams = lp

        val img = ImageView(a)
        img.scaleType = ImageView.ScaleType.FIT_CENTER
        var d: Drawable? = null
        try {
            d = info.loadPreviewImage(a, 0)
        } catch (e: Exception) {
        }
        if (d == null) {
            try {
                d = info.loadIcon(a, 0)
            } catch (e: Exception) {
            }
        }
        img.setImageDrawable(d)
        r.addView(img, LayoutParams(a.dp(104), a.dp(72)))

        val box = LinearLayout(a)
        box.orientation = VERTICAL
        box.setPadding(a.dp(14), 0, 0, 0)
        val t1 = TextView(a)
        t1.text = widgetLabel(info)
        t1.setTextColor(Color.WHITE)
        t1.textSize = 16f
        t1.typeface = Typeface.DEFAULT_BOLD
        t1.maxLines = 2
        val sp = span(info)
        val t2 = TextView(a)
        t2.text = appLabel(info) + "  ·  " + sp[0] + " × " + sp[1]
        t2.setTextColor(0xFFC9CCD2.toInt())
        t2.textSize = 13f
        t2.maxLines = 1
        box.addView(t1)
        box.addView(t2)
        r.addView(box, LayoutParams(0, -2, 1f))
        r.setOnClickListener { onPick(info) }
        return r
    }

    fun close(end: () -> Unit) {
        animate().translationY((height + a.dp(40)).toFloat()).setDuration(220)
            .setInterpolator(DecelerateInterpolator(1.2f)).withEndAction(end).start()
    }
}
