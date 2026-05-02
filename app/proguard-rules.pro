# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Moshi adapters
-keep class com.soll.data.api.model.** { *; }
-keepclassmembers class com.soll.data.api.model.** { *; }

# Keep Room entities
-keep class com.soll.data.local.entity.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# ONNX Runtime (Utrobin HF VITS)
-keep class ai.onnxruntime.** { *; }

# Moshi
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
