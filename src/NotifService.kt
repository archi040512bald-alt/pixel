package com.example.pixellauncher

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotifService : NotificationListenerService() {
    companion object {
        var inst: NotifService? = null
        var onChange: (() -> Unit)? = null

        fun list(): List<StatusBarNotification> {
            return try {
                inst?.activeNotifications?.toList() ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    override fun onListenerConnected() {
        inst = this
        Badges.refresh()
        onChange?.invoke()
    }

    override fun onListenerDisconnected() {
        inst = null
        Badges.refresh()
        onChange?.invoke()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        Badges.refresh()
        onChange?.invoke()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        Badges.refresh()
        onChange?.invoke()
    }
}
