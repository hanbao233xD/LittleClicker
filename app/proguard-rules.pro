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
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep enum names used by Gson serialization/deserialization.
-keep enum com.example.littleclicker.autoclick.AutoClickActionType { *; }
-keep enum com.example.littleclicker.autoclick.AutoClickRunMode { *; }

# Keep JSON field names for local profile/state persistence.
-keepclassmembers class com.example.littleclicker.autoclick.AutoClickPoint { <fields>; }
-keepclassmembers class com.example.littleclicker.autoclick.AutoClickProfile { <fields>; }
-keepclassmembers class com.example.littleclicker.autoclick.AutoClickRepository$AutoClickStorageState { <fields>; }
-keepclassmembers class com.example.littleclicker.autoclick.AutoClickRepository$AutoClickPointPayload { <fields>; }
-keepclassmembers class com.example.littleclicker.autoclick.AutoClickRepository$AutoClickProfilePayload { <fields>; }
