package com.example.pixellauncher

import android.content.Intent
import android.net.Uri
import android.os.Build
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
import com.google.android.material.materialswitch.MaterialSwitch
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

    private fun l(ru: String, en: String) = L(this, ru, en)

    override fun onCreate(s: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(s)
        p = Prefs(this)
        val screen = intent.getStringExtra("s") ?: "main"
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        val bar = MaterialToolbar(this)
        bar.title = when (screen) {
            "desk" -> l("Рабочий стол", "Desktop")
            "anim" -> l("Анимации", "Animations")
            "icons" -> l("Иконки", "Icons")
            "grid" -> l("Сетка", "Grid")
            "wall" -> l("Обои", "Wallpaper")
            "lang" -> l("Язык", "Language")
            else -> l("Настройки", "Settings")
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
            "desk" -> desk()
            "anim" -> anim()
            "icons" -> icons()
            "grid" -> {
                slider(l("Колонок", "Columns"), 3, 6, p.cols, "") { p.cols = it }
                slider(l("Строк на рабочем столе", "Desktop rows"), 3, 7, p.rows, "") { p.rows = it }
            }
            "wall" -> row(l("Выбрать картинку", "Choose image"), l("Из галереи или файлов", "From gallery or files")) {
                pick.launch("image/*")
            }
            "lang" -> {
                val codes = arrayOf("", "ru", "en")
                radios(listOf(l("Системный", "System"), "Русский", "English"), codes.indexOf(p.lang).coerceAtLeast(0)) {
                    p.lang = codes[it]
                    val i = Intent(this, SettingsActivity::class.java)
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(i)
                }
            }
            else -> {
                row(l("Рабочий стол", "Desktop"), l("Док, значки, поиск", "Dock, icons, search")) { go("desk") }
                row(l("Анимации", "Animations"), l("Пресеты, размытие, зум, скорость", "Presets, blur, zoom, speed")) { go("anim") }
                row(l("Иконки", "Icons"), l("Форма и иконпак", "Shape and icon pack")) { go("icons") }
                row(l("Сетка", "Grid"), l("Колонки и строки", "Columns and rows")) { go("grid") }
                row(l("Обои", "Wallpaper"), l("Выбрать картинку для обоев", "Pick a wallpaper image")) { go("wall") }
                row(l("Язык", "Language"), l("Русский, English", "Russian, English")) { go("lang") }
                row(l("Лаунчер по умолчанию", "Default launcher"), l("Открыть системные настройки", "Open system settings")) {
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

    private fun head(t: String) {
        val h = text(t, titleStyle)
        h.setPadding(dp(8), dp(16), 0, dp(8))
        list.addView(h)
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

    private fun toggle(label: String, on: Boolean, set: (Boolean) -> Unit) {
        val sw = MaterialSwitch(this)
        sw.text = label
        sw.isChecked = on
        sw.setOnCheckedChangeListener { _, v -> set(v) }
        list.addView(sw)
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

    private fun radios(items: List<String>, checked: Int, on: (Int) -> Unit) {
        val g = RadioGroup(this)
        for (i in items.indices) {
            val r = MaterialRadioButton(this)
            r.text = items[i]
            r.id = 100 + i
            g.addView(r)
        }
        g.check(100 + checked)
        g.setOnCheckedChangeListener { _, id -> on(id - 100) }
        list.addView(g)
    }

    private fun desk() {
        head(l("Док", "Dock"))
        toggle(l("Фон для дока", "Dock background"), p.dockBg == 1) { p.dockBg = if (it) 1 else 0 }
        slider(l("Скругление фона дока", "Dock corner radius"), 0, 40, p.dockR, " dp") { p.dockR = it }
        toggle(l("Панель поиска", "Search bar"), p.search == 1) { p.search = if (it) 1 else 0 }
        head(l("Значки", "Icons"))
        slider(l("Размер значков", "Icon size"), 40, 80, p.iconSize, " dp") { p.iconSize = it }
        toggle(l("Названия приложений", "App names"), p.labels == 1) { p.labels = if (it) 1 else 0 }
    }

    private fun anim() {
        head(l("Пресет", "Preset"))
        radios(listOf(presetName(this, 0), presetName(this, 1), presetName(this, 2)), p.preset) {
            p.applyPreset(it)
            recreate()
        }
        head(l("Эффекты", "Effects"))
        toggle(l("Размытие в анимации", "Blur in animation"), p.blur == 1) { p.blur = if (it) 1 else 0 }
        if (Build.VERSION.SDK_INT < 31) {
            val n = text(l("Размытие работает на Android 12 и новее", "Blur needs Android 12 or newer"), bodyStyle)
            n.alpha = 0.7f
            list.addView(n)
        }
        slider(l("Сила размытия", "Blur strength"), 1, 30, p.blurR, "") { p.blurR = it }
        slider(l("Зум фона", "Background zoom"), -15, 15, p.zoom, " %") { p.zoom = it }
        head(l("Параметры", "Parameters"))
        slider(l("Радиус углов", "Corner radius"), 0, 60, p.radius, " dp") { p.radius = it }
        slider(l("Длительность", "Duration"), 150, 800, p.speed, l(" мс", " ms")) { p.speed = it }
        slider(l("Пауза перед запуском", "Launch delay"), 0, 500, p.pause, l(" мс", " ms")) { p.pause = it }
        head(l("Горизонтальные приложения", "Landscape apps"))
        toggle(l("Особая анимация", "Special animation"), p.landOn == 1) { p.landOn = if (it) 1 else 0 }
        slider(l("Длительность", "Duration"), 300, 1200, p.landSpeed, l(" мс", " ms")) { p.landSpeed = it }
        slider(l("Радиус углов", "Corner radius"), 0, 60, p.landR, " dp") { p.landR = it }
    }

    private fun icons() {
        var cur = l("Системные", "System")
        if (p.pack.isNotEmpty()) {
            cur = IconPacks.find(this).firstOrNull { it.first == p.pack }?.second ?: p.pack
        }
        row(l("Иконпак", "Icon pack"), cur) { pickPack() }
        head(l("Форма иконок", "Icon shape"))
        radios(shapeNames(this), p.shape) { p.shape = it }
    }

    private fun pickPack() {
        val packs = IconPacks.find(this)
        if (packs.isEmpty()) {
            MaterialAlertDialogBuilder(this).setTitle(l("Иконпаки не найдены", "No icon packs found"))
                .setMessage(l("Установите иконпак (например, из Google Play), и он появится здесь.",
                    "Install an icon pack (for example from Google Play) and it will appear here."))
                .setPositiveButton("OK", null).show()
            return
        }
        val names = ArrayList<String>()
        names.add(l("Системные", "System"))
        for (x in packs) names.add(x.second)
        MaterialAlertDialogBuilder(this).setTitle(l("Иконпак", "Icon pack"))
            .setItems(names.toTypedArray()) { _, i ->
                p.pack = if (i == 0) "" else packs[i - 1].first
                recreate()
            }.show()
    }
}
