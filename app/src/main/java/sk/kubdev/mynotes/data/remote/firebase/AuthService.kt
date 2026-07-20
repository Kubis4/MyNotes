package sk.kubdev.mynotes.data.remote.firebase

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import sk.kubdev.mynotes.data.remote.models.UserProfile
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
                photoUrl = user.photoUrl?.toString()
            )

            Log.d(TAG, "Google Sign-In successful")
            Result.success(user)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google Sign-In failed", e)
            Result.failure(Exception("Google Sign-In failed: ${e.message}"))
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
        usersCollection.document(userId).set(userProfile).await()
    }

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
