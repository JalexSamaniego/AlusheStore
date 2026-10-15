# AlusheStore ProGuard rules

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# Google Drive / API Client
-keep class com.google.api.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn com.google.api.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
