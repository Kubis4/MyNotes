package sk.kubdev.mynotes

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp
import sk.kubdev.mynotes.collab.InviteCheckWorker

@HiltAndroidApp
class MyNotesApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // App Check must be installed before any other Firebase API is touched, so
        // every Firestore/Auth request carries an attestation token. Enforcement is
        // toggled server-side in the Firebase console - until then this only
        // reports metrics, so it's safe to ship ahead of flipping the switch.
        FirebaseApp.initializeApp(this)
        val appCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
            // Debug builds aren't Play-installed; the debug provider issues tokens
            // for devices whose debug secret is registered in the console.
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        } else {
            appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        }

        // Background poll for collaboration invites, so invitations produce a system
        // notification even when the app is closed (no-ops while signed out).
        InviteCheckWorker.schedule(this)
    }
}
