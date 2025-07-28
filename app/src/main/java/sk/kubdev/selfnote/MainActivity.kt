package sk.kubdev.selfnote

import android.content.Context
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import sk.kubdev.selfnote.auth.AuthenticationManager
import sk.kubdev.selfnote.auth.AuthenticationScreen
import sk.kubdev.selfnote.backup.BackupWorker
import sk.kubdev.selfnote.backup.BackupFrequency
import sk.kubdev.selfnote.settings.LocaleManager
import sk.kubdev.selfnote.settings.SettingsRepository
import sk.kubdev.selfnote.settings.SettingsViewModel
import sk.kubdev.selfnote.settings.model.AppTheme
import sk.kubdev.selfnote.ui.theme.SelfNoteDynamicTheme

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private lateinit var authManager: AuthenticationManager
    private lateinit var settingsRepository: SettingsRepository

    override fun attachBaseContext(newBase: Context) {
        // Apply saved locale before calling super
        settingsRepository = SettingsRepository(newBase)
        val context = runBlocking {
            try {
                val settings = settingsRepository.settingsFlow.first()
                LocaleManager.setLocale(newBase, settings.language)
            } catch (e: Exception) {
                newBase
            }
        }
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authManager = AuthenticationManager(this)

        // Initialize auto-backup if it was previously enabled
        initializeAutoBackup()

        setContent {
            val settingsViewModel = remember { SettingsViewModel(settingsRepository, authManager, this) }
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

            // Track previous language to detect changes
            var previousLanguage by remember { mutableStateOf(settings.language) }

            // Handle language changes
            LaunchedEffect(settings.language) {
                if (previousLanguage != settings.language) {
                    previousLanguage = settings.language

                    // Prepare auth manager for language change
                    authManager.prepareForLanguageChange()

                    // Set new locale
                    LocaleManager.setLocale(this@MainActivity, settings.language)

                    // Small delay to ensure preferences are saved
                    delay(100)

                    // Recreate activity
                    recreate()
                }
            }

            LaunchedEffect(settings.theme, settings.colorScheme) {
                updateSystemBars(settings.theme, Color(settings.colorScheme.primaryColor))
            }

            SelfNoteDynamicTheme(
                appTheme = settings.theme,
                colorScheme = settings.colorScheme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isAuthenticated by authManager.isAuthenticated.collectAsStateWithLifecycle()
                    val authRequired by authManager.authenticationRequired.collectAsStateWithLifecycle()

                    if (authRequired && !isAuthenticated) {
                        AuthenticationScreen(
                            authManager = authManager,
                            onAuthenticated = {
                                // Authentication successful
                            }
                        )
                    } else {
                        // UPDATED: Use Hilt to inject the ViewModel
                        val noteViewModel: NoteViewModel = hiltViewModel()

                        AppNavigation(
                            viewModel = noteViewModel,
                            authManager = authManager,
                            settingsViewModel = settingsViewModel
                        )
                    }
                }
            }
        }
    }

    private fun initializeAutoBackup() {
        try {
            val prefs = getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("auto_backup_enabled", false)) {
                val frequency = BackupFrequency.valueOf(
                    prefs.getString("backup_frequency", BackupFrequency.DAILY.name)
                        ?: BackupFrequency.DAILY.name
                )
                val hour = prefs.getInt("backup_hour", 2)
                val minute = prefs.getInt("backup_minute", 0)
                val dayOfWeek = prefs.getInt("backup_day_of_week", java.util.Calendar.MONDAY)
                val dayOfMonth = prefs.getInt("backup_day_of_month", 1)

                BackupWorker.schedulePeriodicBackup(
                    context = this,
                    frequency = frequency,
                    hour = hour,
                    minute = minute,
                    dayOfWeek = dayOfWeek,
                    dayOfMonth = dayOfMonth
                )
                println("🔧 DEBUG: Auto-backup scheduled on app start")
            }
        } catch (e: Exception) {
            println("🔧 DEBUG: Failed to initialize auto-backup: ${e.message}")
        }
    }

    private fun updateSystemBars(theme: AppTheme, primaryColor: Color) {
        val isDark = when (theme) {
            AppTheme.LIGHT -> false
            AppTheme.DARK -> true
            AppTheme.SYSTEM -> {
                resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }

        val lightScrim = if (isDark) {
            Color.Black.copy(alpha = 0.2f)
        } else {
            primaryColor.copy(alpha = 0.1f)
        }

        val darkScrim = if (isDark) {
            primaryColor.copy(alpha = 0.3f)
        } else {
            Color.Black.copy(alpha = 0.2f)
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = lightScrim.toArgb(),
                darkScrim = darkScrim.toArgb(),
                detectDarkMode = { isDark }
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = lightScrim.toArgb(),
                darkScrim = darkScrim.toArgb(),
                detectDarkMode = { isDark }
            )
        )
    }

    override fun onPause() {
        super.onPause()
        authManager.onAppGoingToBackground()
    }

    override fun onResume() {
        super.onResume()
        if (authManager.isAuthenticationRequired()) {
            val withinGracePeriod = authManager.onAppComingFromBackground()
        }
    }

    override fun onStop() {
        super.onStop()
        authManager.onAppGoingToBackground()
    }

    override fun onStart() {
        super.onStart()
        if (authManager.isAuthenticationRequired()) {
            authManager.onAppComingFromBackground()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::authManager.isInitialized) {
            authManager.logout()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        authManager.onAppGoingToBackground()
    }
}
