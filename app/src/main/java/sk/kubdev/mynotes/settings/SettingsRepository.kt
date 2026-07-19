package sk.kubdev.mynotes.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import sk.kubdev.mynotes.settings.model.*

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mynotes_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val THEME_KEY = stringPreferencesKey("theme")
        private val COLOR_SCHEME_KEY = stringPreferencesKey("color_scheme")
        private val BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("biometric_enabled")
        private val PASSWORD_ENABLED_KEY = booleanPreferencesKey("password_enabled")
        private val NOTE_SWIPE_ENABLED_KEY = booleanPreferencesKey("note_swipe_enabled")
        private val SWIPE_LEFT_ACTION_KEY = stringPreferencesKey("swipe_left_action")
        private val SWIPE_RIGHT_ACTION_KEY = stringPreferencesKey("swipe_right_action")
        private val LANGUAGE_KEY = stringPreferencesKey("language") // 🆕 NEW KEY
        private val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("has_completed_onboarding")
        private val GRADIENT_HEADERS_KEY = booleanPreferencesKey("gradient_headers_enabled")
    }

    val settingsFlow: Flow<SettingsData> = context.dataStore.data.map { preferences ->
        SettingsData(
            theme = AppTheme.valueOf(preferences[THEME_KEY] ?: AppTheme.SYSTEM.name),
            colorScheme = AppColorScheme.valueOf(preferences[COLOR_SCHEME_KEY] ?: AppColorScheme.OCEAN_BLUE.name),
            biometricEnabled = preferences[BIOMETRIC_ENABLED_KEY] ?: false,
            passwordEnabled = preferences[PASSWORD_ENABLED_KEY] ?: false,
            noteSwipeEnabled = preferences[NOTE_SWIPE_ENABLED_KEY] ?: true,
            swipeLeftAction = SwipeAction.valueOf(preferences[SWIPE_LEFT_ACTION_KEY] ?: SwipeAction.DELETE.name),
            swipeRightAction = SwipeAction.valueOf(preferences[SWIPE_RIGHT_ACTION_KEY] ?: SwipeAction.ARCHIVE.name),
            language = AppLanguage.fromCode(preferences[LANGUAGE_KEY] ?: AppLanguage.SYSTEM.code), // 🆕 NEW FIELD
            hasCompletedOnboarding = preferences[ONBOARDING_COMPLETED_KEY] ?: false,
            gradientHeadersEnabled = preferences[GRADIENT_HEADERS_KEY] ?: true
        )
    }

    suspend fun updateTheme(theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme.name
        }
    }

    suspend fun updateColorScheme(colorScheme: AppColorScheme) {
        context.dataStore.edit { preferences ->
            preferences[COLOR_SCHEME_KEY] = colorScheme.name
        }
    }

    suspend fun updateBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BIOMETRIC_ENABLED_KEY] = enabled
        }
    }

    suspend fun updatePasswordEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PASSWORD_ENABLED_KEY] = enabled
        }
    }

    suspend fun updateNoteSwipeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NOTE_SWIPE_ENABLED_KEY] = enabled
        }
    }

    suspend fun updateSwipeLeftAction(action: SwipeAction) {
        context.dataStore.edit { preferences ->
            preferences[SWIPE_LEFT_ACTION_KEY] = action.name
        }
    }

    suspend fun updateSwipeRightAction(action: SwipeAction) {
        context.dataStore.edit { preferences ->
            preferences[SWIPE_RIGHT_ACTION_KEY] = action.name
        }
    }

    // 🆕 NEW METHOD
    suspend fun updateLanguage(language: AppLanguage) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = language.code
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED_KEY] = completed
        }
    }

    suspend fun updateGradientHeadersEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[GRADIENT_HEADERS_KEY] = enabled
        }
    }
}