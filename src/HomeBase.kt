package com.example.pixellauncher

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.abs

abstract class HomeBase : Activity() {
    protected val prefs by lazy { Prefs(this) }
    protected val EMPH = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    protected lateinit var home: FrameLayout
    protected lateinit var drawer: LinearLayout
    protected var progress = 0f
    private var anim: ValueAnimator? = null
    private var downY = 0f
    private var downX = 0f
    private var startP = 0f
    private var startFrac = 0f
    private var mode = 0
    private var vt: VelocityTracker? = null
    private var lastResume = 0L
    private var paused = false

    protected open fun pagingAllowed(): Boolean = false
    protected open fun drawerAllowed(): Boolean = true
    protected open fun pageStart(): Float = 0f
    protected open fun pageMove(f: Float) {}
    protected open fun pageEnd(vx: Float) {}
    protected fun swiping(): Boolean = mode == 1 || mode == 2

    protected fun onSwipe(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                anim?.cancel()
                downY = e.rawY
                downX = e.rawX
                startP = progress
                startFrac = pageStart()
                mode = 0
                vt?.recycle()
                vt = VelocityTracker.obtain()
                vt?.addMovement(e)
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                if (mode == 0) {
                    val dx = abs(e.rawX - downX)
                    val dy = abs(e.rawY - downY)
                    if (maxOf(dx, dy) > ViewConfiguration.get(this).scaledTouchSlop) {
                        mode = if (progress > 0f) 1
                        else if (dx > dy && pagingAllowed()) 2
                        else if (drawerAllowed()) 1
                        else 3
                    }
                }
                if (mode == 1) {
                    progress = (startP + (downY - e.rawY) / (home.height * 0.7f)).coerceIn(0f, 1f)
                    applyProgress()
                } else if (mode == 2) {
                    pageMove(startFrac - (e.rawX - downX) / home.width)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                vt?.addMovement(e)
                vt?.computeCurrentVelocity(1000)
                if (mode == 1) {
                    val vy = vt?.yVelocity ?: 0f
                    animateTo(if (vy < -900f) 1f else if (vy > 900f) 0f else if (progress > 0.5f) 1f else 0f)
                } else if (mode == 2) {
                    pageEnd(vt?.xVelocity ?: 0f)
                } else if (progress > 0f && progress < 1f) {
                    animateTo(if (progress > 0.5f) 1f else 0f)
                }
                vt?.recycle()
                vt = null
                mode = 0
            }
        }
        return true
    }

    protected fun animateTo(to: Float) {
        anim?.cancel()
        val va = ValueAnimator.ofFloat(progress, to)
        va.duration = 350
        va.interpolator = EMPH
        va.addUpdateListener { progress = it.animatedValue as Float; applyProgress() }
        anim = va
        va.start()
    }

    protected fun applyProgress() {
        val h = home.height.toFloat().coerceAtLeast(1f)
        drawer.visibility = if (progress > 0f) View.VISIBLE else View.INVISIBLE
        drawer.translationY = (1f - progress) * h
        home.alpha = 1f - progress
        home.scaleX = 1f - 0.08f * progress
        home.scaleY = home.scaleX
        if (progress == 0f) {
            val im = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            im.hideSoftInputFromWindow(drawer.windowToken, 0)
        }
    }

    private fun settle(stamp: Long) {
        if (isDestroyed || paused || stamp != lastResume || anim?.isRunning == true) return
        applyProgress()
        drawer.alpha = 1f
        drawer.scaleX = 1f
        drawer.scaleY = 1f
    }

    override fun onPause() {
        super.onPause()
        paused = true
    }

    override fun onResume() {
        super.onResume()
        paused = false
        lastResume = System.currentTimeMillis()
        val stamp = lastResume
        home.animate().cancel()
        drawer.animate().cancel()
        if (progress > 0f) {
            applyProgress()
            drawer.alpha = 1f
            drawer.scaleX = 1f
            drawer.scaleY = 1f
        }
        Fx.back(this, prefs, home, drawer, progress)
        home.postDelayed({ settle(stamp) }, prefs.speed + 200L)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action != Intent.ACTION_MAIN) return
        if (System.currentTimeMillis() - lastResume < 600) return
        animateTo(0f)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (progress > 0f) animateTo(0f)
    }
}
