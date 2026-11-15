package com.example.pixellauncher

import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.radiobutton.MaterialRadioButton

class LockSettingsActivity : AppCompatActivity() {
    private lateinit var sp: LockPrefs
    private lateinit var list: LinearLayout
    private var night = false
    private val ink: Int get() = if (night) 0xFFD5DEE6.toInt() else 0xFF37474F.toInt()
    private val titleStyle = com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
    private val bodyStyle = com.google.android.material.R.style.TextAppearance_Material3_BodyMedium

    private fun l(ru: String, en: String) = L(this, ru, en)

    override fun onCreate(s: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(s)
        sp = LockPrefs(this)
        night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val colors = if (night) intArrayOf(0xFF171C21.toInt(), 0xFF0E1216.toInt())
        else intArrayOf(0xFFEAF2F8.toInt(), 0xFFDAE6EE.toInt())
        window.setBackgroundDrawable(GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors))
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(16), dp(16), dp(16), dp(12))
        val back = GlyphView(this, "back", ink)
        back.setOnClickListener { finish() }
        head.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
        val title = TextView(this)
        title.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium)
        title.text = l("Экран блокировки", "Lock screen")
        title.setPadding(dp(16), 0, 0, 0)
        head.addView(title)
        root.addView(head, LinearLayout.LayoutParams(-1, -2))
        val sv = ScrollView(this)
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(16), dp(4), dp(16), dp(24))
        sv.addView(list)
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun text(t: String, style: Int): TextView {
        val v = TextView(this)
        v.text = t
        v.setTextAppearance(style)
        return v
    }

    private fun head(t: String) {
        val h = text(t, titleStyle)
        h.setPadding(dp(8), dp(16), 0, dp(8))
        list.addView(h)
    }

    private fun note(t: String) {
        val n = text(t, bodyStyle)
        n.alpha = 0.7f
        n.setPadding(dp(8), dp(4), dp(8), dp(4))
        list.addView(n)
    }

    private fun toggle(label: String, on: Boolean, set: (Boolean) -> Unit) {
        val sw = MaterialSwitch(this)
        sw.text = label
        sw.isChecked = on
        sw.setOnCheckedChangeListener { _, v -> set(v) }
        list.addView(sw)
    }

    private fun row(title: String, sub: String, click: () -> Unit) {
        val card = MaterialCardView(this)
        card.radius = dp(22).toFloat()
        card.cardElevation = dp(2).toFloat()
        card.setCardBackgroundColor(if (night) 0xFF262C33.toInt() else 0xE6FFFFFF.toInt())
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(16), dp(20), dp(16))
        val st = text(sub, bodyStyle)
        st.alpha = 0.7f
        box.addView(text(title, titleStyle))
        box.addView(st)
        card.addView(box)
        card.setOnClickListener { click() }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(8)
        lp.bottomMargin = dp(8)
        list.addView(card, lp)
    }

    private fun build() {
        list.removeAllViews()
        note(l("Это экран блокировки лаунчера: он показывается, когда вы открываете лаунчер после выключения экрана. Системный экран блокировки телефона остаётся, и защитой данных это не заменяет.",
            "This is the launcher's own lock screen: it appears when you open the launcher after the screen was turned off. The system lock screen stays and this does not replace it."))
        toggle(l("Экран блокировки", "Lock screen"), sp.on) { sp.on = it }
        head(l("Защита", "Protection"))
        val names = listOf(l("Нет", "None"), l("PIN-код (4 цифры)", "PIN (4 digits)"), l("Графический ключ", "Pattern"))
        val g = RadioGroup(this)
        for (i in names.indices) {
            val r = MaterialRadioButton(this)
            r.text = names[i]
            r.id = 100 + i
            g.addView(r)
        }
        g.check(100 + (if (sp.ready()) sp.type else 0))
        g.setOnCheckedChangeListener { _, id ->
            when (id - 100) {
                0 -> sp.type = 0
                1 -> setupPin()
                else -> setupPattern()
            }
        }
        list.addView(g)
        head(l("Уведомления", "Notifications"))
        toggle(l("Показывать уведомления", "Show notifications"), sp.notif) { sp.notif = it }
        val granted = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        row(l("Доступ к уведомлениям", "Notification access"),
            if (granted) l("Выдан. Нажмите, чтобы изменить", "Granted. Tap to change")
            else l("Не выдан. Нажмите, чтобы открыть настройки", "Not granted. Tap to open settings")) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        note(l("Если переключатель в системных настройках серый: Настройки телефона → Приложения → Pixel Launcher → ⋮ → «Разрешить ограниченные настройки».",
            "If the switch in system settings is greyed out: phone Settings → Apps → Pixel Launcher → ⋮ → Allow restricted settings."))
        head(l("Отпечаток пальца", "Fingerprint"))
        toggle(l("Значок отпечатка (имитация)", "Fingerprint icon (fake)"), sp.fp) { sp.fp = it }
        note(l("Это не настоящий датчик: удержание значка просто снимает блокировку без кода.",
            "This is not a real sensor: holding the icon just unlocks without a code."))
    }

    private fun setupPin() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(24), dp(12), dp(24), 0)
        val e1 = EditText(this)
        val e2 = EditText(this)
        for (e in listOf(e1, e2)) {
            e.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            e.filters = arrayOf(InputFilter.LengthFilter(4))
            box.addView(e)
        }
        e1.hint = l("PIN-код", "PIN")
        e2.hint = l("Повторите PIN-код", "Repeat PIN")
        MaterialAlertDialogBuilder(this).setTitle(l("PIN-код из 4 цифр", "4-digit PIN")).setView(box)
            .setPositiveButton("OK") { _, _ ->
                val x = e1.text.toString()
                if (x.length == 4 && x == e2.text.toString()) {
                    sp.save(x)
                    sp.type = 1
                } else {
                    Toast.makeText(this, l("Нужны 4 одинаковые цифры", "Enter the same 4 digits twice"), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(l("Отмена", "Cancel"), null)
            .setOnDismissListener { build() }.show()
    }

    private fun setupPattern() {
        var first = ""
        var dlg: AlertDialog? = null
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(dp(24), dp(16), dp(24), dp(8))
        val hint = TextView(this)
        hint.text = l("Нарисуйте ключ (минимум 4 точки)", "Draw a pattern (at least 4 dots)")
        box.addView(hint)
        val pv = PatternView(this, true) { v, code ->
            if (code.length < 4) {
                hint.text = l("Слишком коротко, нужно минимум 4 точки", "Too short, use at least 4 dots")
                v.fail()
            } else if (first.isEmpty()) {
                first = code
                hint.text = l("Повторите ключ", "Draw it again")
                v.postDelayed({ v.reset() }, 350)
            } else if (first == code) {
                sp.save(code)
                sp.type = 2
                dlg?.dismiss()
            } else {
                first = ""
                hint.text = l("Не совпало, начните заново", "Doesn't match, start over")
                v.fail()
            }
        }
        box.addView(pv, LinearLayout.LayoutParams(dp(260), dp(260)))
        dlg = MaterialAlertDialogBuilder(this).setTitle(l("Графический ключ", "Pattern")).setView(box)
            .setNegativeButton(l("Отмена", "Cancel"), null)
            .setOnDismissListener { build() }.show()
    }
}
