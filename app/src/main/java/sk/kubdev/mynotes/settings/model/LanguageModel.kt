package sk.kubdev.mynotes.settings.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

enum class AppLanguage(
    val code: String,
    val displayName: String,
    val nativeDisplayName: String,
    val locale: Locale,
    val flag: String,
    val isAITranslated: Boolean = false  // Add this property
) {
    ENGLISH(
        code = "en",
        displayName = "English",
        nativeDisplayName = "English",
        locale = Locale.ENGLISH,
        flag = "🇬🇧",
        isAITranslated = false  // Human translated
    ),
    SLOVAK(
        code = "sk",
        displayName = "Slovak",
        nativeDisplayName = "Slovenčina",
        locale = Locale("sk", "SK"),
        flag = "🇸🇰",
        isAITranslated = false  // Human translated
    ),
    FRENCH(
        code = "fr",
        displayName = "French",
        nativeDisplayName = "Français",
        locale = Locale.FRENCH,
        flag = "🇫🇷",
        isAITranslated = true  // AI translated
    ),
    GERMAN(
        code = "de",
        displayName = "German",
        nativeDisplayName = "Deutsch",
        locale = Locale.GERMAN,
        flag = "🇩🇪",
        isAITranslated = true  // AI translated
    ),
    ITALIAN(
        code = "it",
        displayName = "Italian",
        nativeDisplayName = "Italiano",
        locale = Locale.ITALIAN,
        flag = "🇮🇹",
        isAITranslated = true  // AI translated
    ),
    SPANISH(
        code = "es",
        displayName = "Spanish",
        nativeDisplayName = "Español",
        locale = Locale("es", "ES"),
        flag = "🇪🇸",
        isAITranslated = true  // AI translated
    ),
    CZECH(
        code = "cs",
        displayName = "Czech",
        nativeDisplayName = "Čeština",
        locale = Locale("cs", "CZ"),
        flag = "🇨🇿",
        isAITranslated = false  // Human translated
    ),
    SYSTEM(
        code = "system",
        displayName = "System Default",
        nativeDisplayName = "System Default",
        locale = Locale.getDefault(),
        flag = "🌐",
        isAITranslated = false  // Not applicable
    );

    companion object {
        fun fromCode(code: String): AppLanguage {
            return values().find { it.code == code } ?: SYSTEM
        }

        fun getCurrentSystemLanguage(): AppLanguage {
            val systemLocale = Locale.getDefault()
            return when (systemLocale.language) {
                "sk" -> SLOVAK
                "en" -> ENGLISH
                "fr" -> FRENCH
                "de" -> GERMAN
                "it" -> ITALIAN
                "es" -> SPANISH
                "cs" -> CZECH
                else -> ENGLISH // Default fallback
            }
        }
    }
}
