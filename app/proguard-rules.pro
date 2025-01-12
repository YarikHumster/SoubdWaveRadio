# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable, Signature, *Annotation*
-dontwarn com.yandex.metrica.ModulesFacade

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# Preserve the core classes - because they need to be de-/serialized with GSON
-keep public class com.yaros.RadioUrl.core.**  { *; }
-keep public class com.yaros.RadioUrl.PlayerService  { *; }
-keep public class com.yaros.RadioUrl.ui.search.RadioBrowserResult  { *; }
-keep class com.yaros.RadioUrl.ui.FavoriteSong.** { *; }
-keep class com.google.firebase.** { *; }
-keep class com.yaros.RadioUrl.model.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class com.google.gson.stream.** { *; }
-keep class com.yaros.RadioUrl.model.** { *; }
-dontwarn com.yandex.metrica.IIdentifierCallback
-dontwarn com.yandex.metrica.YandexMetrica
-dontwarn com.yandex.metrica.p
