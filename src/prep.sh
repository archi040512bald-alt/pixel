set -e
D=proj/app/src/main
K=$D/java/com/example/pixellauncher
mkdir -p $K $D/res/values
cp src/*.kt $K/
sed -i 's/setProgress(/animateTo(/g' $K/*.kt
cp src/AndroidManifest.xml $D/
cp src/themes.xml $D/res/values/
cp src/app.gradle.kts proj/app/build.gradle.kts
if [ ! -f src/key.txt ]; then
  keytool -genkeypair -keystore k.p12 -storetype PKCS12 -alias androiddebugkey -keyalg EC -groupname secp256r1 -validity 36500 -storepass android -keypass android -dname "CN=Pixel Launcher"
  base64 -w0 k.p12 > src/key.txt
  git config user.name ci
  git config user.email ci@example.com
  git add src/key.txt
  git commit -m "Add signing key"
  git push
fi
base64 -d src/key.txt > proj/debug.keystore
sed -i 's|showSettings(this@MainActivity.*|startActivity(Intent(this@MainActivity, SettingsActivity::class.java))|' $K/MainActivity.kt
sed -i 's|registerReceiver(rcv, f)|registerReceiver(rcv, f); prefs.listen { loadApps(); applyGrid() }|' $K/MainActivity.kt
sed -i 's|ri.loadIcon(pm)))|IconPacks.icon(c, ri.activityInfo.packageName, ri.activityInfo.name) ?: ri.loadIcon(pm)))|' $K/Data.kt
grep -q SettingsActivity $K/MainActivity.kt
grep -q IconPacks $K/Data.kt
echo 'android.useAndroidX=true' > proj/gradle.properties
printf '%s\n' 'pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }' 'dependencyResolutionManagement { repositories { google(); mavenCentral() } }' 'rootProject.name = "PixelLauncher"' 'include(":app")' > proj/settings.gradle.kts
printf '%s\n' 'plugins {' '  id("com.android.application") version "8.5.2" apply false' '  id("org.jetbrains.kotlin.android") version "1.9.24" apply false' '}' > proj/build.gradle.kts
