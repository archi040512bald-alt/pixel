package com.example.pixellauncher

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout

abstract class HomeBase : Activity() {
    protected val EMPH = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    protected lateinit var home: FrameLayout
    protected lateinit var drawer: LinearLayout
    protected var progress = 0f
    private var anim: ValueAnimator? = null
    private var downY = 0f
    private var startP = 0f
    private var vt: VelocityTracker? = null

    protected fun onSwipe(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                anim?.cancel(); downY = e.rawY; startP = progress
                vt = VelocityTracker.obtain(); vt?.addMovement(e)
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                progress = (startP + (downY - e.rawY) / (home.height * 0.7f)).coerceIn(0f, 1f)
                applyProgress()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                vt?.addMovement(e); vt?.computeCurrentVelocity(1000)
                val vy = vt?.yVelocity ?: 0f
                vt?.recycle(); vt = null
                setProgress(if (vy < -900f) 1f else if (vy > 900f) 0f else if (progress > 0.5f) 1f else 0f)
            }
        }
        return true
    }

    protected fun setProgress(to: Float) {
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

    override fun onResume() {
        super.onResume()
        val s = 1f - 0.08f * progress
        home.animate().scaleX(s).scaleY(s).alpha(1f - progress).setDuration(300).setInterpolator(EMPH).start()
        drawer.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).setInterpolator(EMPH).start()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == Intent.ACTION_MAIN) setProgress(0f)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (progress > 0f) setProgress(0f)
    }
}
