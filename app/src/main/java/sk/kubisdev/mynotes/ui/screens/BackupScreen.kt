package sk.kubisdev.mynotes.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import sk.kubisdev.mynotes.R
import sk.kubisdev.mynotes.backup.*
import sk.kubisdev.mynotes.ui.components.GradientTopAppBar
import sk.kubisdev.mynotes.ui.components.SectionCard
import sk.kubisdev.mynotes.ui.components.SectionIconCircle
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import sk.kubisdev.mynotes.NoteViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    navController: NavController,
    noteViewModel: NoteViewModel
) {
    val context = LocalContext.current
    val backupViewModel: BackupViewModel = viewModel {
        BackupViewModel(context, noteViewModel)
    }

    val uiState by backupViewModel.uiState.collectAsStateWithLifecycle()
    val backupFiles by backupViewModel.backupFiles.collectAsStateWithLifecycle()
    val allNotes by noteViewModel.allNotes.collectAsStateWithLifecycle()
    val archivedNotes by noteViewModel.archivedNotes.collectAsStateWithLifecycle()

    val activeNotesCount = allNotes.size
    val archivedNotesCount = archivedNotes.size
    val totalNotesCount = activeNotesCount + archivedNotesCount

    var isAutoBackupEnabled by remember {
        mutableStateOf(backupViewModel.isAutoBackupEnabled())
    }

    var showCustomNameDialog by remember { mutableStateOf(false) }
    var includeArchivedInCustomBackup by remember { mutableStateOf(true) }

    var showBackupSettingsDialog by remember { mutableStateOf(false) }
    var showNotificationPermissionDialog by remember { mutableStateOf(false) }
    var pendingRestoreBackupId by remember { mutableStateOf<String?>(null) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        backupViewModel.handleSignInResult(result.data)
    }

    // Without this, backup success/failure notifications (BackupNotifier) silently
    // do nothing on Android 13+, since POST_NOTIFICATIONS is a runtime permission there.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        // System dialog didn't appear (permanently denied) or user denied -
        // point them to app notification settings instead.
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

    LaunchedEffect(Unit) {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)

        if (resultCode != ConnectionResult.SUCCESS) {
            println("🔧 DEBUG: Google Play Services not available: $resultCode")
        } else {
            println("🔧 DEBUG: Google Play Services is available")
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // Backup success/failure has no undo to offer, so no snackbar - just clear the
    // transient state once observed.
    LaunchedEffect(uiState.message) {
        if (uiState.message != null) {
            backupViewModel.clearMessage()
        }
    }

    LaunchedEffect(uiState.error) {
        if (uiState.error != null) {
            backupViewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.backup_screen_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!navController.popBackStack()) {
                                navController.navigate("noteList") {
                                    popUpTo(0) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                GoogleDriveSignInCard(
                    isSignedIn = uiState.isSignedIn,
                    accountInfo = uiState.accountInfo,
                    isLoading = uiState.isLoading,
                    onSignIn = {
                        signInLauncher.launch(backupViewModel.getSignInIntent())
                    },
                    onSignOut = { backupViewModel.signOut() }
                )
            }

            if (uiState.isSignedIn) {
                item {
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
                        onSettingsClick = {
                            showBackupSettingsDialog = true
                        }
                    )
                }

                item {
                    DataOverviewCard(
                        activeNotesCount = activeNotesCount,
                        archivedNotesCount = archivedNotesCount,
                        totalNotesCount = totalNotesCount
                    )
                }

                item {
                    CreateBackupCard(
                        isCreating = uiState.isCreatingBackup,
                        progress = uiState.backupProgress,
                        onCreateBackup = { includeArchived ->
                            includeArchivedInCustomBackup = includeArchived
                            showCustomNameDialog = true
                        }
                    )
                }

                item {
                    ExistingBackupsCard(
                        backupFiles = backupFiles,
                        isLoading = uiState.isLoadingBackups,
                        isRestoring = uiState.isRestoring,
                        restoreProgress = uiState.restoreProgress,
                        onRefresh = { backupViewModel.loadBackupFiles() },
                        onRestore = { backupId -> pendingRestoreBackupId = backupId },
                        onDelete = { backupId -> backupViewModel.deleteBackup(backupId) }
                    )
                }
            }

            item {
                SharedNotesBackupNoticeCard()
            }

            item {
                BackupTipsCard()
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showCustomNameDialog) {
        CustomBackupNameDialog(
            onDismiss = { showCustomNameDialog = false },
            onConfirm = { customName ->
                showCustomNameDialog = false
                backupViewModel.createBackup(
                    includeArchived = includeArchivedInCustomBackup,
                    customName = customName
                )
            }
        )
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
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
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

    pendingRestoreBackupId?.let { backupId ->
        RestoreModeDialog(
            onDismiss = { pendingRestoreBackupId = null },
            onConfirm = { replaceExisting ->
                pendingRestoreBackupId = null
                backupViewModel.restoreFromBackup(backupId, replaceExisting)
            }
        )
    }
}

@Composable
private fun RestoreModeDialog(
    onDismiss: () -> Unit,
    onConfirm: (replaceExisting: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_restore_choice_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.backup_restore_choice_description),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = stringResource(R.string.backup_restore_merge_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.backup_restore_merge_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = stringResource(R.string.backup_restore_replace_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.backup_restore_replace_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(true) }) {
                Text(stringResource(R.string.backup_restore_replace_title))
            }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(false) }) {
                Text(stringResource(R.string.backup_restore_merge_title))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoBackupCard(
    isAutoBackupEnabled: Boolean,
    backupSettings: BackupSettings,
    onToggleAutoBackup: (Boolean) -> Unit,
    onSettingsClick: () -> Unit
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionIconCircle(Icons.Default.Schedule)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.backup_auto_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.backup_auto_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isAutoBackupEnabled,
                onCheckedChange = onToggleAutoBackup
            )
        }

        if (isAutoBackupEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .clickable(onClick = onSettingsClick)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val time = String.format("%02d:%02d", backupSettings.hour, backupSettings.minute)
                val scheduleText = when (backupSettings.frequency) {
                    BackupFrequency.DAILY ->
                        stringResource(R.string.backup_schedule_daily, time)
                    BackupFrequency.WEEKLY ->
                        stringResource(R.string.backup_schedule_weekly, getDayName(backupSettings.dayOfWeek), time)
                    BackupFrequency.MONTHLY ->
                        stringResource(R.string.backup_schedule_monthly, backupSettings.dayOfMonth, time)
                }
                Text(
                    text = scheduleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.action_edit),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsDialog(
    currentSettings: BackupSettings,
    onDismiss: () -> Unit,
    onConfirm: (BackupSettings) -> Unit
) {
    var frequency by remember { mutableStateOf(currentSettings.frequency) }
    var hour by remember { mutableStateOf(currentSettings.hour) }
    var minute by remember { mutableStateOf(currentSettings.minute) }
    var dayOfWeek by remember { mutableStateOf(currentSettings.dayOfWeek) }
    var dayOfMonth by remember { mutableStateOf(currentSettings.dayOfMonth) }

    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_schedule_title)) },
        text = {
            Column {
                // Frequency Selection
                Text(
                    text = stringResource(R.string.backup_frequency),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BackupFrequency.values().forEach { freq ->
                        FilterChip(
                            selected = frequency == freq,
                            onClick = { frequency = freq },
                            label = {
                                Text(
                                    stringResource(
                                        when (freq) {
                                            BackupFrequency.DAILY -> R.string.backup_freq_daily
                                            BackupFrequency.WEEKLY -> R.string.backup_freq_weekly
                                            BackupFrequency.MONTHLY -> R.string.backup_freq_monthly
                                        }
                                    )
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Time Selection
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showTimePicker = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.backup_time))
                        Text(
                            text = String.format("%02d:%02d", hour, minute),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Day Selection for Weekly
                if (frequency == BackupFrequency.WEEKLY) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.backup_day_of_week),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val days = listOf(
                        Calendar.MONDAY to stringResource(R.string.day_monday),
                        Calendar.TUESDAY to stringResource(R.string.day_tuesday),
                        Calendar.WEDNESDAY to stringResource(R.string.day_wednesday),
                        Calendar.THURSDAY to stringResource(R.string.day_thursday),
                        Calendar.FRIDAY to stringResource(R.string.day_friday),
                        Calendar.SATURDAY to stringResource(R.string.day_saturday),
                        Calendar.SUNDAY to stringResource(R.string.day_sunday)
                    )

                    LazyColumn(
                        modifier = Modifier.height(150.dp)
                    ) {
                        items(days) { (day, name) ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (dayOfWeek == day)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surface
                                ),
                                onClick = { dayOfWeek = day }
                            ) {
                                Text(
                                    text = name,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }

                // Day Selection for Monthly
                if (frequency == BackupFrequency.MONTHLY) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.backup_day_of_month),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = dayOfMonth.toString(),
                        onValueChange = { value ->
                            value.toIntOrNull()?.let { day ->
                                if (day in 1..28) {
                                    dayOfMonth = day
                                }
                            }
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        BackupSettings(
                            frequency = frequency,
                            hour = hour,
                            minute = minute,
                            dayOfWeek = dayOfWeek,
                            dayOfMonth = dayOfMonth
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )

    if (showTimePicker) {
        TimePickerDialog(
            initialHour = hour,
            initialMinute = minute,
            onTimeSelected = { h, m ->
                hour = h
                minute = m
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onTimeSelected: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_select_time)) },
        text = {
            TimePicker(
                state = timePickerState,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onTimeSelected(timePickerState.hour, timePickerState.minute)
                }
            ) {
                Text(stringResource(R.string.action_set))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun getDayName(dayOfWeek: Int): String {
    return stringResource(
        when (dayOfWeek) {
            Calendar.SUNDAY -> R.string.day_sunday
            Calendar.MONDAY -> R.string.day_monday
            Calendar.TUESDAY -> R.string.day_tuesday
            Calendar.WEDNESDAY -> R.string.day_wednesday
            Calendar.THURSDAY -> R.string.day_thursday
            Calendar.FRIDAY -> R.string.day_friday
            Calendar.SATURDAY -> R.string.day_saturday
            else -> R.string.day_monday
        }
    )
}

@Composable
fun CustomBackupNameDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var customName by remember { mutableStateOf("") }
    // Guards against a fast double-tap firing onConfirm twice before the dialog is
    // dismissed on the next recomposition, which was uploading two backup files.
    var hasConfirmed by remember { mutableStateOf(false) }
    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_name_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.backup_name_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text(stringResource(R.string.backup_name_label)) },
                    placeholder = { Text("MyNotes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(
                        R.string.backup_name_preview,
                        "MyNotes_${if (customName.isNotEmpty()) "${customName}_" else ""}$timestamp.json"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !hasConfirmed,
                onClick = {
                    if (!hasConfirmed) {
                        hasConfirmed = true
                        onConfirm(customName)
                    }
                }
            ) {
                Text(stringResource(R.string.backup_create_new))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Suppress("DEPRECATION") // GoogleSignInAccount: deprecated but functional, see GoogleDriveManager
@Composable
fun GoogleDriveSignInCard(
    isSignedIn: Boolean,
    accountInfo: com.google.android.gms.auth.api.signin.GoogleSignInAccount?,
    isLoading: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionIconCircle(Icons.Default.CloudSync)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.backup_google_drive),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (isSignedIn && accountInfo != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Small green status dot replaces the old "✅ Connected" emoji card
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = accountInfo.email ?: stringResource(R.string.backup_connected),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.backup_signin_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (isSignedIn) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onSignOut) {
                        Text(stringResource(R.string.sign_in_sign_out))
                    }
                }
            }
        }

        if (!isSignedIn) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onSignIn,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.backup_signin_button))
            }
        }
    }
}

@Composable
fun DataOverviewCard(
    activeNotesCount: Int,
    archivedNotesCount: Int,
    totalNotesCount: Int
) {
    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DataStat(title = stringResource(R.string.backup_stat_active), count = activeNotesCount, modifier = Modifier.weight(1f))
            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
            DataStat(title = stringResource(R.string.backup_stat_archived), count = archivedNotesCount, modifier = Modifier.weight(1f))
            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
            DataStat(title = stringResource(R.string.backup_stat_total), count = totalNotesCount, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DataStat(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun CreateBackupCard(
    isCreating: Boolean,
    progress: String,
    onCreateBackup: (Boolean) -> Unit
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionIconCircle(Icons.Default.CloudUpload)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.backup_create_new),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isCreating) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = progress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Button(
                onClick = { onCreateBackup(true) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.backup_all_notes))
            }

            TextButton(
                onClick = { onCreateBackup(false) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.backup_active_only))
            }
        }
    }
}

@Composable
fun ExistingBackupsCard(
    backupFiles: List<BackupFile>,
    isLoading: Boolean,
    isRestoring: Boolean,
    restoreProgress: String,
    onRefresh: () -> Unit,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var showAllFiles by remember { mutableStateOf(false) }
    val visibleFiles = if (showAllFiles) backupFiles else backupFiles.take(3)

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionIconCircle(Icons.Default.CloudDownload)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.backup_existing),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefresh, enabled = !isLoading && !isRestoring) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (isRestoring) {
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = restoreProgress,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (isLoading) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else if (backupFiles.isEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.backup_none_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            Spacer(modifier = Modifier.height(8.dp))

            visibleFiles.forEachIndexed { index, backupFile ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                }
                BackupFileItem(
                    backupFile = backupFile,
                    onRestore = { onRestore(backupFile.id) },
                    onDelete = { onDelete(backupFile.id) },
                    enabled = !isRestoring
                )
            }

            if (backupFiles.size > 3) {
                TextButton(
                    onClick = { showAllFiles = !showAllFiles },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (showAllFiles) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showAllFiles) stringResource(R.string.show_less)
                        else stringResource(R.string.backup_show_all, backupFiles.size)
                    )
                }
            }
        }
    }
}

@Composable
fun BackupFileItem(
    backupFile: BackupFile,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Flat row inside the section card (no bordered card-in-card): name on one
    // ellipsized line, date + size merged into a single meta line.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = backupFile.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${backupFile.getFormattedDate()} · ${backupFile.getFormattedSize()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        IconButton(
            onClick = onRestore,
            enabled = enabled
        ) {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = stringResource(R.string.action_restore),
                modifier = Modifier.size(20.dp),
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }

        IconButton(
            onClick = { showDeleteDialog = true },
            enabled = enabled
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.action_delete),
                modifier = Modifier.size(20.dp),
                tint = if (enabled) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.backup_delete_title)) },
            text = { Text(stringResource(R.string.backup_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

// Collaborative notes are stored in Firestore under the signing-in Google account,
// not in the Room database this screen backs up - so nothing here protects them.
// Spelling that out prevents the "I had a backup" surprise after a reinstall.
@Composable
fun SharedNotesBackupNoticeCard() {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionIconCircle(Icons.Default.Groups)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.backup_shared_notice_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.backup_shared_notice_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        )
    }
}

@Composable
fun BackupTipsCard() {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionIconCircle(Icons.Default.Lightbulb)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.backup_tips_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.backup_tips_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        )
    }
}
