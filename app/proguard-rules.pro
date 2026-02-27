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

# TensorFlow Lite required rules (TFLite relies on JNI and specific class names)
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-keepclassmembers class org.tensorflow.lite.** { *; }
-keepclassmembers class org.tensorflow.lite.support.** { *; }

# Fix TensorFlow Lite missing AutoValue classes
-dontwarn com.google.auto.value.**
-keep class com.google.auto.value.** { *; }

# Keep Dagger/Hilt classes
-keep class dagger.** { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep class javax.inject.** { *; }
-keepclassmembers,allowobfuscation class * {
    @javax.inject.* *;
    @dagger.* *;
    @dagger.hilt.* *;
}