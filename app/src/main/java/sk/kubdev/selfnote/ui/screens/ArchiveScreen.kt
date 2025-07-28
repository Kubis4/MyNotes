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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    navController: NavController,
    viewModel: NoteViewModel,
    settingsViewModel: SettingsViewModel
) {
    HandleBackNavigation(navController = navController)

    val archivedNotes by viewModel.archivedNotes.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current // Add context for string resources

    // Color picker states
    var showColorPicker by remember { mutableStateOf(false) }
    var selectedNoteForColor by remember { mutableStateOf<Note?>(null) }

    // String resources - get them in composable scope
    val archiveAllRestoredMessage = stringResource(R.string.archive_all_restored)

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

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.archive_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (archivedNotes.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    viewModel.restoreAllArchivedNotes()
                                    viewModel.showMessage(archiveAllRestoredMessage) // Use pre-loaded string
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Unarchive,
                                contentDescription = stringResource(R.string.archive_restore_all)
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
        if (archivedNotes.isEmpty()) {
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
                        imageVector = Icons.Default.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.archive_empty_message),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.archive_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(archivedNotes, key = { it.id }) { note ->
                    ArchiveNoteItem(
                        note = note,
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
                        onRestore = { viewModel.triggerUnarchive(note) },
                        onDelete = { viewModel.triggerDelete(note) },
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

@Composable
fun ArchiveNoteItem(
    note: Note,
    onNoteClick: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onNoteClick
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
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Unarchive,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.action_restore))
                }

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
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
