package sk.kubdev.selfnote.ui.screens

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import sk.kubdev.selfnote.HandleBackNavigation
import sk.kubdev.selfnote.NoteViewModel
import sk.kubdev.selfnote.R
import sk.kubdev.selfnote.Screen
import sk.kubdev.selfnote.data.remote.local.entities.Note
import sk.kubdev.selfnote.data.remote.local.entities.NoteType
import sk.kubdev.selfnote.settings.SettingsViewModel
import sk.kubdev.selfnote.ui.components.ColorPickerDialog
import sk.kubdev.selfnote.ui.components.NoteItem
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinScreen(
    navController: NavController,
    viewModel: NoteViewModel,
    settingsViewModel: SettingsViewModel
) {
    HandleBackNavigation(navController = navController)

    val deletedNotes by viewModel.deletedNotes.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showCleanupDialog by remember { mutableStateOf(false) }

    // Color picker states
    var showColorPicker by remember { mutableStateOf(false) }
    var selectedNoteForColor by remember { mutableStateOf<Note?>(null) }

    // Handle UI events
    LaunchedEffect(key1 = true) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is NoteViewModel.UiEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(message = event.message)
                }
                is NoteViewModel.UiEvent.ShowError -> {
                    snackbarHostState.showSnackbar(message = event.error)
                }
                else -> { /* Handle other events */ }
            }
        }
    }

    // Color picker dialog
    if (showColorPicker && selectedNoteForColor != null) {
        ColorPickerDialog(
            currentColorIndex = selectedNoteForColor!!.colorIndex,
            onColorSelected = { colorIndex ->
                viewModel.updateNoteColor(selectedNoteForColor!!, colorIndex)
                showColorPicker = false
                selectedNoteForColor = null
            },
            onDismiss = {
                showColorPicker = false
                selectedNoteForColor = null
            }
        )
    }

    val context = LocalContext.current

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bin_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (deletedNotes.isNotEmpty()) {
                        // CLEANUP EXPIRED NOTES BUTTON
                        IconButton(
                            onClick = { showCleanupDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = stringResource(R.string.bin_clean_expired)
                            )
                        }
                        // Empty bin button
                        IconButton(
                            onClick = { showDeleteAllDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = stringResource(R.string.bin_empty_action)
                            )
                        }
                        // Restore all button
                        IconButton(
                            onClick = {
                                scope.launch {
                                    viewModel.restoreAllDeletedNotes()
                                    viewModel.showMessage(context.getString(R.string.bin_all_restored))
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestoreFromTrash,
                                contentDescription = stringResource(R.string.bin_restore_all)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        if (deletedNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.bin_empty_message),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.bin_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    // AUTO-DELETE INFO
                    Text(
                        text = stringResource(R.string.bin_auto_delete_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            Column {
                // AUTO-DELETE INFO HEADER
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.bin_auto_delete_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.padding(paddingValues),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(deletedNotes, key = { it.id }) { note ->
                        BinNoteItem(
                            note = note,
                            viewModel = viewModel, // PASS VIEWMODEL FOR EXPIRY CALCULATION
                            onNoteClick = {
                                when (note.type) {
                                    NoteType.CHECKLIST -> {
                                        navController.navigate(Screen.ToDoList.createRoute(note.id))
                                    }
                                    NoteType.TEXT -> {
                                        navController.navigate(Screen.NoteDetail.createRoute(note.id))
                                    }
                                }
                            },
                            onRestore = { viewModel.restoreNote(note) },
                            onPermanentDelete = { viewModel.permanentlyDelete(note) },
                            onLongClick = {
                                selectedNoteForColor = note
                                showColorPicker = true
                            }
                        )
                    }
                }
            }
        }
    }

    // Delete All Confirmation Dialog
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(stringResource(R.string.bin_empty_dialog_title)) },
            text = {
                Text(stringResource(R.string.bin_empty_dialog_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.emptyBin()
                            viewModel.showMessage(context.getString(R.string.bin_emptied))
                        }
                        showDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.bin_delete_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // CLEANUP EXPIRED NOTES DIALOG
    if (showCleanupDialog) {
        AlertDialog(
            onDismissRequest = { showCleanupDialog = false },
            title = { Text(stringResource(R.string.bin_cleanup_dialog_title)) },
            text = {
                Text(stringResource(R.string.bin_cleanup_dialog_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.performManualCleanup()
                        }
                        showCleanupDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.bin_cleanup_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanupDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
fun BinNoteItem(
    note: Note,
    viewModel: NoteViewModel, // ADDED FOR EXPIRY CALCULATION
    onNoteClick: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    // CALCULATE EXPIRY INFO
    val daysUntilExpiry = viewModel.getDaysUntilExpiry(note)
    val isExpired = viewModel.isNoteExpired(note)
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onNoteClick,
        colors = if (isExpired) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Note content with color support
            NoteItem(
                note = note,
                onClick = { /* Handled by Card onClick */ },
                onLongClick = onLongClick
            )

            // EXPIRY INFORMATION
            if (note.deletedAt != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isExpired) Icons.Default.Warning else Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (isExpired) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when {
                            isExpired -> stringResource(R.string.bin_expired_status)
                            daysUntilExpiry != null -> {
                                if (daysUntilExpiry <= 1) {
                                    stringResource(R.string.bin_expires_today)
                                } else {
                                    stringResource(R.string.bin_expires_days, daysUntilExpiry)
                                }
                            }
                            else -> stringResource(R.string.bin_deleted_time, formatDeletedDate(note.deletedAt, context))
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isExpired) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        },
                        fontWeight = if (isExpired) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    enabled = !isExpired // DISABLE RESTORE FOR EXPIRED NOTES
                ) {
                    Icon(
                        imageVector = Icons.Default.RestoreFromTrash,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.action_restore))
                }

                OutlinedButton(
                    onClick = onPermanentDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.action_delete))
                }
            }
        }
    }
}

// HELPER FUNCTION TO FORMAT DELETION DATE
private fun formatDeletedDate(deletedAt: Long, context: android.content.Context): String {
    val date = Date(deletedAt)
    val now = Date()
    val diff = now.time - date.time

    return when {
        diff < 60000 -> context.getString(R.string.time_just_now)
        diff < 3600000 -> context.getString(R.string.time_minutes_ago, diff / 60000)
        diff < 86400000 -> context.getString(R.string.time_hours_ago, diff / 3600000)
        diff < 604800000 -> context.getString(R.string.time_days_ago, diff / 86400000)
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(date)
    }
}
