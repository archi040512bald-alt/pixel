package com.example.pixellauncher

import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import java.io.File
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import java.util.Date

class LockView(private val a: Activity, private val unlock: (PendingIntent?) -> Unit) : FrameLayout(a) {
    private val sp = LockPrefs(a)
    private val content = LinearLayout(a)
    private val cards = LinearLayout(a)
    private val alarm = TextView(a)
    private val clock = TextClock(a)
    private val finger = FingerprintView(a)
    private val bgImg = ImageView(a)
    private var sensors: SensorManager? = null
    private var tx = 0f
    private var ty = 0f
    private val tilt = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val dd = a.resources.displayMetrics.density
            val gx = (-e.values[0] / 9.8f).coerceIn(-1f, 1f) * 14f * dd
            val gy = (e.values[1] / 9.8f).coerceIn(-1f, 1f) * 14f * dd
            tx += (gx - tx) * 0.12f
            ty += (gy - ty) * 0.12f
            bgImg.translationX = tx
            bgImg.translationY = ty
        }

        override fun onAccuracyChanged(s: Sensor?, acc: Int) {}
    }
    private var panel: AuthPanel? = null
    private var after: PendingIntent? = null
    private var inB = 0
    private var y0 = 0f
    private var vt: VelocityTracker? = null
    private var holdAnim: ValueAnimator? = null
    private val slop = ViewConfiguration.get(a).scaledTouchSlop
    private val fire = Runnable {
        finger.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        after = null
        unlock(null)
    }

    init {
        isClickable = true
        // свои обои экрана блокировки (если выбраны в "Настроить обои") лежат под затемнением
        bgImg.scaleType = ImageView.ScaleType.CENTER_CROP
        addView(bgImg, FrameLayout.LayoutParams(-1, -1))
        val scrim = View(a)
        scrim.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x22000000, 0x77000000))
        addView(scrim, FrameLayout.LayoutParams(-1, -1))
        content.orientation = LinearLayout.VERTICAL

        clock.format12Hour = "h:mm"
        clock.format24Hour = "HH:mm"
        clock.setTextColor(LockTheme.accent(a))
        clock.textSize = 84f
        clock.typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        clock.setShadowLayer(10f, 0f, 3f, 0x55000000)
        clock.includeFontPadding = false
        val date = TextClock(a)
        date.format12Hour = "EEE, d MMM"
        date.format24Hour = "EEE, d MMM"
        date.setTextColor(Color.WHITE)
        date.textSize = 17f
        alarm.setTextColor(Color.WHITE)
        alarm.textSize = 16f
        val head = LinearLayout(a)
        head.orientation = LinearLayout.VERTICAL
        head.setPadding(a.dp(28), a.dp(40), a.dp(28), 0)
        head.addView(clock)
        head.addView(date)
        head.addView(alarm)
        content.addView(head, LinearLayout.LayoutParams(-1, -2))
        content.addView(View(a), LinearLayout.LayoutParams(-1, 0, 1f))
        cards.orientation = LinearLayout.VERTICAL
        val cl = LinearLayout.LayoutParams(-1, -2)
        cl.setMargins(a.dp(16), 0, a.dp(16), a.dp(12))
        content.addView(cards, cl)
        val fl = LinearLayout.LayoutParams(a.dp(64), a.dp(64))
        fl.gravity = Gravity.CENTER_HORIZONTAL
        fl.bottomMargin = a.dp(28)
        content.addView(finger, fl)
        addView(content, FrameLayout.LayoutParams(-1, -1))
        finger.setOnTouchListener { _, e -> hold(e) }
        refresh()
    }

    private fun hold(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                finger.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                val va = ValueAnimator.ofFloat(0f, 1f)
                va.duration = 280
                va.addUpdateListener { finger.ring = it.animatedValue as Float }
                holdAnim = va
                va.start()
                finger.postDelayed(fire, 280)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                finger.removeCallbacks(fire)
                holdAnim?.cancel()
                finger.ring = 0f
            }
        }
        return true
    }

    @Suppress("DEPRECATION")
    override fun onApplyWindowInsets(i: WindowInsets): WindowInsets {
        val t: Int
        val b: Int
        if (Build.VERSION.SDK_INT >= 30) {
            val s = i.getInsets(WindowInsets.Type.systemBars())
            t = s.top
            b = s.bottom
        } else {
            t = i.systemWindowInsetTop
            b = i.systemWindowInsetBottom
        }
        inB = b
        content.setPadding(0, t, 0, b)
        return super.onApplyWindowInsets(i)
    }

    private fun loadBg() {
        val f = File(a.filesDir, "lock_wall.jpg")
        if (f.exists()) {
            try {
                bgImg.setImageBitmap(BitmapFactory.decodeFile(f.absolutePath))
            } catch (e: Throwable) {
                bgImg.setImageDrawable(null)
            }
        } else {
            bgImg.setImageDrawable(null)
        }
        val three = Prefs(a).lock3d == 1 && bgImg.drawable != null
        bgImg.scaleX = if (three) 1.12f else 1f
        bgImg.scaleY = if (three) 1.12f else 1f
        bgImg.translationX = 0f
        bgImg.translationY = 0f
    }

    // 3D-эффект: фон чуть сдвигается при наклоне телефона
    private fun startTilt() {
        stopTilt()
        if (Prefs(a).lock3d != 1 || bgImg.drawable == null) return
        val m = a.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val s = m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        m.registerListener(tilt, s, SensorManager.SENSOR_DELAY_GAME)
        sensors = m
    }

    private fun stopTilt() {
        sensors?.unregisterListener(tilt)
        sensors = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        loadBg()
        startTilt()
        requestApplyInsets()
        // цвет часов берётся из обоев, поэтому обновляем при каждом показе экрана
        clock.setTextColor(LockTheme.accent(a))
        NotifService.onChange = { post { refresh() } }
        refresh()
    }

    override fun onDetachedFromWindow() {
        stopTilt()
        NotifService.onChange = null
        super.onDetachedFromWindow()
    }

    fun refresh() {
        val am = a.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val info = am.nextAlarmClock
        if (info == null) {
            alarm.visibility = View.GONE
        } else {
            alarm.visibility = View.VISIBLE
            alarm.text = "⏰ " + DateFormat.getTimeFormat(a).format(Date(info.triggerTime))
        }
        finger.visibility = if (sp.fp) View.VISIBLE else View.GONE
        cards.removeAllViews()
        if (!sp.notif) return
        val list = NotifService.list()
            .filter {
                val ex = it.notification.extras
                it.packageName != a.packageName &&
                    (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 &&
                    (ex.getCharSequence(Notification.EXTRA_TITLE) != null || ex.getCharSequence(Notification.EXTRA_TEXT) != null)
            }
            .sortedByDescending { it.postTime }
            .take(4)
        for (n in list) {
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.bottomMargin = a.dp(8)
            cards.addView(card(n), lp)
        }
    }

    private fun card(n: StatusBarNotification): View {
        val nt = n.notification
        val ex = nt.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        var app = n.packageName
        try {
            val pm = a.packageManager
            app = pm.getApplicationLabel(pm.getApplicationInfo(n.packageName, 0)).toString()
        } catch (e: Exception) {
        }
        val row = LinearLayout(a)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(a.dp(14), a.dp(12), a.dp(16), a.dp(12))
        val bg = GradientDrawable()
        bg.setColor(0xE6F6E9EF.toInt())
        bg.cornerRadius = a.dp(26).toFloat()
        row.background = bg

        val iv = ImageView(a)
        val circle = GradientDrawable()
        circle.shape = GradientDrawable.OVAL
        circle.setColor(if (nt.color != 0) nt.color else 0xFF7A5C6B.toInt())
        iv.background = circle
        iv.setPadding(a.dp(8), a.dp(8), a.dp(8), a.dp(8))
        try {
            val dr = nt.smallIcon?.loadDrawable(a)
            dr?.setTint(Color.WHITE)
            iv.setImageDrawable(dr)
        } catch (e: Exception) {
        }
        row.addView(iv, LinearLayout.LayoutParams(a.dp(38), a.dp(38)))

        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(a.dp(12), 0, a.dp(8), 0)
        val t1 = TextView(a)
        t1.text = if (title.isNotEmpty()) title else app
        t1.setTextColor(0xFF2B2226.toInt())
        t1.textSize = 15f
        t1.typeface = Typeface.DEFAULT_BOLD
        t1.maxLines = 1
        t1.ellipsize = TextUtils.TruncateAt.END
        val t2 = TextView(a)
        t2.text = if (text.isNotEmpty()) text else app
        t2.setTextColor(0xFF5B4A52.toInt())
        t2.textSize = 14f
        t2.maxLines = 1
        t2.ellipsize = TextUtils.TruncateAt.END
        col.addView(t1)
        col.addView(t2)
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))

        val time = TextView(a)
        time.text = DateFormat.getTimeFormat(a).format(Date(n.postTime))
        time.setTextColor(0xFF7A6870.toInt())
        time.textSize = 12f
        row.addView(time)
        row.setOnClickListener {
            after = nt.contentIntent
            begin()
        }
        return row
    }

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            y0 = e.rawY
            vt?.recycle()
            vt = VelocityTracker.obtain()
        }
        vt?.addMovement(e)
        return super.dispatchTouchEvent(e)
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        return panel == null && e.actionMasked == MotionEvent.ACTION_MOVE && y0 - e.rawY > slop
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (panel != null) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dy = minOf(0f, e.rawY - y0)
                content.translationY = dy * 0.4f
                content.alpha = maxOf(0.3f, 1f + dy / (height * 1.2f))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                vt?.computeCurrentVelocity(1000)
                val vy = vt?.yVelocity ?: 0f
                val dy = e.rawY - y0
                content.animate().translationY(0f).alpha(1f).setDuration(220).start()
                if (e.actionMasked == MotionEvent.ACTION_UP && (dy < -height * 0.15f || vy < -1100f)) begin()
            }
        }
        return true
    }

    private fun begin() {
        if (sp.type == 0 || !sp.ready()) {
            val p = after
            after = null
            unlock(p)
        } else {
            showPanel()
        }
    }

    private fun showPanel() {
        if (panel != null) return
        val p = AuthPanel(a, sp, {
            val x = after
            after = null
            unlock(x)
        }, { closePanel(true) })
        p.setPadding(a.dp(20), a.dp(22), a.dp(20), inB + a.dp(16))
        addView(p, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        p.translationY = 700f * a.resources.displayMetrics.density
        p.animate().translationY(0f).setDuration(260).start()
        content.animate().alpha(0.2f).setDuration(200).start()
        panel = p
    }

    fun closePanel(dropAfter: Boolean = false) {
        val p = panel ?: return
        panel = null
        if (dropAfter) after = null
        p.animate().translationY(700f * a.resources.displayMetrics.density).setDuration(200)
            .withEndAction { removeView(p) }.start()
        content.animate().alpha(1f).setDuration(200).start()
    }
}
