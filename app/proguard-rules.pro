# proguard-rules.pro
# VISIONOID MAG PLOTTER - ProGuard設定

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Gson (Room用)
-keepattributes Signature
-keepattributes *Annotation*



