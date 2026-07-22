package sk.kubisdev.mynotes.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import sk.kubisdev.mynotes.NoteViewModel
import sk.kubisdev.mynotes.R
import sk.kubisdev.mynotes.backup.BackupViewModel
import sk.kubisdev.mynotes.settings.ColorSchemeSettingCard
import sk.kubisdev.mynotes.settings.PasswordSetupDialog
import sk.kubisdev.mynotes.settings.SettingsViewModel
import sk.kubisdev.mynotes.ui.components.SectionCard
import sk.kubisdev.mynotes.ui.components.SectionHeader
import sk.kubisdev.mynotes.ui.components.SectionIconCircle

private enum class OnboardingStep(@androidx.annotation.StringRes val nextLabelRes: Int) {
    WELCOME(R.string.onb_get_started),
    APPEARANCE(R.string.onb_next),
    BACKUP(R.string.onb_next),
    IMPORT(R.string.onb_next),
    SECURITY(R.string.onb_finish),
    DONE(R.string.onb_finish) // never shown - DONE hides the bottom bar
}

// Shown once on first launch (gated by SettingsData.hasCompletedOnboarding). Every
// step is skippable and everything configured here can be revisited later in
// Settings / Backup / Import - this is a shortcut, not the only way to set these up.
@Composable
fun OnboardingScreen(
    noteViewModel: NoteViewModel,
    settingsViewModel: SettingsViewModel,
    onFinished: () -> Unit
) {
    val steps = remember { OnboardingStep.entries }
    // Persisted in prefs, not just rememberSaveable: the Google Drive sign-in step
    // bounces through an external activity, and coming back can rebuild this whole
    // composable from scratch (auth-lock swap / process death), which was resetting
    // the tour to the Welcome step mid-way. Prefs survive all of that.
    val context = androidx.compose.ui.platform.LocalContext.current
    val onboardingPrefs = remember {
        context.getSharedPreferences("onboarding_prefs", android.content.Context.MODE_PRIVATE)
    }
    var stepIndex by rememberSaveable {
        mutableIntStateOf(onboardingPrefs.getInt("step", 0).coerceIn(0, steps.lastIndex))
    }
    LaunchedEffect(stepIndex) {
        onboardingPrefs.edit().putInt("step", stepIndex).apply()
    }
    val step = steps[stepIndex]

    fun finish() {
        onboardingPrefs.edit().remove("step").apply()
        settingsViewModel.completeOnboarding()
        onFinished()
    }

    // Skip doesn't silently drop everything: it first reassures the user that all of
    // this lives in Settings, so nothing feels lost by skipping the tour.
    var showSkipDialog by rememberSaveable { mutableStateOf(false) }
    if (showSkipDialog) {
        AlertDialog(
            onDismissRequest = { showSkipDialog = false },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            title = { Text(stringResource(R.string.onb_skip_dialog_title)) },
            text = { Text(stringResource(R.string.onb_skip_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showSkipDialog = false
                    finish()
                }) { Text(stringResource(R.string.onb_skip_dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showSkipDialog = false }) {
                    Text(stringResource(R.string.onb_skip_dialog_keep))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (step != OnboardingStep.DONE) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showSkipDialog = true }) {
                        Text(stringResource(R.string.onb_skip))
                    }
                }
            }
        },
        bottomBar = {
            if (step != OnboardingStep.DONE) {
                OnboardingBottomBar(
                    stepIndex = stepIndex,
                    stepCount = steps.size - 1, // DONE isn't a "step" in the dots
                    nextLabel = stringResource(step.nextLabelRes),
                    onBack = { if (stepIndex > 0) stepIndex-- },
                    onNext = { if (stepIndex < steps.lastIndex) stepIndex++ }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (step) {
                OnboardingStep.WELCOME -> WelcomeStep()
                OnboardingStep.APPEARANCE -> AppearanceStep(settingsViewModel)
                OnboardingStep.BACKUP -> BackupStep(noteViewModel)
                OnboardingStep.IMPORT -> ImportStep(noteViewModel)
                OnboardingStep.SECURITY -> SecurityStep(settingsViewModel)
                OnboardingStep.DONE -> DoneStep(onFinish = { finish() })
            }
        }
    }
}

@Composable
private fun OnboardingBottomBar(
    stepIndex: Int,
    stepCount: Int,
    nextLabel: String,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Active step is an elongated pill, matching the app's rounded look.
            repeat(stepCount) { i ->
                val isActive = i == stepIndex
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(if (isActive) 24.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (stepIndex > 0) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Button(onClick = onNext) { Text(nextLabel) }
        }
    }
}

@Composable
private fun StepHeader(icon: ImageVector, title: String, description: String) {
    SectionIconCircle(icon = icon, size = 88.dp)
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
    Spacer(modifier = Modifier.height(32.dp))
}

@Composable
private fun StepContainer(
    icon: ImageVector,
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        StepHeader(icon = icon, title = title, description = description)
        content()
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Brand-gradient hero circle, echoing the About screen's header.
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(sk.kubisdev.mynotes.ui.components.gradientHeaderBrush()),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.EditNote,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = androidx.compose.ui.graphics.Color.White
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onb_welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onb_welcome_desc),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun AppearanceStep(settingsViewModel: SettingsViewModel) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    StepContainer(
        icon = Icons.Default.Palette,
        title = stringResource(R.string.onb_appearance_title),
        description = stringResource(R.string.onb_appearance_desc)
    ) {
        ColorSchemeSettingCard(
            currentColorScheme = settings.colorScheme,
            onColorSchemeChange = settingsViewModel::updateColorScheme
        )
    }
}

@Composable
private fun BackupStep(noteViewModel: NoteViewModel) {
    val context = LocalContext.current
    val backupViewModel: BackupViewModel = viewModel { BackupViewModel(context, noteViewModel) }
    val uiState by backupViewModel.uiState.collectAsStateWithLifecycle()
    val backupFiles by backupViewModel.backupFiles.collectAsStateWithLifecycle()
    var isAutoBackupEnabled by remember { mutableStateOf(backupViewModel.isAutoBackupEnabled()) }
    var showBackupSettingsDialog by remember { mutableStateOf(false) }
    // Replaces the old standalone "Stay Informed" step: notifications are only
    // meaningful once auto-backup is on, so ask for the permission right when the
    // user enables it here, instead of as an earlier, disconnected wizard step.
    var showNotificationPermissionDialog by remember { mutableStateOf(false) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        backupViewModel.handleSignInResult(result.data)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            showNotificationPermissionDialog = true
        }
    }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Check for existing backups as soon as we're signed in, so someone who already
    // has notes backed up (new device, reinstall) can restore them right here.
    LaunchedEffect(uiState.isSignedIn) {
        if (uiState.isSignedIn) {
            backupViewModel.loadBackupFiles()
        }
    }

    StepContainer(
        icon = Icons.Default.CloudUpload,
        title = stringResource(R.string.onb_backup_title),
        description = stringResource(R.string.onb_backup_desc)
    ) {
        GoogleDriveSignInCard(
            isSignedIn = uiState.isSignedIn,
            accountInfo = uiState.accountInfo,
            isLoading = uiState.isLoading,
            onSignIn = { signInLauncher.launch(backupViewModel.getSignInIntent()) },
            onSignOut = { backupViewModel.signOut() }
        )

        if (uiState.isSignedIn) {
            Spacer(modifier = Modifier.height(16.dp))
            AutoBackupCard(
                isAutoBackupEnabled = isAutoBackupEnabled,
                backupSettings = backupViewModel.getBackupSettings(),
                onToggleAutoBackup = { enabled ->
                    isAutoBackupEnabled = enabled
                    backupViewModel.setAutoBackupEnabled(enabled)
                    if (enabled) {
                        requestNotificationPermissionIfNeeded()
                    }
                },
                onSettingsClick = { showBackupSettingsDialog = true }
            )

            // Only worth showing once we know there's something to restore -
            // an empty "No backups found" card here would just be noise for
            // most people going through this for the first time.
            if (backupFiles.isNotEmpty() || uiState.isLoadingBackups) {
                Spacer(modifier = Modifier.height(16.dp))
                ExistingBackupsCard(
                    backupFiles = backupFiles,
                    isLoading = uiState.isLoadingBackups,
                    isRestoring = uiState.isRestoring,
                    restoreProgress = uiState.restoreProgress,
                    onRefresh = { backupViewModel.loadBackupFiles() },
                    onRestore = { backupFileId -> backupViewModel.restoreFromBackup(backupFileId) },
                    onDelete = { backupFileId -> backupViewModel.deleteBackup(backupFileId) }
                )
            }
        }
    }

    if (showBackupSettingsDialog) {
        BackupSettingsDialog(
            currentSettings = backupViewModel.getBackupSettings(),
            onDismiss = { showBackupSettingsDialog = false },
            onConfirm = { settings ->
                showBackupSettingsDialog = false
                backupViewModel.updateBackupSettings(settings)
            }
        )
    }

    if (showNotificationPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationPermissionDialog = false },
            title = { Text(stringResource(R.string.notif_enable_title)) },
            text = { Text(stringResource(R.string.notif_enable_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationPermissionDialog = false
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                    )
                }) {
                    Text(stringResource(R.string.notif_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationPermissionDialog = false }) {
                    Text(stringResource(R.string.notif_not_now))
                }
            }
        )
    }
}

@Composable
private fun ImportStep(noteViewModel: NoteViewModel) {
    var isImporting by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isImporting = true
            noteViewModel.importNotes(uri)
        }
    }

    LaunchedEffect(Unit) {
        noteViewModel.uiEvent.collect { event ->
            when (event) {
                is NoteViewModel.UiEvent.ShowMessage -> {
                    isImporting = false
                    resultMessage = event.message
                }
                is NoteViewModel.UiEvent.ShowError -> {
                    isImporting = false
                    resultMessage = event.error
                }
                else -> Unit
            }
        }
    }

    StepContainer(
        icon = Icons.Default.Download,
        title = stringResource(R.string.onb_import_title),
        description = stringResource(R.string.onb_import_desc)
    ) {
        resultMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        Button(
            onClick = { pickFileLauncher.launch("*/*") },
            enabled = !isImporting
        ) {
            if (isImporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(stringResource(R.string.onb_import_button))
        }

        Spacer(modifier = Modifier.height(20.dp))

        SectionCard {
            SectionHeader(
                icon = Icons.Default.Article,
                title = stringResource(R.string.import_supported_formats)
            )
            Spacer(modifier = Modifier.height(12.dp))

            ImportFormatRow(
                icon = Icons.Default.Article,
                title = stringResource(R.string.import_format_evernote_title),
                description = stringResource(R.string.import_format_evernote_desc)
            )
            Spacer(modifier = Modifier.height(12.dp))
            ImportFormatRow(
                icon = Icons.Default.Checklist,
                title = stringResource(R.string.import_format_keep_title),
                description = stringResource(R.string.import_format_keep_desc)
            )
            Spacer(modifier = Modifier.height(12.dp))
            ImportFormatRow(
                icon = Icons.Default.Note,
                title = stringResource(R.string.import_format_plaintext_title),
                description = stringResource(R.string.import_format_plaintext_desc)
            )
        }
    }
}

@Composable
private fun SecurityStep(settingsViewModel: SettingsViewModel) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val showPasswordDialog by settingsViewModel.showPasswordDialog.collectAsStateWithLifecycle()
    val isBiometricAvailable = remember { settingsViewModel.isBiometricAvailable() }
    val hasLock = settings.biometricEnabled || settings.passwordEnabled

    StepContainer(
        icon = Icons.Default.Lock,
        title = stringResource(R.string.onb_security_title),
        description = stringResource(R.string.onb_security_desc)
    ) {
        if (hasLock) {
            Row(
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (settings.biometricEnabled) stringResource(R.string.onb_biometric_enabled)
                    else stringResource(R.string.onb_password_enabled)
                )
            }
        } else {
            if (isBiometricAvailable) {
                Button(
                    onClick = { settingsViewModel.updateBiometricEnabled(true) },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.onb_biometric_lock))
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            OutlinedButton(
                onClick = { settingsViewModel.updatePasswordEnabled(true) },
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.onb_password_lock))
            }
        }
    }

    if (showPasswordDialog) {
        PasswordSetupDialog(
            onConfirm = settingsViewModel::confirmPasswordSetup,
            onDismiss = settingsViewModel::dismissPasswordDialog
        )
    }
}

@Composable
private fun DoneStep(onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        SectionIconCircle(icon = Icons.Default.Done, size = 96.dp)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onb_done_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onb_done_desc),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth(0.8f)) {
            Text(stringResource(R.string.onb_done_button))
        }
    }
}
