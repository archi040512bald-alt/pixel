package com.example.pixellauncher

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.FrameLayout

object LockHost {
    var locked = true
    private var act: Activity? = null
    private var root: FrameLayout? = null
    private var view: LockView? = null
    private var rcv: BroadcastReceiver? = null
    private var hideHome: () -> Unit = {}
    private var showHome: () -> Unit = {}

    fun attach(a: Activity, r: FrameLayout, hide: () -> Unit, show: () -> Unit) {
        act = a
        root = r
        hideHome = hide
        showHome = show
        val x = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                locked = true
                sync()
            }
        }
        rcv = x
        a.registerReceiver(x, IntentFilter(Intent.ACTION_SCREEN_OFF))
        sync()
    }

    fun detach() {
        try {
            val x = rcv
            if (x != null) act?.unregisterReceiver(x)
        } catch (e: Exception) {
        }
        rcv = null
        val v = view
        if (v != null) root?.removeView(v)
        view = null
        act = null
        root = null
    }

    fun resume() = sync()

    fun back(): Boolean {
        val v = view ?: return false
        v.closePanel()
        return true
    }

    fun sync() {
        val a = act ?: return
        val r = root ?: return
        if (LockPrefs(a).on && locked) {
            if (view == null) {
                val v = LockView(a) { pi -> unlock(pi) }
                view = v
                hideHome()
                r.addView(v, FrameLayout.LayoutParams(-1, -1))
            } else {
                view?.refresh()
            }
        } else if (view != null) {
            unlock(null)
        }
    }

    private fun unlock(pi: PendingIntent?) {
        locked = false
        val v = view ?: return
        view = null
        showHome()
        v.animate().translationY(-v.height * 0.3f).alpha(0f).setDuration(240)
            .withEndAction { root?.removeView(v) }.start()
        if (pi != null) {
            try {
                pi.send()
            } catch (e: Exception) {
            }
        }
    }
}
