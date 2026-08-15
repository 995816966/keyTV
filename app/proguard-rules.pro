# 保留 ExoPlayer / Media3 需要的混淆规则（release 构建时用）
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.**

# DataStore
-keep class androidx.datastore.** { *; }
