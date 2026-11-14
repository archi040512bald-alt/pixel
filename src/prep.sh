set -e
D=proj/app/src/main
K=$D/java/com/example/pixellauncher
mkdir -p $K $D/res/values
cp src/*.kt $K/
cp src/AndroidManifest.xml $D/
cp src/themes.xml $D/res/values/
cp src/app.gradle.kts proj/app/build.gradle.kts
base64 -d src/key.txt > proj/debug.keystore
echo 'android.useAndroidX=true' > proj/gradle.properties
printf '%s\n' 'pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }' 'dependencyResolutionManagement { repositories { google(); mavenCentral() } }' 'rootProject.name = "PixelLauncher"' 'include(":app")' > proj/settings.gradle.kts
printf '%s\n' 'plugins {' '  id("com.android.application") version "8.5.2" apply false' '  id("org.jetbrains.kotlin.android") version "1.9.24" apply false' '}' > proj/build.gradle.kts
