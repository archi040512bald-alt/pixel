package com.example.pixellauncher

import android.app.Notification
import android.os.Handler
import android.os.Looper

/** Счётчики уведомлений по приложениям. Обновляется из NotifService, перерисовывает видимые значки. */
object Badges {
    var counts: Map<String, Int> = emptyMap()
    val views = HashSet<ShapedIcon>()
    private val h = Handler(Looper.getMainLooper())

    fun refresh() {
        val m = HashMap<String, Int>()
        for (s in NotifService.list()) {
            val n = s.notification ?: continue
            if (s.isOngoing) continue
            if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) continue
            m[s.packageName] = (m[s.packageName] ?: 0) + 1
        }
        counts = m
        h.post { for (v in views.toList()) v.invalidate() }
    }
}
