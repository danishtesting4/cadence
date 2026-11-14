# ProGuard/R8 rules for Cadence.

# kotlinx.serialization: keep generated serializers for our persisted models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class app.cadence.** {
    *** Companion;
}
-keepclasseswithmembers class app.cadence.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.cadence.**$$serializer { *; }

# Keep model property names used as JSON keys.
-keepclassmembers class app.cadence.** {
    <fields>;
}

# Media3/ExoPlayer
-dontwarn androidx.media3.**

# Skiko/Compose native bindings
-dontwarn org.jetbrains.skia.**
-keep class org.jetbrains.skia.** { *; }

# innertubex / Ktor / OkHttp / QuackJS (JavaScript engine used for signature
# deciphering). Loaded reflectively and evaluated dynamically, so keep them.
-dontwarn com.metrolist.innertubex.**
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.openjdk.nashorn.**
-dontwarn com.eclipsesource.**

-keep class com.metrolist.innertubex.** { *; }

# Ktor engines/clients are loaded reflectively; keep the engine entry points.
-keep class io.ktor.client.engine.okhttp.** { *; }
-keep class io.ktor.client.engine.cio.** { *; }
-dontwarn io.ktor.server.**

# Coroutines internals are referenced reflectively by some libraries.
-dontwarn kotlinx.coroutines.**
