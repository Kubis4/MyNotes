package sk.kubdev.selfnote.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import sk.kubdev.selfnote.backup.*
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import sk.kubdev.selfnote.NoteViewModel
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

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        backupViewModel.handleSignInResult(result.data)
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

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            println("🔧 DEBUG: Message: $message")
            backupViewModel.clearMessage()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            println("🔧 DEBUG: Error: $error")
            backupViewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore") },
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
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
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
                        onRestore = { backupId -> backupViewModel.restoreFromBackup(backupId) },
                        onDelete = { backupId -> backupViewModel.deleteBackup(backupId) }
                    )
                }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoBackupCard(
    isAutoBackupEnabled: Boolean,
    backupSettings: BackupSettings,
    onToggleAutoBackup: (Boolean) -> Unit,
    onSettingsClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Automatic Backup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Automatically backup your notes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Switch(
                    checked = isAutoBackupEnabled,
                    onCheckedChange = onToggleAutoBackup
                )
            }

            if (isAutoBackupEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    onClick = onSettingsClick
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            val scheduleText = when (backupSettings.frequency) {
                                BackupFrequency.DAILY ->
                                    "Daily at ${String.format("%02d:%02d", backupSettings.hour, backupSettings.minute)}"
                                BackupFrequency.WEEKLY ->
                                    "Weekly on ${getDayName(backupSettings.dayOfWeek)} at ${String.format("%02d:%02d", backupSettings.hour, backupSettings.minute)}"
                                BackupFrequency.MONTHLY ->
                                    "Monthly on day ${backupSettings.dayOfMonth} at ${String.format("%02d:%02d", backupSettings.hour, backupSettings.minute)}"
                            }

                            Text(
                                text = scheduleText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Change settings",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
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
        title = { Text("Backup Schedule") },
        text = {
            Column {
                // Frequency Selection
                Text(
                    text = "Frequency",
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
                                    freq.name.lowercase().replaceFirstChar {
                                        if (it.isLowerCase()) it.titlecase() else it.toString()
                                    }
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
                        Text("Time")
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
                        text = "Day of Week",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val days = listOf(
                        Calendar.MONDAY to "Monday",
                        Calendar.TUESDAY to "Tuesday",
                        Calendar.WEDNESDAY to "Wednesday",
                        Calendar.THURSDAY to "Thursday",
                        Calendar.FRIDAY to "Friday",
                        Calendar.SATURDAY to "Saturday",
                        Calendar.SUNDAY to "Sunday"
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
                        text = "Day of Month (1-28)",
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
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
        title = { Text("Select Time") },
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
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun getDayName(dayOfWeek: Int): String {
    return when (dayOfWeek) {
        Calendar.SUNDAY -> "Sunday"
        Calendar.MONDAY -> "Monday"
        Calendar.TUESDAY -> "Tuesday"
        Calendar.WEDNESDAY -> "Wednesday"
        Calendar.THURSDAY -> "Thursday"
        Calendar.FRIDAY -> "Friday"
        Calendar.SATURDAY -> "Saturday"
        else -> "Monday"
    }
}

@Composable
fun CustomBackupNameDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var customName by remember { mutableStateOf("") }
    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup Name") },
        text = {
            Column {
                Text(
                    text = "Enter a custom name for your backup (optional)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Custom name") },
                    placeholder = { Text("My Important Backup") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Preview: SelfNote_${if (customName.isNotEmpty()) "${customName}_" else ""}$timestamp.json",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(customName) }
            ) {
                Text("Create Backup")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun GoogleDriveSignInCard(
    isSignedIn: Boolean,
    accountInfo: com.google.android.gms.auth.api.signin.GoogleSignInAccount?,
    isLoading: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSignedIn) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (isSignedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Google Drive Backup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isSignedIn && accountInfo != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "✅ Connected",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Account: ${accountInfo.email}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onSignOut,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out")
                }
            } else {
                Text(
                    text = "Sign in to Google Drive to backup and restore your notes securely in the cloud.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onSignIn,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign in to Google Drive")
                }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Current Data Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DataStatCard(
                    title = "Active Notes",
                    count = activeNotesCount,
                    icon = Icons.Default.Note
                )
                DataStatCard(
                    title = "Archived",
                    count = archivedNotesCount,
                    icon = Icons.Default.Archive
                )
                DataStatCard(
                    title = "Total",
                    count = totalNotesCount,
                    icon = Icons.Default.Storage
                )
            }
        }
    }
}

@Composable
fun DataStatCard(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CreateBackupCard(
    isCreating: Boolean,
    progress: String,
    onCreateBackup: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Create New Backup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Create a backup of your notes to Google Drive",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            if (isCreating) {
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = progress,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))

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
                    Text("Backup All Notes")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { onCreateBackup(false) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Note,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Backup Active Notes Only")
                }
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Existing Backups",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onRefresh, enabled = !isLoading && !isRestoring) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (isRestoring) {
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = restoreProgress,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else if (isLoading) {
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (backupFiles.isEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No backups found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))

                visibleFiles.forEach { backupFile ->
                    BackupFileItem(
                        backupFile = backupFile,
                        onRestore = { onRestore(backupFile.id) },
                        onDelete = { onDelete(backupFile.id) },
                        enabled = !isRestoring
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (backupFiles.size > 3) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        ),
                        onClick = { showAllFiles = !showAllFiles }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (showAllFiles) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (showAllFiles) "Show Less" else "Show More (${backupFiles.size - 3} more)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = backupFile.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = backupFile.getFormattedDate(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Size: ${backupFile.getFormattedSize()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Row {
                    IconButton(
                        onClick = onRestore,
                        enabled = enabled
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Restore",
                            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }

                    IconButton(
                        onClick = { showDeleteDialog = true },
                        enabled = enabled
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Backup") },
            text = { Text("Are you sure you want to delete this backup? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun BackupTipsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "💡 Backup Tips",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "• Regular backups keep your notes safe\n• Backups include note content, timestamps, and metadata\n• Archived notes are included by default\n• You can restore from any previous backup\n• Backups are stored securely in your Google Drive\n• Choose daily, weekly, or monthly automatic backups\n• Backups run in background even when app is closed\n• Old backups are automatically cleaned up (keeps last 10)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}
