package sk.kubisdev.mynotes.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import sk.kubisdev.mynotes.NoteViewModel
import sk.kubisdev.mynotes.R
import sk.kubisdev.mynotes.ui.components.GradientTopAppBar
import sk.kubisdev.mynotes.ui.components.SectionCard
import sk.kubisdev.mynotes.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    navController: NavController,
    noteViewModel: NoteViewModel
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var isImporting by remember { mutableStateOf(false) }
    var isRestoringBackup by remember { mutableStateOf(false) }

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isImporting = true
            noteViewModel.importNotes(uri)
        }
    }

    // Restoring the app's own MyNotes backup (.json) - full-fidelity path that keeps
    // colors/pins/groups, unlike the third-party importers above. Its own launcher so
    // it routes to restoreFromLocalBackup instead of the format-detecting importer.
    val pickBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isRestoringBackup = true
            noteViewModel.restoreFromLocalBackup(uri)
        }
    }

    // Import result has no undo to offer, so no snackbar - just clear the loading state.
    LaunchedEffect(Unit) {
        noteViewModel.uiEvent.collect { event ->
            when (event) {
                is NoteViewModel.UiEvent.ShowMessage -> { isImporting = false; isRestoringBackup = false }
                is NoteViewModel.UiEvent.ShowError -> { isImporting = false; isRestoringBackup = false }
                else -> Unit
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            GradientTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nav_import),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!navController.popBackStack()) {
                            navController.navigate("noteList") {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard {
                SectionHeader(
                    icon = Icons.Default.CloudDownload,
                    title = stringResource(R.string.import_backup_title)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.import_backup_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { pickBackupLauncher.launch("*/*") },
                    enabled = !isRestoringBackup,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRestoringBackup) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isRestoringBackup) stringResource(R.string.import_importing)
                        else stringResource(R.string.import_backup_choose_file)
                    )
                }
            }

            SectionCard {
                SectionHeader(
                    icon = Icons.Default.FileOpen,
                    title = stringResource(R.string.import_from_other_title)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.import_from_other_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { pickFileLauncher.launch("*/*") },
                    enabled = !isImporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isImporting) stringResource(R.string.import_importing)
                        else stringResource(R.string.import_choose_file)
                    )
                }
            }

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
}

@Composable
internal fun ImportFormatRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}
