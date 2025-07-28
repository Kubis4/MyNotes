package sk.kubdev.selfnote.settings

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import sk.kubdev.selfnote.settings.model.AppLanguage
import java.util.*

object LocaleManager {

    fun setLocale(context: Context, language: AppLanguage): Context {
        return when (language) {
            AppLanguage.ENGLISH -> setAppLocale(context, "en")
            AppLanguage.SLOVAK -> setAppLocale(context, "sk")
            AppLanguage.FRENCH -> setAppLocale(context, "fr")
            AppLanguage.GERMAN -> setAppLocale(context, "de")
            AppLanguage.ITALIAN -> setAppLocale(context, "it")
            AppLanguage.SPANISH -> setAppLocale(context, "es")
            AppLanguage.CZECH -> setAppLocale(context, "cs")
            AppLanguage.SYSTEM -> setSystemLocale(context)
        }
    }

    private fun setAppLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // For Android 13+ - this doesn't require activity recreation
            val localeList = LocaleListCompat.forLanguageTags(languageCode)
            AppCompatDelegate.setApplicationLocales(localeList)
            updateResources(context, locale)
        } else {
            // For older versions
            updateResources(context, locale)
        }
    }

    private fun setSystemLocale(context: Context): Context {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            context
        } else {
            val systemLocale = Locale.getDefault()
            updateResources(context, systemLocale)
        }
    }

    private fun updateResources(context: Context, locale: Locale): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }

    fun getCurrentLanguage(context: Context): AppLanguage {
        val currentLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }

        return when (currentLocale.language) {
            "sk" -> AppLanguage.SLOVAK
            "en" -> AppLanguage.ENGLISH
            "fr" -> AppLanguage.FRENCH
            "de" -> AppLanguage.GERMAN
            "it" -> AppLanguage.ITALIAN
            "es" -> AppLanguage.SPANISH
            "cs" -> AppLanguage.CZECH
            else -> AppLanguage.SYSTEM
        }
    }

    // 🆕 ONLY RECREATE IF ABSOLUTELY NECESSARY (older Android versions)
    fun recreateActivityIfNeeded(activity: Activity, language: AppLanguage) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Only recreate on older versions where AppCompatDelegate doesn't work
            activity.recreate()
        }
        // On newer versions, AppCompatDelegate handles it automatically
    }
}
