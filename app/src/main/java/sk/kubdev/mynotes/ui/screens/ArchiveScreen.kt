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

    // String resources - get them in composable scope
    val archiveAllRestoredMessage = stringResource(R.string.archive_all_restored)

    // Only ShowUndoDeleteSnackbar/ShowUndoArchiveSnackbar (handled where notes are
    // actually deleted/archived) get a snackbar - plain ShowMessage/ShowError here
    // have no action to offer, so they'd just be noise.

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
            GradientTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.archive_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
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
                        // Open the full note, same as the Bin screen - the archived
                        // state is untouched by just viewing it.
                        onNoteClick = {
                            when (note.type) {
                                NoteType.CHECKLIST -> navController.navigate(
                                    sk.kubdev.mynotes.Screen.ToDoList.createRoute(note.id)
                                )
                                NoteType.TEXT -> navController.navigate(
                                    sk.kubdev.mynotes.Screen.NoteDetail.createRoute(note.id)
                                )
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
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onNoteClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Note content with color support
            // NoteItem's own clickable CONSUMES taps, so the Card's onClick never fires
            // for taps landing on the note content (i.e. almost all of them) - the
            // preview must be wired here directly, not on the Card.
            NoteItem(
                note = note,
                onClick = onNoteClick,
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

