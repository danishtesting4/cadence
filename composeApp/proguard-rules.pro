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
