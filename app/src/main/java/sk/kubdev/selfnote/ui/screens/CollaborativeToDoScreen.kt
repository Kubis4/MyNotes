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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import sk.kubdev.selfnote.NoteViewModel
import sk.kubdev.selfnote.R
import sk.kubdev.selfnote.data.remote.models.CollaborativeNote
import sk.kubdev.selfnote.ui.components.LoadingIndicator
import sk.kubdev.selfnote.NoteLine
import sk.kubdev.selfnote.toNoteLines

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollaborativeToDoScreen(
    collaborativeNoteId: String,
    navController: NavController,
    viewModel: NoteViewModel
) {
    val context = LocalContext.current
    var collaborativeNote by remember { mutableStateOf<CollaborativeNote?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Collect real-time updates
    val noteFlow by viewModel.getCollaborativeNoteFlow(collaborativeNoteId)
        .collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(noteFlow) {
        noteFlow?.let { note ->
            collaborativeNote = note
            isLoading = false
        }
    }

    LaunchedEffect(collaborativeNoteId) {
        isLoading = true
        error = null
        // Load initial note data
        viewModel.loadCollaborativeNote(collaborativeNoteId) { result ->
            result.fold(
                onSuccess = { note ->
                    collaborativeNote = note
                    isLoading = false
                },
                onFailure = { throwable ->
                    error = throwable.message ?: "Failed to load collaborative note"
                    isLoading = false
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = collaborativeNote?.title ?: "Collaborative Todo",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // TODO: Show collaborators dialog
                    }) {
                        Icon(Icons.Default.Group, contentDescription = "Collaborators")
                    }

                    IconButton(onClick = {
                        // TODO: Show invite dialog
                    }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Invite")
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    LoadingIndicator(
                        message = "Loading collaborative note...",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                // Retry loading
                                isLoading = true
                                error = null
                                viewModel.loadCollaborativeNote(collaborativeNoteId) { result ->
                                    result.fold(
                                        onSuccess = { note ->
                                            collaborativeNote = note
                                            isLoading = false
                                        },
                                        onFailure = { throwable ->
                                            error = throwable.message ?: "Failed to load collaborative note"
                                            isLoading = false
                                        }
                                    )
                                }
                            }
                        ) {
                            Text("Retry")
                        }
                    }
                }

                collaborativeNote != null -> {
                    CollaborativeToDoContent(
                        note = collaborativeNote!!,
                        onUpdateNote = { title, lines ->
                            // ✅ FIXED: Use the correct method signature
                            viewModel.updateCollaborativeNote(
                                noteId = collaborativeNote!!.id,
                                title = title,
                                lines = lines
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun CollaborativeToDoContent(
    note: CollaborativeNote,
    onUpdateNote: (String, List<NoteLine>) -> Unit, // ✅ FIXED: Updated signature
    modifier: Modifier = Modifier
) {
    // Convert note content to NoteLine list
    val noteLines = remember(note.content) {
        note.content.toNoteLines()
    }

    var title by remember(note.title) { mutableStateOf(note.title) }
    val lines = remember(noteLines) { mutableStateListOf<NoteLine>().apply { addAll(noteLines) } }

    Column(
        modifier = modifier.padding(16.dp)
    ) {
        // Collaboration info bar
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Collaborative Todo",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "${note.collaborators.size} collaborators • Last edited by ${note.lastEditedByEmail}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Title input
        OutlinedTextField(
            value = title,
            onValueChange = { newTitle ->
                title = newTitle
                onUpdateNote(newTitle, lines.toList())
            },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Todo items
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = lines,
                key = { it.id }
            ) { line ->
                CollaborativeToDoItem(
                    line = line,
                    onLineChange = { updatedLine ->
                        val index = lines.indexOfFirst { it.id == updatedLine.id }
                        if (index != -1) {
                            lines[index] = updatedLine
                            onUpdateNote(title, lines.toList())
                        }
                    },
                    onDeleteLine = {
                        lines.removeAll { it.id == line.id }
                        onUpdateNote(title, lines.toList())
                    }
                )
            }

            // Add new item button
            item {
                OutlinedButton(
                    onClick = {
                        val newLine = NoteLine(
                            id = java.util.UUID.randomUUID().toString(),
                            content = "",
                            isChecked = false
                        )
                        lines.add(newLine)
                        onUpdateNote(title, lines.toList())
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Item")
                }
            }
        }
    }
}

@Composable
private fun CollaborativeToDoItem(
    line: NoteLine,
    onLineChange: (NoteLine) -> Unit,
    onDeleteLine: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = line.isChecked,
                onCheckedChange = { isChecked ->
                    onLineChange(line.copy(isChecked = isChecked))
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = line.content,
                onValueChange = { content ->
                    onLineChange(line.copy(content = content))
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Enter todo item...") },
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Collaborative sync indicator
            Icon(
                Icons.Default.CloudSync,
                contentDescription = "Synced",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onDeleteLine) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
