package com.example.pixellauncher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.slider.Slider

class SettingsActivity : AppCompatActivity() {
    private lateinit var p: Prefs
    private lateinit var list: LinearLayout
    private val titleStyle = com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
    private val bodyStyle = com.google.android.material.R.style.TextAppearance_Material3_BodyMedium
    private val pick = registerForActivityResult(ActivityResultContracts.GetContent()) { u: Uri? ->
        if (u != null) applyWallpaper(this, u)
    }

    override fun onCreate(s: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(s)
        p = Prefs(this)
        val screen = intent.getStringExtra("s") ?: "main"
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        val bar = MaterialToolbar(this)
        bar.title = when (screen) {
            "anim" -> "Анимации"
            "icons" -> "Иконки"
            "grid" -> "Сетка"
            "wall" -> "Обои"
            else -> "Настройки"
        }
        if (screen != "main") {
            bar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            bar.setNavigationOnClickListener { finish() }
        }
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        val sv = ScrollView(this)
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(16), dp(8), dp(16), dp(24))
        sv.addView(list)
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        when (screen) {
            "anim" -> {
                slider("Радиус углов", 0, 60, p.radius, " dp") { p.radius = it }
                slider("Длительность", 150, 800, p.speed, " мс") { p.speed = it }
                slider("Пауза перед запуском", 0, 500, p.pause, " мс") { p.pause = it }
            }
            "icons" -> icons()
            "grid" -> {
                slider("Колонок", 3, 6, p.cols, "") { p.cols = it }
                slider("Строк на рабочем столе", 3, 7, p.rows, "") { p.rows = it }
            }
            "wall" -> row("Выбрать картинку", "Из галереи или файлов") { pick.launch("image/*") }
            else -> {
                row("Анимации", "Радиус углов, скорость, пауза") { go("anim") }
                row("Иконки", "Форма и иконпак") { go("icons") }
                row("Сетка", "Колонки и строки") { go("grid") }
                row("Обои", "Выбрать картинку для обоев") { go("wall") }
                row("Лаунчер по умолчанию", "Открыть системные настройки") {
                    startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                }
            }
        }
    }

    private fun go(s: String) {
        val i = Intent(this, SettingsActivity::class.java)
        i.putExtra("s", s)
        startActivity(i)
    }

    private fun text(t: String, style: Int): TextView {
        val v = TextView(this)
        v.text = t
        v.setTextAppearance(style)
        return v
    }

    private fun row(title: String, sub: String, click: () -> Unit) {
        val card = MaterialCardView(this)
        card.radius = dp(20).toFloat()
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
        lp.bottomMargin = dp(8)
        list.addView(card, lp)
    }

    private fun slider(name: String, min: Int, max: Int, value: Int, unit: String, set: (Int) -> Unit) {
        val t = text(name + ": " + value + unit, titleStyle)
        t.setPadding(dp(8), dp(16), 0, 0)
        val sl = Slider(this)
        sl.valueFrom = min.toFloat()
        sl.valueTo = max.toFloat()
        sl.stepSize = 1f
        sl.value = value.coerceIn(min, max).toFloat()
        sl.addOnChangeListener { _, v, _ ->
            t.text = name + ": " + v.toInt() + unit
            set(v.toInt())
        }
        list.addView(t)
        list.addView(sl)
    }

    private fun icons() {
        var cur = "Системные"
        if (p.pack.isNotEmpty()) {
            cur = IconPacks.find(this).firstOrNull { it.first == p.pack }?.second ?: p.pack
        }
        row("Иконпак", cur) { pickPack() }
        val head = text("Форма иконок", titleStyle)
        head.setPadding(dp(8), dp(16), 0, dp(8))
        list.addView(head)
        val g = RadioGroup(this)
        for (i in SHAPES.indices) {
            val r = MaterialRadioButton(this)
            r.text = SHAPES[i]
            r.id = 100 + i
            g.addView(r)
        }
        g.check(100 + p.shape)
        g.setOnCheckedChangeListener { _, id -> p.shape = id - 100 }
        list.addView(g)
    }

    private fun pickPack() {
        val packs = IconPacks.find(this)
        if (packs.isEmpty()) {
            MaterialAlertDialogBuilder(this).setTitle("Иконпаки не найдены")
                .setMessage("Установите иконпак (например, из Google Play), и он появится здесь.")
                .setPositiveButton("OK", null).show()
            return
        }
        val names = ArrayList<String>()
        names.add("Системные")
        for (x in packs) names.add(x.second)
        MaterialAlertDialogBuilder(this).setTitle("Иконпак")
            .setItems(names.toTypedArray()) { _, i ->
                p.pack = if (i == 0) "" else packs[i - 1].first
                recreate()
            }.show()
    }
}
