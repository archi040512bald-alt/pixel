package com.example.pixellauncher

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.slider.Slider

class SettingsActivity : AppCompatActivity() {
    private lateinit var p: Prefs
    private lateinit var list: LinearLayout
    private var night = false
    private val ink: Int get() = if (night) 0xFFD5DEE6.toInt() else 0xFF37474F.toInt()
    private val rowStyle = com.google.android.material.R.style.TextAppearance_Material3_TitleLarge
    private val titleStyle = com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
    private val bodyStyle = com.google.android.material.R.style.TextAppearance_Material3_BodyMedium
    private var wall: WallScreen? = null
    private val pick = registerForActivityResult(ActivityResultContracts.GetContent()) { u: Uri? ->
        if (u != null) {
            applyWallpaper(this, u)
            Thread {
                val b = decodeUri(this, u)
                if (b != null) {
                    saveFile(this, b, "home_orig.jpg")
                    saveFile(this, b, "home_cur.jpg")
                }
            }.start()
            wall?.refresh()
        }
    }

    fun pickWallpaper() {
        pick.launch("image/*")
    }

    private var customCb: ((Uri) -> Unit)? = null
    private val pickCustom = registerForActivityResult(ActivityResultContracts.GetContent()) { u: Uri? ->
        val cb = customCb
        customCb = null
        if (u != null && cb != null) cb(u)
    }

    fun pickForCustom(cb: (Uri) -> Unit) {
        customCb = cb
        pickCustom.launch("image/*")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == WallScreen.REQ) wall?.onPermission()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (wall?.onBack() == true) return
        super.onBackPressed()
    }

    private fun l(ru: String, en: String) = L(this, ru, en)

    override fun onCreate(s: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(s)
        p = Prefs(this)
        night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val screen = intent.getStringExtra("s") ?: "main"
        if (screen == "wall") {
            // экран обоев рисуется целиком отдельным видом (тёмный, как системный выбор обоев)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xFF0F1114.toInt()))
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            val ws = WallScreen(this)
            wall = ws
            setContentView(ws)
            return
        }

        val colors = if (night) intArrayOf(0xFF171C21.toInt(), 0xFF0E1216.toInt())
        else intArrayOf(0xFFEAF2F8.toInt(), 0xFFDAE6EE.toInt())
        window.setBackgroundDrawable(GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, ins ->
            val b = ins.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, b.top, 0, b.bottom)
            ins
        }
        WindowCompat.getInsetsController(window, root).isAppearanceLightStatusBars = !night

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(16), dp(16), dp(16), dp(12))
        val back = GlyphView(this, "back", ink)
        back.setOnClickListener { finish() }
        head.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
        val title = TextView(this)
        title.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium)
        title.text = when (screen) {
            "desk" -> l("Рабочий стол", "Desktop")
            "anim" -> l("Анимации", "Animations")
            "icons" -> l("Иконки", "Icons")
            "grid" -> l("Сетка", "Grid")
            "wall" -> l("Обои", "Wallpaper")
            "lang" -> l("Язык", "Language")
            else -> l("Настройки", "Settings")
        }
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

        when (screen) {
            "desk" -> desk()
            "anim" -> anim()
            "icons" -> icons()
            "grid" -> {
                slider(l("Колонок", "Columns"), 3, 6, p.cols, "") { p.cols = it }
                slider(l("Строк на рабочем столе", "Desktop rows"), 3, 7, p.rows, "") { p.rows = it }
            }
            "wall" -> row("image", l("Выбрать картинку", "Choose image"), l("Из галереи или файлов", "From gallery or files")) {
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
                row("home", l("Рабочий стол", "Desktop"), l("Док, значки, поиск, страницы", "Dock, icons, search, pages")) { go("desk") }
                row("anim", l("Анимации", "Animations"), l("Пресеты, размытие, зум, скорость", "Presets, blur, zoom, speed")) { go("anim") }
                row("shapes", l("Иконки", "Icons"), l("Форма и иконпак", "Shape and icon pack")) { go("icons") }
                row("grid", l("Сетка", "Grid"), l("Колонки и строки", "Columns and rows")) { go("grid") }
                row("image", l("Обои", "Wallpaper"), l("Выбрать картинку для обоев", "Pick a wallpaper image")) { go("wall") }
                row("globe", l("Язык", "Language"), l("Системный, Русский, English", "System, Russian, English")) { go("lang") }
                row("launcher", l("Лаунчер по умолчанию", "Default launcher"), l("Открыть системные настройки", "Open system settings")) {
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

    private fun row(icon: String, title: String, sub: String, click: () -> Unit) {
        val card = MaterialCardView(this)
        card.radius = dp(26).toFloat()
        card.cardElevation = dp(3).toFloat()
        card.setCardBackgroundColor(if (night) 0xFF262C33.toInt() else 0xE6FFFFFF.toInt())
        val line = LinearLayout(this)
        line.orientation = LinearLayout.HORIZONTAL
        line.gravity = Gravity.CENTER_VERTICAL
        line.setPadding(dp(22), dp(18), dp(18), dp(18))
        line.addView(GlyphView(this, icon, ink), LinearLayout.LayoutParams(dp(30), dp(30)))
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), 0, dp(8), 0)
        val st = text(sub, bodyStyle)
        st.alpha = 0.7f
        box.addView(text(title, rowStyle))
        box.addView(st)
        line.addView(box, LinearLayout.LayoutParams(0, -2, 1f))
        line.addView(GlyphView(this, "chev", 0xFF9AA5AE.toInt()), LinearLayout.LayoutParams(dp(22), dp(22)))
        card.addView(line)
        card.setOnClickListener { click() }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.bottomMargin = dp(12)
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
        // уникальные id и без сохранения состояния: иначе после recreate() кнопки из разных групп
        // с одинаковыми id восстанавливаются неверно и запускают бесконечное пересоздание экрана
        g.isSaveEnabled = false
        val ids = IntArray(items.size) { View.generateViewId() }
        for (i in items.indices) {
            val r = MaterialRadioButton(this)
            r.text = tagged(items[i])
            r.isEnabled = !items[i].endsWith("Soon")
            r.id = ids[i]
            r.isSaveEnabled = false
            g.addView(r)
        }
        var cur = checked.coerceIn(0, items.size - 1)
        g.check(ids[cur])
        g.setOnCheckedChangeListener { _, id ->
            val idx = ids.indexOf(id)
            if (idx >= 0 && idx != cur) {
                cur = idx
                on(idx)
            }
        }
        list.addView(g)
    }

    private fun desk() {
        head(l("Док", "Dock"))
        toggle(l("Фон для дока", "Dock background"), p.dockBg == 1) { p.dockBg = if (it) 1 else 0 }
        slider(l("Скругление фона дока", "Dock corner radius"), 0, 40, p.dockR, " dp") { p.dockR = it }
        toggle(l("Панель поиска", "Search bar"), p.search == 1) { p.search = if (it) 1 else 0 }
        radios(listOf(l("Текущая панель", "Current bar"), l("Google поиск", "Google search")), p.searchStyle) {
            p.searchStyle = it
            recreate()
        }
        run {
            head(l("Цвет панели поиска", "Search bar color"))
            if (p.searchStyle != 1) {
                val sn = text(l("Работает для панели «Google поиск»: выберите её выше", "Works for the Google search bar: choose it above"), bodyStyle)
                sn.alpha = 0.7f
                list.addView(sn)
            }
            radios(listOf(l("По умолчанию", "Default"), l("Цвета обоев", "Wallpaper colors"), l("Свой цвет", "Custom color")), p.searchColor) {
                p.searchColor = it
                recreate()
            }
            if (p.searchColor == 2) radios(IconTheme.seedNames(this), p.searchSeed) { p.searchSeed = it }
            head(l("Значок Google", "Google icon"))
            radios(listOf(l("Цветной", "Colourful"), l("Одноцветный", "Single colour"), l("Лупа", "Magnifier"), l("Без значка", "None")), p.searchIcon) { p.searchIcon = it }
        }
        head(l("Страницы", "Pages"))
        toggle(l("Индикатор страниц", "Page indicator"), p.dots == 1) { p.dots = if (it) 1 else 0 }
        head(l("Меню приложений", "App drawer"))
        toggle(l("Меню приложений", "App drawer"), p.drawer == 1) { p.drawer = if (it) 1 else 0 }
        val note = text(l("Если выключить, все приложения окажутся на страницах рабочего стола, а свайп вверх отключится",
            "When off, all apps are placed on desktop pages and swipe up is disabled"), bodyStyle)
        note.alpha = 0.7f
        list.addView(note)
        head(l("Значки", "Icons"))
        slider(l("Размер значков", "Icon size"), 40, 80, p.iconSize, " dp") { p.iconSize = it }
        toggle(l("Названия приложений", "App names"), p.labels == 1) { p.labels = if (it) 1 else 0 }
        toggle(l("Счётчики уведомлений", "Notification badges"), p.badges == 1) { p.badges = if (it) 1 else 0 }
        if (!NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
            row("shapes", l("Доступ к уведомлениям", "Notification access"),
                l("Нужен для счётчиков, нажмите, чтобы выдать", "Needed for badges, tap to grant")) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        head(l("Цветные значки", "Themed icons"))
        radios(IconTheme.styleNames(this), p.iconStyle) {
            p.iconStyle = it
            recreate()
        }
        if (p.iconStyle != IconTheme.OFF) {
            if (p.iconStyle == IconTheme.CUSTOM) {
                head(l("Цвет значков", "Icon color"))
                radios(IconTheme.seedNames(this), p.iconSeed) {
                    p.iconSeed = it
                    p.iconHue = -1
                }
            }
            head(l("Оттенок значков", "Icon tone"))
            radios(listOf(l("Авто", "Auto"), l("Светлые", "Light"), l("Тёмные", "Dark")), p.iconTone) { p.iconTone = it }
            toggle(l("Красить и значки без цветной версии", "Also recolor icons without a themed version"), p.iconAll == 1) {
                p.iconAll = if (it) 1 else 0
            }
            val tn = text(l("Цвета обоев работают на Android 12 и новее, цветные версии значков есть у приложений на Android 13 и новее. Остальные значки остаются как есть, пока не включено окрашивание всех.",
                "Wallpaper colors need Android 12+, themed icon versions come from apps on Android 13+. Other icons stay as they are unless recoloring all is on."), bodyStyle)
            tn.alpha = 0.7f
            list.addView(tn)
        }
    }

    private fun anim() {
        head(l("Пресет", "Preset"))
        radios(listOf(presetName(this, 0), presetName(this, 1), presetName(this, 2), presetName(this, 3), presetName(this, 4)), p.preset) {
            p.applyPreset(it)
            if (it == MagicSpec.ID) {
                p.preset = it
                p.blur = 1
                p.zoom = 0
            }
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
        row("shapes", l("Иконпак", "Icon pack"), cur) { pickPack() }
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
