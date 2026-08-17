# Room Database Rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Data Models & Entities (Prevent JSON serialization field renaming)
-keepclassmembers class com.yemen.watersurvey.domain.model.** { *; }
-keepclassmembers class com.yemen.watersurvey.data.entity.** { *; }
