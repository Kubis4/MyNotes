# Project-specific R8/ProGuard rules.

# Keep file/line info so Crashlytics stack traces stay symbolicated (paired with
# the mapping file uploaded by the Crashlytics Gradle plugin).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Repackage all obfuscated classes into a single (empty-named) root package. This
# is the "Repackage classes" optimisation Play Console flags: it flattens the
# package hierarchy so R8 can rename more aggressively, improving obfuscation and
# shrinking the DEX string pool. Safe here because everything reflected over is
# explicitly -kept below.
-repackageclasses ''

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
-keep class sk.kubisdev.mynotes.data.remote.models.** { *; }

# The Room entities ARE the backup format: Gson serialises Note/Category straight
# into the Drive + local backup JSON, using reflection over field names (and
# enum name() for NoteType). Without this keep, R8 renames them - Note.title
# becomes "b", NoteType.TEXT becomes "a" - so the JSON keys depend on that
# build's obfuscation mapping. Adding a field then shifts every later letter,
# which is why a backup written by one release silently restored as blank notes
# in the next (Gson finds no matching keys and falls back to defaults, with no
# exception to log). Keeping these makes the backup format stable and readable.
-keep class sk.kubisdev.mynotes.data.remote.local.entities.** { *; }

# Google Drive backup payloads are (de)serialized with Gson reflection.
-keep class sk.kubisdev.mynotes.backup.BackupData { *; }
-keep class sk.kubisdev.mynotes.backup.BackupFile { *; }
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

# firebase-analytics's internal measurement code references AdvertisingIdClient,
# but play-services-ads-identifier is deliberately excluded from this build (see
# build.gradle.kts) so the SDK can't read the ad ID at all - the app declares "No"
# for advertising ID use. Analytics already handles this class being absent at
# runtime (catches ClassNotFoundException), R8 just needs to be told not to fail
# the build over the now-unresolvable reference.
-dontwarn com.google.android.gms.ads.identifier.AdvertisingIdClient
-dontwarn com.google.android.gms.ads.identifier.AdvertisingIdClient$Info
