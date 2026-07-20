# Project-specific R8/ProGuard rules.

# Keep file/line info so Crashlytics stack traces stay symbolicated (paired with
# the mapping file uploaded by the Crashlytics Gradle plugin).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Strip verbose/debug/info logging from release builds. Works because the build
# uses proguard-android-optimize.txt (optimization must be on for
# -assumenosideeffects to take effect).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# lifecycle-runtime-compose 2.8.x + Compose UI 1.6.x: LocalLifecycleOwner moved
# into the lifecycle artifact and delegates to the compose-ui one via reflection,
# which R8 strips - crashing release builds with "CompositionLocal
# LocalLifecycleOwner not present". Official workaround from the androidx
# lifecycle 2.8.0 release notes:
-if public class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt {
    public static *** getLocalLifecycleOwner();
}
-keep public class androidx.lifecycle.compose.LocalLifecycleOwnerKt {
    public static *** getLocalLifecycleOwner();
}
-if public class androidx.lifecycle.compose.LocalLifecycleOwnerKt {
    public static *** getLocalLifecycleOwner();
}
-keep public class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt {
    public static *** getLocalLifecycleOwner();
}

# Firestore deserializes these via reflection (DocumentSnapshot.toObject), which
# needs the no-arg constructor and unobfuscated field names.
-keep class sk.kubdev.mynotes.data.remote.models.** { *; }

# Google Drive backup payloads are (de)serialized with Gson reflection.
-keep class sk.kubdev.mynotes.backup.BackupData { *; }
-keep class sk.kubdev.mynotes.backup.BackupFile { *; }
-keepattributes Signature
-keepattributes *Annotation*

# google-api-client / google-http-client (Drive REST) use reflection over their
# own model classes and emit harmless warnings about optional dependencies.
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.** { *; }
-dontwarn com.google.api.client.**
-dontwarn com.google.http.client.**
-dontwarn org.apache.http.**
-dontwarn com.google.appengine.**
-dontwarn javax.naming.**
