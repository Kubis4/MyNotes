// Top-level build file where you can add configuration options common to all sub-projects/modules.
import java.io.File

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.google.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    id("com.google.gms.google-services") version "4.4.3" apply false
    id("com.google.dagger.hilt.android") version "2.48" apply false
}

tasks.register("printDebugKeystoreSHA") {
    doLast {
        val home = System.getProperty("user.home")
        val keystoreFile = File("$home/.android/debug.keystore")

        if (!keystoreFile.exists()) {
            println("⚠️ debug.keystore not found at: ${keystoreFile.absolutePath}")
            return@doLast
        }

        val command = listOf(
            "keytool",
            "-list",
            "-v",
            "-keystore", keystoreFile.absolutePath,
            "-alias", "androiddebugkey",
            "-storepass", "android",
            "-keypass", "android"
        )

        println("Running: ${command.joinToString(" ")}")

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        println(output)
    }
}
