# Own code: keep names (stack traces in bug reports stay readable); unused code is still removed
-keep,allowshrinking,allowoptimization class de.letzgo.launcher.** { *; }

# Classes Launcher3 looks up by name from resources (settings fragment, filtered components, ...)
-keep class de.letzgo.launcher.settings.LetzgoSettingsFragment { <init>(); }

# Reflection on hidden platform APIs (SystemPanels, AppLockCompat)
-dontwarn android.**
-dontwarn com.android.internal.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
-dontwarn kotlinx.**

# protobuf-lite reads message fields by name via reflection
-keep class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
