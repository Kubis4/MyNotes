package sk.kubdev.mynotes.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import sk.kubdev.mynotes.HandleBackNavigation
import sk.kubdev.mynotes.LineType
import sk.kubdev.mynotes.NoteViewModel
import sk.kubdev.mynotes.R
import sk.kubdev.mynotes.data.remote.local.entities.Note
import sk.kubdev.mynotes.data.remote.local.entities.NoteType
import sk.kubdev.mynotes.toNoteLines
import sk.kubdev.mynotes.ui.components.ColorPickerDialog
import sk.kubdev.mynotes.ui.components.GradientTopAppBar
import sk.kubdev.mynotes.ui.components.NoteItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    navController: NavController,
    viewModel: NoteViewModel
) {
    HandleBackNavigation(navController = navController)

    val archivedNotes by viewModel.archivedNotes.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Color picker states
    var showColorPicker by remember { mutableStateOf(false) }
    var selectedNoteForColor by remember { mutableStateOf<Note?>(null) }

    // Tapping an archived note used to jump straight into the full editor, which
    // meant the only way to just LOOK at an archived note's content was through a
    // screen that also lets you edit/delete it. This read-only preview lets you
    // view an archived note without touching its archived state either way.
    var previewNote by remember { mutableStateOf<Note?>(null) }

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

    previewNote?.let { note ->
        ArchivedNotePreviewDialog(
            note = note,
            onDismiss = { previewNote = null },
            onRestore = {
                viewModel.triggerUnarchive(note)
                previewNote = null
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            GradientTopAppBar(
                title = { Text(stringResource(R.string.archive_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                }
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
                        modifier = Modifier.animateItemPlacement(),
                        note = note,
                        onNoteClick = { previewNote = note },
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
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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

// Plain read-only rendering of an archived note's content - no text fields, no
// checkbox toggling, no delete/reorder controls. Restore stays available as an
// explicit action here, but it's opt-in rather than the only way to see what's
// inside the note.
@Composable
private fun ArchivedNotePreviewDialog(
    note: Note,
    onDismiss: () -> Unit,
    onRestore: () -> Unit
) {
    val lines = remember(note.content) { note.content.toNoteLines() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = note.title.ifBlank { stringResource(R.string.untitled_note) },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                lines.forEach { line ->
                    when (line.type) {
                        LineType.CHECKLIST -> Text(
                            text = "${if (line.isChecked) "☑" else "☐"} ${line.content}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                        LineType.BULLET -> Text(
                            text = "• ${line.content}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                        LineType.DIVIDER, LineType.SEPARATOR -> HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        LineType.IMAGE -> Unit
                        else -> if (line.content.isNotBlank()) {
                            Text(
                                text = line.content,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRestore) {
                Text(stringResource(R.string.action_restore))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}
