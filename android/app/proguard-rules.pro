# Keep Flutter/Tuya safe defaults (we can tighten later)
-keep class io.flutter.** { *; }
-dontwarn io.flutter.**

# Tuya/Thingclips: keep everything (safe for now)
-keep class com.thingclips.** { *; }
-dontwarn com.thingclips.**
-keep class com.tuya.** { *; }
-dontwarn com.tuya.**
