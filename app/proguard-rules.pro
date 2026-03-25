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

# Gson reflection compatibility for local profile/state persistence.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Keep enums used in JSON values.
-keep enum com.example.littleclicker.autoclick.AutoClickActionType { *; }
-keep enum com.example.littleclicker.autoclick.AutoClickRunMode { *; }

# Keep full model/payload classes to avoid R8 renaming/removing members that Gson relies on.
-keep class com.example.littleclicker.autoclick.AutoClickPoint { *; }
-keep class com.example.littleclicker.autoclick.AutoClickProfile { *; }
-keep class com.example.littleclicker.autoclick.AutoClickRepository$AutoClickStorageState { *; }
-keep class com.example.littleclicker.autoclick.AutoClickRepository$AutoClickPointPayload { *; }
-keep class com.example.littleclicker.autoclick.AutoClickRepository$AutoClickProfilePayload { *; }
