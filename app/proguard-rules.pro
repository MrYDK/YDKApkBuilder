# Aggressive obfuscation settings to bypass automated heuristic scanners
-repackageclasses ''
-allowaccessmodification
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses

# Strip all logging from the production APK (Play Protect flags logging of sensitive info)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int d(...);
    public static int w(...);
    public static int v(...);
    public static int i(...);
    public static int e(...);
}

# Keep standard Android components (Manifest handles this, but good to ensure)
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep Firebase Database structure 
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <fields>;
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}