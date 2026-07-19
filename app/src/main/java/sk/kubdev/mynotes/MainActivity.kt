package sk.kubdev.mynotes

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.view.animation.AccelerateInterpolator
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import sk.kubdev.mynotes.auth.AuthenticationManager
import sk.kubdev.mynotes.auth.AuthenticationScreen
import sk.kubdev.mynotes.backup.BackupWorker
import sk.kubdev.mynotes.backup.BackupFrequency
import sk.kubdev.mynotes.settings.LocaleManager
import sk.kubdev.mynotes.settings.SettingsRepository
import sk.kubdev.mynotes.settings.SettingsViewModel
import sk.kubdev.mynotes.settings.model.AppTheme
import sk.kubdev.mynotes.ui.components.LocalGradientHeaders
import sk.kubdev.mynotes.ui.screens.OnboardingScreen
import sk.kubdev.mynotes.ui.theme.MyNotesDynamicTheme

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private lateinit var authManager: AuthenticationManager
    private lateinit var settingsRepository: SettingsRepository

    // updateSystemBars() only runs from a LaunchedEffect keyed on settings.theme/
    // colorScheme, so it does NOT re-fire just because the activity was backgrounded
    // and resumed. A screen lock/unlock cycle in particular can reset more than just
    // the window background - the keyguard's own transition appears to touch the
    // edge-to-edge/system-bar setup too, so reapplying only the ColorDrawable wasn't
    // enough (confirmed: app-switch via Home was fine, lock/unlock still went gray).
    // Caching the last args and redoing the *entire* updateSystemBars() call in
    // onResume() covers both cases.
    private var lastSystemBarsTheme: AppTheme? = null
    private var lastSystemBarsPrimaryColor: Color? = null

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
        // Must be called before super.onCreate() per the SplashScreen API contract.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Until now edge-to-edge was only enabled from a LaunchedEffect after the
        // first composition (via updateSystemBars). That leaves the very first
        // frames of a cold start in the legacy mode where the system reserves the
        // status bar strip and paints it its default gray - exactly the flash the
        // user kept seeing after restarting the app. Enabling it here, before
        // setContent, guarantees the window is edge-to-edge from frame one; the
        // theme-colored variant still re-applies later once settings are loaded.
        enableEdgeToEdge()

        // Custom exit: fade + scale the icon out instead of the system's default
        // instant cut, so the transition into the app feels intentional.
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val iconView = splashScreenView.iconView
            val fade = ObjectAnimator.ofFloat(iconView, "alpha", 1f, 0f)
            val scaleX = ObjectAnimator.ofFloat(iconView, "scaleX", 1f, 1.15f)
            val scaleY = ObjectAnimator.ofFloat(iconView, "scaleY", 1f, 1.15f)
            listOf(fade, scaleX, scaleY).forEach {
                it.interpolator = AccelerateInterpolator()
                it.duration = 220L
            }
            fade.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    splashScreenView.remove()
                    // Platform SplashScreenView.remove() RESTORES the system-bar
                    // appearance from the theme, silently wiping out everything
                    // enableEdgeToEdge() set up - this was the actual source of the
                    // recurring gray status bar after every cold start (warm starts
                    // skip the splash, which is why the bug seemed to come and go).
                    // Re-apply our setup immediately after the reset.
                    val theme = lastSystemBarsTheme
                    val color = lastSystemBarsPrimaryColor
                    if (theme != null && color != null) {
                        updateSystemBars(theme, color)
                    } else {
                        enableEdgeToEdge()
                    }
                }
            })
            fade.start()
            scaleX.start()
            scaleY.start()
        }

        authManager = AuthenticationManager(this)

        // Initialize auto-backup if it was previously enabled
        initializeAutoBackup()

        setContent {
            val settingsViewModel = remember { SettingsViewModel(settingsRepository, authManager, this) }
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

            // Track previous language to detect changes
            var previousLanguage by remember { mutableStateOf(settings.language) }

            // attachBaseContext() already tries to apply the saved locale, but calling
            // AppCompatDelegate.setApplicationLocales() that early (before the Activity is
            // attached) doesn't reliably reach the OS-level per-app language record - on
            // some devices/OS versions it silently no-ops, leaving a stale per-app override
            // (e.g. "sk" from a much earlier test) in place even though the app's own saved
            // preference is correctly "en"/System. Re-apply once here, from a fully running
            // Activity, without triggering the recreate() below (it's a no-op if already correct).
            LaunchedEffect(Unit) {
                LocaleManager.setLocale(this@MainActivity, settings.language)
            }

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

            MyNotesDynamicTheme(
                appTheme = settings.theme,
                colorScheme = settings.colorScheme
            ) {
              CompositionLocalProvider(LocalGradientHeaders provides settings.gradientHeadersEnabled) {
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

                        if (!settings.hasCompletedOnboarding) {
                            OnboardingScreen(
                                noteViewModel = noteViewModel,
                                settingsViewModel = settingsViewModel,
                                // Nothing to do here: completeOnboarding() flips
                                // settings.hasCompletedOnboarding, which is what
                                // actually switches this branch to AppNavigation.
                                onFinished = {}
                            )
                        } else {
                            AppNavigation(
                                viewModel = noteViewModel,
                                settingsViewModel = settingsViewModel
                            )
                        }
                    }
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
                    dayOfMonth = dayOfMonth,
                    // KEEP: only schedule if nothing is queued yet. Using UPDATE here
                    // would recompute the next run time from "now" on every app open,
                    // silently pushing an already-correctly-queued backup to the next day
                    // whenever the app is opened after today's target time.
                    existingWorkPolicy = androidx.work.ExistingPeriodicWorkPolicy.KEEP
                )
                println("🔧 DEBUG: Auto-backup ensured scheduled on app start")
            }
        } catch (e: Exception) {
            println("🔧 DEBUG: Failed to initialize auto-backup: ${e.message}")
        }
    }

    private fun updateSystemBars(theme: AppTheme, primaryColor: Color) {
        lastSystemBarsTheme = theme
        lastSystemBarsPrimaryColor = primaryColor

        val isDark = when (theme) {
            AppTheme.LIGHT -> false
            AppTheme.DARK -> true
            AppTheme.SYSTEM -> {
                resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }

        // SystemBarStyle.auto()'s scrim is only meant to matter on API <29, where a
        // truly transparent status bar isn't reliably supported - on modern Android
        // it should be fully transparent regardless of what's passed here. On this
        // device (API 37) that assumption doesn't seem to hold: the status bar is its
        // own compositor surface layered over the app (confirmed via dumpsys window -
        // "StatusBar#83"), and low-alpha scrim colors were rendering as opaque gray
        // instead of blending through. Using solid, fully-opaque scrim colors that
        // match the theme means even if the platform insists on an opaque fill here,
        // it's at least the right color instead of a default gray.
        val scrimColor = primaryColor.copy(alpha = 1f)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = scrimColor.toArgb(),
                darkScrim = scrimColor.toArgb(),
                detectDarkMode = { isDark }
            ),
            // Navigation bar: fully transparent, so with gesture navigation the app
            // background reaches the very bottom edge instead of showing a system
            // strip under the content (the "white line" band).
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = Color.Transparent.toArgb(),
                darkScrim = Color.Transparent.toArgb(),
                detectDarkMode = { isDark }
            )
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Without this the system may still paint its own translucent contrast
            // scrim behind the gesture area, recreating the band we just removed.
            window.isNavigationBarContrastEnforced = false
        }

        // Belt-and-suspenders for the status bar area: Compose screens paint their
        // own themed background behind it via statusBarsPadding(), but whenever that
        // doesn't extend all the way up for some reason, the window's own background
        // shows through. Left at its default that's the system's neutral gray, which
        // reads as a rendering bug. Matching it to the current primary color means
        // even that fallback looks intentional instead of broken.
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(primaryColor.toArgb()))
    }

    override fun onPause() {
        super.onPause()
        authManager.onAppGoingToBackground()
    }

    override fun onResume() {
        super.onResume()
        if (authManager.isAuthenticationRequired()) {
            authManager.onAppComingFromBackground()
        }
        // See lastSystemBarsTheme's declaration: a screen lock/unlock cycle can reset
        // more than just the window background, so redo the whole edge-to-edge setup
        // here rather than just the ColorDrawable fallback.
        val theme = lastSystemBarsTheme
        val color = lastSystemBarsPrimaryColor
        if (theme != null && color != null) {
            updateSystemBars(theme, color)
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
