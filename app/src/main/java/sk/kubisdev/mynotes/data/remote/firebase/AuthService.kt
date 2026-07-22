package sk.kubisdev.mynotes.data.remote.firebase

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import sk.kubisdev.mynotes.data.remote.models.UserProfile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthService @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    private val usersCollection = firestore.collection("users")

    companion object {
        private const val TAG = "AuthService"
        // You'll need to get this from your Firebase project settings
        // Go to Project Settings -> General -> Web API Key
        private const val WEB_CLIENT_ID = "946751285218-hgm21vckspia4mf9a3bphjlqv32toib7.apps.googleusercontent.com" // Replace with actual ID
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser
    fun getCurrentUserId(): String? = auth.currentUser?.uid
    fun getCurrentUserEmail(): String? = auth.currentUser?.email
    fun isUserSignedIn(): Boolean = auth.currentUser != null

    // Auth state flow
    fun getAuthStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    // Google Sign-In
    suspend fun signInWithGoogle(context: Context): Result<FirebaseUser> {
        return try {
            Log.d(TAG, "Starting Google Sign-In")

            // Credential Manager routes Google sign-in through a Play Services broker
            // process. On devices/emulators without a working, up-to-date Play
            // Services install, that broker throws a SecurityException instead of a
            // normal failure ("Unknown calling package name 'com.google.android.gms'",
            // tag GoogleApiManager) - checking availability up front turns that into a
            // clear, actionable message instead of a raw broker crash.
            val playServicesStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
            if (playServicesStatus != ConnectionResult.SUCCESS) {
                Log.e(TAG, "Google Play Services unavailable (status $playServicesStatus)")
                return Result.failure(
                    Exception(
                        "Google Play Services isn't available or is out of date on this " +
                            "device, so Google Sign-In can't run. On an emulator, use an " +
                            "AVD image with Google Play (not just Google APIs) and update " +
                            "Play Services from the Play Store inside it; on a real device, " +
                            "update Google Play Services."
                    )
                )
            }

            val credentialManager = CredentialManager.create(context)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(WEB_CLIENT_ID)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context,
            )

            val credential = result.credential
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val googleIdToken = googleIdTokenCredential.idToken

            Log.d(TAG, "Got Google ID token, signing in with Firebase")

            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val user = authResult.user ?: throw Exception("Firebase sign in failed")

            // Save user profile to Firestore
            saveUserProfile(
                userId = user.uid,
                email = user.email ?: "",
                displayName = user.displayName ?: "",
                photoUrl = (user.photoUrl?.toString()
                    ?: googleIdTokenCredential.profilePictureUri?.toString())?.let(::highResPhotoUrl)
            )

            Log.d(TAG, "Google Sign-In successful")
            Result.success(user)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google Sign-In failed", e)
            Result.failure(Exception("Google Sign-In failed: ${e.message}"))
        } catch (e: SecurityException) {
            // Defense in depth: on some broken Play Services installs the broker
            // rejects the call with a bare SecurityException instead of routing it
            // through GetCredentialException, even after the availability check above.
            Log.e(TAG, "Google Play Services broker rejected the sign-in request", e)
            Result.failure(
                Exception("Google Play Services rejected the sign-in request. Update Google Play Services, or use a Play Store-enabled emulator/device.")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Sign-In error", e)
            Result.failure(e)
        }
    }

    // Sign up with email and password (keep for testing)
    suspend fun signUp(email: String, password: String, displayName: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw Exception("User creation failed")

            // Save user profile to Firestore
            saveUserProfile(user.uid, email, displayName)

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Sign in with email and password (keep for testing)
    suspend fun signIn(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw Exception("Sign in failed")
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Sign out
    fun signOut() {
        auth.signOut()
    }

    // Save user profile
    private suspend fun saveUserProfile(
        userId: String,
        email: String,
        displayName: String,
        photoUrl: String? = null
    ) {
        val userProfile = UserProfile(
            userId = userId,
            email = email,
            displayName = displayName,
            photoUrl = photoUrl
        )
        // Merge so a later sign-in never wipes fields we didn't compute here
        // (and so profiles written before photoUrl existed just gain the field).
        usersCollection.document(userId).set(userProfile, SetOptions.merge()).await()
    }

    /**
     * Refreshes the signed-in user's Firestore profile from their Google account.
     *
     * Collaborators read each other's avatar from this document, so accounts that
     * signed in before the photo was captured - or that changed their Google picture
     * since - would otherwise keep showing the fallback person glyph forever. Safe to
     * call on every app start; it's a single merge write and silently no-ops offline.
     */
    suspend fun syncCurrentUserProfile() {
        val user = auth.currentUser ?: return
        try {
            // Firebase caches the profile from the last token refresh; reload() pulls
            // the current Google display name/photo before we mirror it to Firestore.
            runCatching { user.reload().await() }

            val photo = user.photoUrl?.toString()
                ?: user.providerData.firstNotNullOfOrNull { it.photoUrl?.toString() }

            saveUserProfile(
                userId = user.uid,
                email = user.email ?: "",
                displayName = user.displayName ?: "",
                photoUrl = photo?.let(::highResPhotoUrl)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not sync user profile", e)
        }
    }

    // Google avatar URLs carry their size in the trailing "=s96-c" token. The default
    // is too small for anything but a thumbnail, so ask for a crisper variant.
    private fun highResPhotoUrl(url: String): String =
        Regex("=s\\d+(-c)?$").replace(url, "=s192-c")

    // Get user profile
    suspend fun getUserProfile(userId: String): UserProfile? {
        return try {
            val snapshot = usersCollection.document(userId).get().await()
            snapshot.toObject(UserProfile::class.java)
        } catch (e: Exception) {
            null
        }
    }

    // Update user profile
    suspend fun updateUserProfile(displayName: String): Boolean {
        return try {
            val userId = getCurrentUserId() ?: return false
            usersCollection.document(userId)
                .update("displayName", displayName)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }
}
