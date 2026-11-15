package com.example.pixellauncher

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

class LockPrefs(c: Context) {
    private val sp = c.getSharedPreferences("lock", Context.MODE_PRIVATE)

    var on: Boolean
        get() = sp.getBoolean("on", false)
        set(v) { sp.edit().putBoolean("on", v).apply() }
    var type: Int
        get() = sp.getInt("type", 0)
        set(v) { sp.edit().putInt("type", v).apply() }
    var fp: Boolean
        get() = sp.getBoolean("fp", true)
        set(v) { sp.edit().putBoolean("fp", v).apply() }
    var notif: Boolean
        get() = sp.getBoolean("notif", true)
        set(v) { sp.edit().putBoolean("notif", v).apply() }

    private fun hash(code: String, salt: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest((salt + code).toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    fun save(code: String) {
        val b = ByteArray(8)
        SecureRandom().nextBytes(b)
        val salt = b.joinToString("") { "%02x".format(it) }
        sp.edit().putString("salt", salt).putString("hash", hash(code, salt)).apply()
    }

    fun check(code: String): Boolean {
        val salt = sp.getString("salt", "") ?: ""
        val h = sp.getString("hash", "") ?: ""
        return h.isNotEmpty() && hash(code, salt) == h
    }

    fun ready(): Boolean = type == 0 || (sp.getString("hash", "") ?: "").isNotEmpty()
}
