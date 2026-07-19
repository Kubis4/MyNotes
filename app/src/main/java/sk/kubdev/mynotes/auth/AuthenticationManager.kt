package sk.kubdev.mynotes.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import sk.kubdev.mynotes.R

class AuthenticationManager(private val context: Context) {

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _authenticationRequired = MutableStateFlow(false)
    val authenticationRequired: StateFlow<Boolean> = _authenticationRequired.asStateFlow()

    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    // Grace period for background/foreground
    private var appBackgroundTime = 0L
    private val gracePeriodMillis = 10_000L // 10 seconds

    companion object {
        private const val PASSWORD_HASH_KEY = "password_hash"
        private const val PASSWORD_ENABLED_KEY = "password_enabled"
        private const val BIOMETRIC_ENABLED_KEY = "biometric_enabled"
        // Add these for language change handling
        private const val LANGUAGE_CHANGE_KEY = "language_change_in_progress"
        private const val LAST_AUTH_TIME_KEY = "last_auth_success_time"
    }

    init {
        checkAuthenticationRequired()
        // 🆕 AUTO-SETUP: If no auth is set and biometric is available, suggest it
        autoSetupAuthentication()

        // Check if we're coming from a language change
        if (isComingFromLanguageChange()) {
            _isAuthenticated.value = true
            clearLanguageChangeFlag()
        }
    }

    private fun checkAuthenticationRequired() {
        val passwordEnabled = prefs.getBoolean(PASSWORD_ENABLED_KEY, false)
        val biometricEnabled = prefs.getBoolean(BIOMETRIC_ENABLED_KEY, false)
        _authenticationRequired.value = passwordEnabled || biometricEnabled
    }

    // 🆕 SMART AUTO-SETUP
    private fun autoSetupAuthentication() {
        val hasAnyAuth = prefs.getBoolean(PASSWORD_ENABLED_KEY, false) ||
                prefs.getBoolean(BIOMETRIC_ENABLED_KEY, false)

        if (!hasAnyAuth) {
            // No authentication set up - use biometric if available, otherwise suggest password
            if (isBiometricAvailable()) {
                // Don't auto-enable, just make it the preferred option
            } else {
                // Device doesn't support biometric, password will be the only option
            }
        }
    }

    // 🆕 GET PREFERRED AUTHENTICATION METHOD
    fun getPreferredAuthMethod(): AuthMethod {
        return when {
            isBiometricEnabled() -> AuthMethod.BIOMETRIC
            isPasswordEnabled() -> AuthMethod.PASSWORD
            isBiometricAvailable() -> AuthMethod.BIOMETRIC // Preferred if available
            else -> AuthMethod.PASSWORD // Fallback
        }
    }

    // 🆕 SET AUTHENTICATION METHOD (ONLY ONE ACTIVE)
    fun setAuthenticationMethod(method: AuthMethod, password: String? = null) {
        when (method) {
            AuthMethod.BIOMETRIC -> {
                if (isBiometricAvailable()) {
                    prefs.edit()
                        .putBoolean(BIOMETRIC_ENABLED_KEY, true)
                        .putBoolean(PASSWORD_ENABLED_KEY, false)
                        .remove(PASSWORD_HASH_KEY)
                        .apply()
                } else {
                    throw IllegalStateException(context.getString(R.string.auth_biometric_not_available))
                }
            }
            AuthMethod.PASSWORD -> {
                if (password != null) {
                    val hashedPassword = hashPassword(password)
                    prefs.edit()
                        .putString(PASSWORD_HASH_KEY, hashedPassword)
                        .putBoolean(PASSWORD_ENABLED_KEY, true)
                        .putBoolean(BIOMETRIC_ENABLED_KEY, false)
                        .apply()
                } else {
                    throw IllegalArgumentException(context.getString(R.string.auth_password_required_for_setup))
                }
            }
            AuthMethod.NONE -> {
                prefs.edit()
                    .putBoolean(BIOMETRIC_ENABLED_KEY, false)
                    .putBoolean(PASSWORD_ENABLED_KEY, false)
                    .remove(PASSWORD_HASH_KEY)
                    .apply()
            }
        }
        checkAuthenticationRequired()
    }

    // 🆕 VERIFY CURRENT AUTHENTICATION FOR SECURITY CHANGES
    fun verifyCurrentAuthentication(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        password: String? = null
    ) {
        when {
            isBiometricEnabled() -> {
                // Verify with biometric
                authenticateWithBiometric(
                    activity = activity,
                    onSuccess = onSuccess,
                    onError = onError,
                    onFailed = { onError(context.getString(R.string.auth_biometric_verification_failed)) }
                )
            }
            isPasswordEnabled() -> {
                // Verify with password
                if (password != null) {
                    if (verifyPassword(password)) {
                        onSuccess()
                    } else {
                        onError(context.getString(R.string.auth_invalid_password))
                    }
                } else {
                    onError(context.getString(R.string.auth_password_required))
                }
            }
            else -> {
                // No authentication set up
                onSuccess()
            }
        }
    }

    fun onAppGoingToBackground() {
        if (isAuthenticationRequired()) {
            appBackgroundTime = System.currentTimeMillis()
        }
    }

    fun onAppComingFromBackground(): Boolean {
        if (!isAuthenticationRequired()) return true

        if (appBackgroundTime == 0L) {
            return false
        }

        val currentTime = System.currentTimeMillis()
        val timeInBackground = currentTime - appBackgroundTime

        return if (timeInBackground <= gracePeriodMillis) {
            true
        } else {
            logout()
            false
        }
    }

    fun logout() {
        _isAuthenticated.value = false
        appBackgroundTime = 0L
    }

    fun isAuthenticationRequired(): Boolean {
        return _authenticationRequired.value
    }

    fun setPassword(password: String) {
        setAuthenticationMethod(AuthMethod.PASSWORD, password)
    }

    fun verifyPassword(password: String): Boolean {
        val storedHash = prefs.getString(PASSWORD_HASH_KEY, null) ?: return false
        val inputHash = hashPassword(password)
        val isValid = storedHash == inputHash
        if (isValid) {
            _isAuthenticated.value = true
            appBackgroundTime = 0L
            // Store last successful auth time
            storeLastAuthTime()
        }
        return isValid
    }

    fun removePassword() {
        setAuthenticationMethod(AuthMethod.NONE)
    }

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(password.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        if (enabled) {
            setAuthenticationMethod(AuthMethod.BIOMETRIC)
        } else {
            setAuthenticationMethod(AuthMethod.NONE)
        }
    }

    fun authenticateWithBiometric(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit
    ) {
        if (!isBiometricAvailable()) {
            onError(context.getString(R.string.auth_biometric_not_available))
            return
        }

        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                _isAuthenticated.value = true
                appBackgroundTime = 0L
                // Store last successful auth time
                storeLastAuthTime()
                onSuccess()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailed()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.auth_biometric_prompt_title))
            .setSubtitle(context.getString(R.string.auth_biometric_prompt_subtitle))
            .setNegativeButtonText(context.getString(R.string.action_cancel))
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    fun isPasswordEnabled(): Boolean {
        return prefs.getBoolean(PASSWORD_ENABLED_KEY, false)
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(BIOMETRIC_ENABLED_KEY, false)
    }

    // ============ LANGUAGE CHANGE HANDLING METHODS ============

    // Call this before changing language
    fun prepareForLanguageChange() {
        if (_isAuthenticated.value) {
            prefs.edit()
                .putBoolean(LANGUAGE_CHANGE_KEY, true)
                .putLong(LAST_AUTH_TIME_KEY, System.currentTimeMillis())
                .apply()
        }
    }

    // Check if coming from language change
    private fun isComingFromLanguageChange(): Boolean {
        val isLanguageChange = prefs.getBoolean(LANGUAGE_CHANGE_KEY, false)
        if (isLanguageChange) {
            val lastAuthTime = prefs.getLong(LAST_AUTH_TIME_KEY, 0)
            val timeSinceAuth = System.currentTimeMillis() - lastAuthTime

            // If language change was marked and it's been less than 10 seconds
            return timeSinceAuth < 10000
        }
        return false
    }

    // Clear language change flag
    private fun clearLanguageChangeFlag() {
        prefs.edit()
            .remove(LANGUAGE_CHANGE_KEY)
            .apply()
    }

    // Store last successful authentication time
    private fun storeLastAuthTime() {
        prefs.edit()
            .putLong(LAST_AUTH_TIME_KEY, System.currentTimeMillis())
            .apply()
    }
}

// 🆕 AUTHENTICATION METHOD ENUM
enum class AuthMethod {
    BIOMETRIC,
    PASSWORD,
    NONE
}
