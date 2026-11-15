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
        onChange?.invoke()
    }

    override fun onListenerDisconnected() {
        inst = null
        onChange?.invoke()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        onChange?.invoke()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        onChange?.invoke()
    }
}
