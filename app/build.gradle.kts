import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.kotlin.serialization)
    id("com.google.gms.google-services") // Remove the duplicate android.application plugin
    id("com.google.dagger.hilt.android")
    id("com.google.firebase.crashlytics")
}

// Release signing credentials live in a git-ignored keystore.properties file
// (see keystore.properties.example) - never commit a keystore password. Debug
// builds and CI checkouts without that file still compile fine; only an actual
// `assembleRelease`/`bundleRelease` without it will fail signing, on purpose.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}

android {
    namespace = "sk.kubdev.mynotes"
    compileSdk = 35

    defaultConfig {
        applicationId = "sk.kubdev.mynotes"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += (
                "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
                )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // The app ships partial / AI-assisted translations with an English fallback,
        // so missing translations should be reported but must not fail the build.
        warning += "MissingTranslation"
        // Baseline of pre-existing, non-blocking lint warnings (outdated dependency
        // suggestions, unused resources, etc.). Recorded so the build stays clean;
        // burn these down over time with proper testing rather than blind changes.
        baseline = file("lint-baseline.xml")
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.google.gson)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    // Firebase BOM - this manages all Firebase library versions
    implementation(platform("com.google.firebase:firebase-bom:33.6.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth") // Add for authentication
    implementation("com.google.firebase:firebase-firestore") // Add for Firestore
    implementation("com.google.firebase:firebase-crashlytics")
    // App Check: only genuine Play-installed builds get Firestore/Auth access once
    // enforcement is turned on in the Firebase console (blocks scripted abuse with
    // the API key extracted from the APK). Debug builds use the debug provider.
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    // implementation (not debugImplementation) so release still compiles; the
    // BuildConfig.DEBUG branch selecting it is stripped from release by R8.
    implementation("com.google.firebase:firebase-appcheck-debug")

    // Coroutines support for Firebase
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Settings dependencies
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.biometric:biometric:1.1.0")
    // Must match the lifecycle version in libs.versions.toml: mixing 2.7 and 2.8
    // artifacts crashed release builds ("LocalLifecycleOwner not present" - 2.8
    // moved that CompositionLocal into lifecycle-runtime-compose).
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("io.coil-kt:coil-compose:2.4.0")

    // Google Drive API dependencies
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("com.google.api-client:google-api-client-android:1.31.5")
    implementation("com.google.apis:google-api-services-drive:v3-rev20220508-1.32.1")
    implementation("com.google.http-client:google-http-client-gson:1.41.8")
    implementation("com.google.http-client:google-http-client-android:1.41.8")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.credentials:credentials:1.2.2")
    implementation("androidx.credentials:credentials-play-services-auth:1.2.2")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.0")

    // WorkManager for automatic backups
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Add Hilt dependencies
    implementation("com.google.dagger:hilt-android:2.48")
    ksp("com.google.dagger:hilt-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    ksp(libs.androidx.room.compiler)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
