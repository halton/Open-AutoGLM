# ProGuard rules for AutoGLM Android Agent

# Keep Room entities and DAOs
-keep class com.openautoglm.agent.data.entities.** { *; }
-keep class com.openautoglm.agent.data.dao.** { *; }

# Keep model classes for JSON serialization
-keep class com.openautoglm.agent.model.** { *; }

# Keep Gson TypeToken
-keepattributes Signature
-keepattributes *Annotation*

# Retrofit
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
