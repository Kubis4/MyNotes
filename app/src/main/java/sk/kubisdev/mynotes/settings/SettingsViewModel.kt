package sk.kubisdev.mynotes.settings

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sk.kubisdev.mynotes.auth.AuthMethod
import sk.kubisdev.mynotes.auth.AuthenticationManager
import sk.kubisdev.mynotes.settings.model.*

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val authManager: AuthenticationManager,
    private val context: Context
) : ViewModel() {

    private val _settings = MutableStateFlow(SettingsData())
    val settings: StateFlow<SettingsData> = _settings.asStateFlow()

    private val _showPasswordDialog = MutableStateFlow(false)
    val showPasswordDialog: StateFlow<Boolean> = _showPasswordDialog.asStateFlow()

    private val _showLanguageRestartDialog = MutableStateFlow(false)
    val showLanguageRestartDialog: StateFlow<Boolean> = _showLanguageRestartDialog.asStateFlow()

    // 🆕 SECURE AUTHENTICATION CHANGE DIALOGS
    private val _showAuthChangeDialog = MutableStateFlow<AuthChangeType?>(null)
    val showAuthChangeDialog: StateFlow<AuthChangeType?> = _showAuthChangeDialog.asStateFlow()

    private val _authChangeError = MutableStateFlow("")
    val authChangeError: StateFlow<String> = _authChangeError.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settingsFlow.collect { settingsData ->
                _settings.value = settingsData.copy(
                    biometricEnabled = authManager.isBiometricEnabled(),
                    passwordEnabled = authManager.isPasswordEnabled()
                )
            }
        }
    }

    fun updateTheme(theme: AppTheme) {
        viewModelScope.launch {
            repository.updateTheme(theme)
        }
    }

    fun updateColorScheme(colorScheme: AppColorScheme) {
        viewModelScope.launch {
            repository.updateColorScheme(colorScheme)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            repository.setOnboardingCompleted(true)
        }
    }

    fun updateGradientHeadersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateGradientHeadersEnabled(enabled)
        }
    }

    // 🔒 SECURE BIOMETRIC TOGGLE WITH VERIFICATION
    fun updateBiometricEnabled(enabled: Boolean) {
        if (enabled) {
            // Enabling biometric
            if (!authManager.isBiometricAvailable()) {
                _authChangeError.value = "Biometric authentication is not available on this device"
                return
            }

            // Check if any auth is currently active
            if (authManager.isPasswordEnabled()) {
                // Switching from password to biometric - require verification
                _showAuthChangeDialog.value = AuthChangeType.SWITCH_TO_BIOMETRIC
            } else {
                // No auth currently active - can enable directly
                authManager.setAuthenticationMethod(AuthMethod.BIOMETRIC)
                updateRepositoryAuth()
            }
        } else {
            // Disabling biometric - require verification
            _showAuthChangeDialog.value = AuthChangeType.DISABLE_BIOMETRIC
        }
    }

    // 🔒 SECURE PASSWORD TOGGLE WITH VERIFICATION
    fun updatePasswordEnabled(enabled: Boolean) {
        if (enabled) {
            // Check if any auth is currently active
            if (authManager.isBiometricEnabled()) {
                // Switching from biometric to password - require verification
                _showAuthChangeDialog.value = AuthChangeType.SWITCH_TO_PASSWORD
            } else {
                // No auth currently active - show password setup
                _showPasswordDialog.value = true
            }
        } else {
            // Disabling password - require verification
            _showAuthChangeDialog.value = AuthChangeType.DISABLE_PASSWORD
        }
    }

    // 🆕 VERIFY AND PERFORM AUTH CHANGE
    fun verifyAndPerformAuthChange(changeType: AuthChangeType, password: String? = null) {
        val activity = context as? FragmentActivity ?: return

        when (changeType) {
            AuthChangeType.SWITCH_TO_BIOMETRIC -> {
                // Verify current password, then switch to biometric
                authManager.verifyCurrentAuthentication(
                    activity = activity,
                    onSuccess = {
                        authManager.setAuthenticationMethod(AuthMethod.BIOMETRIC)
                        updateRepositoryAuth()
                        _showAuthChangeDialog.value = null
                        _authChangeError.value = ""
                    },
                    onError = { error -> _authChangeError.value = error },
                    password = password
                )
            }

            AuthChangeType.SWITCH_TO_PASSWORD -> {
                // Verify current biometric, then show password setup
                authManager.verifyCurrentAuthentication(
                    activity = activity,
                    onSuccess = {
                        _showAuthChangeDialog.value = null
                        _showPasswordDialog.value = true
                        _authChangeError.value = ""
                    },
                    onError = { error -> _authChangeError.value = error }
                )
            }

            AuthChangeType.DISABLE_BIOMETRIC -> {
                // Verify current biometric, then disable
                authManager.verifyCurrentAuthentication(
                    activity = activity,
                    onSuccess = {
                        authManager.setAuthenticationMethod(AuthMethod.NONE)
                        updateRepositoryAuth()
                        _showAuthChangeDialog.value = null
                        _authChangeError.value = ""
                    },
                    onError = { error -> _authChangeError.value = error }
                )
            }

            AuthChangeType.DISABLE_PASSWORD -> {
                // Verify current password, then disable
                authManager.verifyCurrentAuthentication(
                    activity = activity,
                    onSuccess = {
                        authManager.setAuthenticationMethod(AuthMethod.NONE)
                        updateRepositoryAuth()
                        _showAuthChangeDialog.value = null
                        _authChangeError.value = ""
                    },
                    onError = { error -> _authChangeError.value = error },
                    password = password
                )
            }
        }
    }

    // 🆕 UPDATE REPOSITORY AFTER AUTH CHANGES
    private fun updateRepositoryAuth() {
        viewModelScope.launch {
            repository.updateBiometricEnabled(authManager.isBiometricEnabled())
            repository.updatePasswordEnabled(authManager.isPasswordEnabled())
        }
    }

    fun confirmPasswordSetup(password: String) {
        // Set password (this will disable biometric automatically)
        authManager.setAuthenticationMethod(AuthMethod.PASSWORD, password)
        updateRepositoryAuth()
        _showPasswordDialog.value = false
    }

    fun dismissPasswordDialog() {
        _showPasswordDialog.value = false
    }

    fun dismissAuthChangeDialog() {
        _showAuthChangeDialog.value = null
        _authChangeError.value = ""
    }

    fun clearAuthChangeError() {
        _authChangeError.value = ""
    }

    fun updateNoteSwipeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateNoteSwipeEnabled(enabled)
        }
    }

    fun updateSwipeLeftAction(action: SwipeAction) {
        viewModelScope.launch {
            repository.updateSwipeLeftAction(action)
        }
    }

    fun updateSwipeRightAction(action: SwipeAction) {
        viewModelScope.launch {
            repository.updateSwipeRightAction(action)
        }
    }

    fun updateLanguage(language: AppLanguage) {
        viewModelScope.launch {
            repository.updateLanguage(language)
        }

        LocaleManager.setLocale(context, language)

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            _showLanguageRestartDialog.value = true
        }
    }

    fun dismissLanguageRestartDialog() {
        _showLanguageRestartDialog.value = false
        if (context is FragmentActivity && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            LocaleManager.recreateActivityIfNeeded(context)
        }
    }

    fun isBiometricAvailable(): Boolean = authManager.isBiometricAvailable()
}

// 🆕 AUTH CHANGE TYPES
enum class AuthChangeType {
    SWITCH_TO_BIOMETRIC,    // Switching from password to biometric
    SWITCH_TO_PASSWORD,     // Switching from biometric to password
    DISABLE_BIOMETRIC,      // Disabling biometric (no replacement)
    DISABLE_PASSWORD        // Disabling password (no replacement)
}
