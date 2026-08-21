# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Moshi adapters
-keep class com.soll.data.api.model.** { *; }
-keepclassmembers class com.soll.data.api.model.** { *; }
-keep class com.soll.data.api.** { *; }

# Daily intelligence snapshots are currently serialized through Moshi's
# reflective adapter. Keep these domain DTOs concrete in optimized builds;
# otherwise R8 can merge/abstract them and Moshi fails during app startup.
-keep class com.soll.domain.soll.** { *; }

# Keep Room entities
-keep class com.soll.data.local.entity.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes AnnotationDefault
-keep interface com.soll.data.api.** { *; }
-keep,allowoptimization,allowshrinking,allowobfuscation class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>
-keep,allowoptimization,allowshrinking,allowobfuscation class retrofit2.Response

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
