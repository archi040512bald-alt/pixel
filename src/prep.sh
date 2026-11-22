set -e
D=proj/app/src/main
K=$D/java/com/example/pixellauncher
mkdir -p $K $D/res/values
cp src/*.kt $K/
sed -i 's/search = TextView(this)/search = SearchBar(this)/' $K/MainActivity.kt
sed -i 's/intArrayOf(32, 420, 0, 1, 16, 6, 1)/intArrayOf(36, 420, 0, 1, 10, 8, 1)/' $K/Prefs.kt
sed -i 's/r\.text = items\[i\]/r.text = tagged(items[i]); r.isEnabled = !items[i].endsWith("Soon")/' $K/SettingsActivity.kt
sed -i '/getInsetsController/d' $K/SettingsActivity.kt
sed -i '/var landR by int("landR", 40)/a\    var reach by int("reach", 50)' $K/Prefs.kt
sed -i '/"Launch delay"/a\        slider(l("Дальность карточки", "Card reach"), 0, 100, p.reach, " %") { p.reach = it }' $K/SettingsActivity.kt
sed -i 's/window\.navigationBarColor = Color\.TRANSPARENT/window.navigationBarColor = Color.TRANSPARENT; if (Build.VERSION.SDK_INT >= 28) window.navigationBarDividerColor = Color.TRANSPARENT/' $K/MainActivity.kt
sed -i 's/card\.expand(rect, sourceIcon(this, a, icon)) {/card.hideView = icon; card.expand(rect, sourceIcon(this, a, icon)) {/' $K/MainActivity.kt
sed -i '/{ go("anim") }/a\                row("lock", l("Экран блокировки", "Lock screen"), l("Пароль, уведомления, отпечаток", "Passcode, notifications, fingerprint")) { startActivity(Intent(this, LockSettingsActivity::class.java)) }' $K/SettingsActivity.kt
sed -i 's/val ws = WallScreen(this)/val ws = WallCustom(this, 0, 0) { finish() }/' $K/SettingsActivity.kt
sed -i 's/wall = ws//' $K/SettingsActivity.kt
sed -i '/"chev" -> {/i\            "lock" -> { fill(); sq(cv, 5f, 10.5f, 19f, 21f, 3f); stroke(2.2f); path.moveTo(8.5f, 10.5f); path.lineTo(8.5f, 7.5f); path.cubicTo(8.5f, 2.5f, 15.5f, 2.5f, 15.5f, 7.5f); path.lineTo(15.5f, 10.5f); cv.drawPath(path, p) }' $K/Glyphs.kt
sed -i 's/menu = AppMenu(this, root)/menu = AppMenu(this, root); LockHost.attach(this, root, { home.visibility = View.INVISIBLE; drawer.visibility = View.INVISIBLE }, { home.visibility = View.VISIBLE; applyProgress() })/' $K/MainActivity.kt
sed -i 's/if (::card.isInitialized) card.collapse()/if (::card.isInitialized) card.collapse(); LockHost.resume()/' $K/MainActivity.kt
sed -i 's/unregisterReceiver(rcv)/unregisterReceiver(rcv); LockHost.detach()/' $K/MainActivity.kt
sed -i '/if (::menu.isInitialized && menu.showing) {/i\        if (LockHost.back()) return' $K/MainActivity.kt
grep -q 'SearchBar(this)' $K/MainActivity.kt
grep -q 'intArrayOf(36, 420' $K/Prefs.kt
grep -q 'endsWith("Soon")' $K/SettingsActivity.kt
grep -q 'var reach' $K/Prefs.kt
grep -q 'p.reach' $K/SettingsActivity.kt
grep -q 'navigationBarDividerColor' $K/MainActivity.kt
grep -q 'card.hideView = icon' $K/MainActivity.kt
grep -q 'LockSettingsActivity' $K/SettingsActivity.kt
grep -q 'WallCustom(this' $K/SettingsActivity.kt
grep -q '"lock" ->' $K/Glyphs.kt
grep -q 'LockHost.attach' $K/MainActivity.kt
grep -q 'LockHost.resume' $K/MainActivity.kt
grep -q 'LockHost.detach' $K/MainActivity.kt
grep -q 'LockHost.back' $K/MainActivity.kt
cp src/AndroidManifest.xml $D/
cp src/themes.xml $D/res/values/
cp src/launcher_theme.xml $D/res/values/
sed -i 's|@android:style/Theme.DeviceDefault.Wallpaper.NoTitleBar|@style/LauncherTheme|' $D/AndroidManifest.xml
sed -i '/<application/i\    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />\n    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />' $D/AndroidManifest.xml
grep -q '@style/LauncherTheme' $D/AndroidManifest.xml
grep -q 'READ_MEDIA_IMAGES' $D/AndroidManifest.xml
ICON=$(ls src/*.png 2>/dev/null | head -1)
if [ -n "$ICON" ]; then
  mkdir -p $D/res/drawable-nodpi
  convert "$ICON" -resize 432x432 $D/res/drawable-nodpi/ic_launcher.png || cp "$ICON" $D/res/drawable-nodpi/ic_launcher.png
  sed -i 's|@android:drawable/sym_def_app_icon|@drawable/ic_launcher|' $D/AndroidManifest.xml
fi
cp src/app.gradle.kts proj/app/build.gradle.kts
base64 -d src/key.txt > proj/debug.keystore
echo 'android.useAndroidX=true' > proj/gradle.properties
printf '%s\n' 'pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }' 'dependencyResolutionManagement { repositories { google(); mavenCentral() } }' 'rootProject.name = "PixelLauncher"' 'include(":app")' > proj/settings.gradle.kts
printf '%s\n' 'plugins {' '  id("com.android.application") version "8.5.2" apply false' '  id("org.jetbrains.kotlin.android") version "1.9.24" apply false' '}' > proj/build.gradle.kts
