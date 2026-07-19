package sk.kubdev.mynotes.settings

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import sk.kubdev.mynotes.settings.model.AppLanguage
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
            setPlatformAppLocales(context, android.os.LocaleList.forLanguageTags(languageCode))
            updateResources(context, locale)
        } else {
            // For older versions
            updateResources(context, locale)
        }
    }

    private fun setSystemLocale(context: Context): Context {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setPlatformAppLocales(context, android.os.LocaleList.getEmptyLocaleList())
            context
        } else {
            val systemLocale = Locale.getDefault()
            updateResources(context, systemLocale)
        }
    }

    // Calling only AppCompatDelegate.setApplicationLocales() doesn't reliably clear a
    // per-app language override that was set outside of AppCompatDelegate's own
    // tracking (e.g. a stale value from an earlier build/test, or one set via the
    // system Settings > Apps > Language screen) - confirmed on-device: the empty-list
    // call silently no-ops and the OS-level per-app locale record is left untouched,
    // even though DataStore correctly says "System"/English. Calling the platform
    // LocaleManager service directly is the same call `adb shell cmd locale
    // set-app-locales --locales ""` makes, which reliably clears/sets it - so use it
    // as the primary path and keep AppCompatDelegate only as a fallback for the (very
    // unlikely on API 33+) case the platform service isn't available.
    private fun setPlatformAppLocales(context: Context, locales: android.os.LocaleList) {
        try {
            val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
            if (localeManager != null) {
                localeManager.applicationLocales = locales
                return
            }
        } catch (e: Exception) {
            // Fall through to the AppCompatDelegate fallback below
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.wrap(locales))
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
    fun recreateActivityIfNeeded(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Only recreate on older versions where AppCompatDelegate doesn't work
            activity.recreate()
        }
        // On newer versions, AppCompatDelegate handles it automatically
    }
}
