-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keep class com.dartsapp.** { *; }

# Metro Darts: no reflection or serialisation in the app, so the defaults are enough.
# Keep line numbers for readable crash reports in Play Console.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
