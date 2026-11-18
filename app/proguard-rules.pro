# Release builds currently ship with minify off. These rules are ready
# if resource shrinking is turned on later.
-keep class com.google.firebase.** { *; }
-keep class com.google.mlkit.** { *; }
-keep class org.tensorflow.lite.** { *; }
-keep class androidx.room.** { *; }
-keepclassmembers class bd.robin.hisabkhata.logic.** { *; }
-dontwarn org.tensorflow.**
