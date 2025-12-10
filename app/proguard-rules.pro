# ═══════════════════════════════════════════════════════════════════════════
# ProGuard rules for Hospice Inventory
# ═══════════════════════════════════════════════════════════════════════════

# ─── GENERAL ───────────────────────────────────────────────────────────────

# Keep line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ─── KOTLIN ────────────────────────────────────────────────────────────────

-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keepclassmembers class kotlinx.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ─── ROOM ──────────────────────────────────────────────────────────────────

-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ─── RETROFIT / OKHTTP ─────────────────────────────────────────────────────

-keepattributes Signature
-keepattributes Exceptions
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# ─── FIREBASE ──────────────────────────────────────────────────────────────

-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# ─── GOOGLE AI (GEMINI) ────────────────────────────────────────────────────

-keep class com.google.ai.client.generativeai.** { *; }

# ─── HILT ──────────────────────────────────────────────────────────────────

-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ─── MLKIT ─────────────────────────────────────────────────────────────────

-keep class com.google.mlkit.** { *; }

# ─── APP MODELS ────────────────────────────────────────────────────────────

# Keep all entity classes
-keep class org.incammino.hospiceinventory.data.local.entity.** { *; }
-keep class org.incammino.hospiceinventory.domain.model.** { *; }

# Keep enums
-keepclassmembers enum org.incammino.hospiceinventory.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
