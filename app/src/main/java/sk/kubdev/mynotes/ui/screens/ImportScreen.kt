package sk.kubdev.mynotes.ui.screens

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
import sk.kubdev.mynotes.NoteViewModel
import sk.kubdev.mynotes.R
import sk.kubdev.mynotes.ui.components.GradientTopAppBar
import sk.kubdev.mynotes.ui.components.SectionCard
import sk.kubdev.mynotes.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    navController: NavController,
    noteViewModel: NoteViewModel
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var isImporting by remember { mutableStateOf(false) }

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isImporting = true
            noteViewModel.importNotes(uri)
        }
    }

    // Import result has no undo to offer, so no snackbar - just clear the loading state.
    LaunchedEffect(Unit) {
        noteViewModel.uiEvent.collect { event ->
            when (event) {
                is NoteViewModel.UiEvent.ShowMessage -> isImporting = false
                is NoteViewModel.UiEvent.ShowError -> isImporting = false
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
