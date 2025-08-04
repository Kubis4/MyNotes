package sk.kubdev.selfnote.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissState
import androidx.compose.material.DismissValue
import androidx.compose.ui.unit.sp
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import sk.kubdev.selfnote.NoteViewModel
import sk.kubdev.selfnote.data.remote.local.entities.Category
import sk.kubdev.selfnote.data.remote.local.entities.Note
import sk.kubdev.selfnote.data.remote.local.entities.NoteType
import sk.kubdev.selfnote.data.remote.models.toNoteLines
import sk.kubdev.selfnote.settings.SettingsViewModel
import sk.kubdev.selfnote.settings.model.SettingsData
import sk.kubdev.selfnote.settings.model.SwipeAction
import sk.kubdev.selfnote.toPlainTextPreview
import sk.kubdev.selfnote.ui.components.ColorPickerDialog
import sk.kubdev.selfnote.ui.theme.ErrorRed
import sk.kubdev.selfnote.ui.theme.NoteColorPalette
import sk.kubdev.selfnote.data.remote.models.CollaborativeNote
import androidx.compose.foundation.gestures.detectTapGestures
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import sk.kubdev.selfnote.LineType
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.luminance

fun getTextColorForBackground(backgroundColor: Color): Color {
    val luminance = backgroundColor.luminance()
    return if (luminance > 0.5) {
        Color.Black
    } else {
        Color.White
    }
}
// Data classes and enums
data class FabButtonItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

enum class NoteLayoutType {
    LIST, GRID
}

enum class NoteFilterType {
    ALL, TEXT_ONLY, CHECKLIST_ONLY, BY_COLOR
}

enum class NoteSortType {
    DATE_NEWEST, DATE_OLDEST, ALPHABETICAL_AZ, ALPHABETICAL_ZA, MANUAL
}

data class NoteSection(
    val id: String,
    val title: String,
    val notes: List<Note>,
    val isEditable: Boolean = false,
    val category: Category? = null
)

data class UnifiedNote(
    val id: String,
    val title: String,
    val content: String,
    val type: NoteType,
    val colorIndex: Int,
    val isPinned: Boolean,
    val categoryId: Int?,
    val lastModifiedAt: Long,
    val isArchived: Boolean,
    val isDeleted: Boolean,
    val orderIndex: Int,
    val isCollaborative: Boolean,
    val collaborativeId: String?,
    val collaboratorCount: Int
) {
    companion object {
        fun fromNote(note: Note): UnifiedNote {
            return UnifiedNote(
                id = note.id.toString(),
                title = note.title,
                content = note.content,
                type = note.type,
                colorIndex = note.colorIndex,
                isPinned = note.isPinned,
                categoryId = note.categoryId,
                lastModifiedAt = note.lastModifiedAt ?: note.createdAt,
                isArchived = note.isArchived,
                isDeleted = note.isDeleted,
                orderIndex = note.orderIndex,
                isCollaborative = false,
                collaborativeId = null,
                collaboratorCount = 0
            )
        }

        fun fromCollaborativeNote(note: CollaborativeNote): UnifiedNote {
            return UnifiedNote(
                id = note.id,
                title = note.title,
                content = note.content,
                type = when (note.type.uppercase()) {
                    "TEXT" -> NoteType.TEXT
                    "CHECKLIST" -> NoteType.CHECKLIST
                    else -> NoteType.CHECKLIST
                },
                colorIndex = 0,
                isPinned = false,
                categoryId = null,
                lastModifiedAt = note.updatedAt,
                isArchived = false,
                isDeleted = false,
                orderIndex = 0,
                isCollaborative = true,
                collaborativeId = note.id,
                collaboratorCount = note.collaborators.size
            )
        }
    }

    fun toNote(): Note {
        return Note(
            id = if (isCollaborative) {
                -(collaborativeId?.hashCode()?.toLong() ?: System.currentTimeMillis())
            } else {
                id.toLongOrNull() ?: 0L
            }.toInt(),
            title = title,
            content = content,
            type = type,
            colorIndex = colorIndex,
            isPinned = isPinned,
            categoryId = categoryId,
            lastModifiedAt = lastModifiedAt,
            isArchived = isArchived,
            isDeleted = isDeleted,
            orderIndex = orderIndex
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun NoteListScreen(
    navController: NavController,
    viewModel: NoteViewModel,
    settingsViewModel: SettingsViewModel,
    onOpenDrawer: () -> Unit = {}
) {
    // State collection
    val notes by viewModel.allNotes.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    // Collaboration states
    val pendingInvites by viewModel.pendingInvites.collectAsStateWithLifecycle(emptyList())
    val rawCollaborativeNotes by viewModel.collaborativeNotes.collectAsStateWithLifecycle(emptyList())
    val collaborationError by viewModel.collaborationError.collectAsStateWithLifecycle(null)
    val isUserSignedIn = viewModel.isUserSignedIn()

    // Filter collaborative notes
    val collaborativeNotes = remember(rawCollaborativeNotes) {
        rawCollaborativeNotes.filter { note ->
            note.id.isNotBlank() &&
                    note.title.isNotBlank() &&
                    note.collaborators.isNotEmpty()
        }
    }

    // String constants
    val sectionPinned = "Pinned"
    val sectionOthers = "Others"
    val fabTodoList = "Todo List"
    val fabNote = "Note"
    val fabNewCategory = "New Category"

    // State variables
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    var isFabExpanded by remember { mutableStateOf(false) }

    // Preferences
    val prefs = remember { context.getSharedPreferences("note_list_prefs", Context.MODE_PRIVATE) }

    var currentLayout by rememberSaveable {
        mutableStateOf(
            NoteLayoutType.values().getOrNull(prefs.getInt("layout_type", 0)) ?: NoteLayoutType.LIST
        )
    }

    var currentSort by rememberSaveable {
        mutableStateOf(
            when (val savedSort = NoteSortType.values().getOrNull(prefs.getInt("sort_type", 0))) {
                NoteSortType.MANUAL -> NoteSortType.DATE_NEWEST
                null -> NoteSortType.DATE_NEWEST
                else -> savedSort
            }
        )
    }

    // Other state variables
    var expandedSections by remember { mutableStateOf(setOf("pinned", "collaborative", "uncategorized")) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<Category?>(null) }
    var showCategoryMenu by remember { mutableStateOf<Pair<Category, Note?>?>(null) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedNoteForOptions by remember { mutableStateOf<Note?>(null) }
    var showInvitationDialog by remember { mutableStateOf(false) }

    // Filter and color picker states
    var showFilterMenu by remember { mutableStateOf(false) }
    var currentFilter by remember { mutableStateOf(NoteFilterType.ALL) }
    var selectedColorFilter by remember { mutableStateOf<Int?>(null) }
    var showColorPicker by remember { mutableStateOf(false) }
    var selectedNoteForColor by remember { mutableStateOf<Note?>(null) }

    // LaunchedEffects
    LaunchedEffect(isUserSignedIn) {
        if (isUserSignedIn) {
            try {
                viewModel.startCollaborativeSync()
            } catch (e: Exception) {
                Log.e("NoteListScreen", "Error starting collaborative sync", e)
            }
        }
    }

    LaunchedEffect(collaborationError) {
        collaborationError?.let { error ->
            snackbarHostState.showSnackbar(
                message = "Collaboration Error: $error",
                duration = SnackbarDuration.Long
            )
        }
    }

    // Save preferences
    LaunchedEffect(currentLayout) {
        prefs.edit().putInt("layout_type", currentLayout.ordinal).apply()
    }

    LaunchedEffect(currentSort) {
        prefs.edit().putInt("sort_type", currentSort.ordinal).apply()
    }

    // Unified notes creation
    val allUnifiedNotes = remember(notes, collaborativeNotes) {
        try {
            // Get all collaborative note IDs that match local notes
            val collaborativeTitles = collaborativeNotes.map { it.title.lowercase() }.toSet()

            // Filter out local notes that have collaborative versions
            val regularNotes = notes
                .filter { localNote ->
                    // Keep the note if it doesn't have a collaborative version
                    !collaborativeTitles.contains(localNote.title.lowercase())
                }
                .map { UnifiedNote.fromNote(it) }

            val collabNotes = collaborativeNotes.map { UnifiedNote.fromCollaborativeNote(it) }

            (regularNotes + collabNotes).sortedByDescending { it.lastModifiedAt }
        } catch (e: Exception) {
            Log.e("NoteListScreen", "Error creating unified notes", e)
            notes.map { UnifiedNote.fromNote(it) }
        }
    }

    // Create sections from unified notes
    val noteSections = remember(allUnifiedNotes, categories, currentFilter, selectedColorFilter, currentSort, sectionPinned, sectionOthers) {
        try {
            // Filter unified notes
            val filtered = when (currentFilter) {
                NoteFilterType.ALL -> allUnifiedNotes
                NoteFilterType.TEXT_ONLY -> allUnifiedNotes.filter { it.type == NoteType.TEXT }
                NoteFilterType.CHECKLIST_ONLY -> allUnifiedNotes.filter { it.type == NoteType.CHECKLIST }
                NoteFilterType.BY_COLOR -> {
                    selectedColorFilter?.let { colorIndex ->
                        allUnifiedNotes.filter { it.colorIndex == colorIndex }
                    } ?: allUnifiedNotes
                }
            }

            val sections = mutableListOf<NoteSection>()

            // Add pinned section (only regular notes can be pinned)
            val pinnedNotes = filtered.filter { it.isPinned && !it.isCollaborative }
            if (pinnedNotes.isNotEmpty()) {
                val sortedPinned = when (currentSort) {
                    NoteSortType.DATE_NEWEST -> pinnedNotes.sortedByDescending { it.lastModifiedAt }
                    NoteSortType.DATE_OLDEST -> pinnedNotes.sortedBy { it.lastModifiedAt }
                    NoteSortType.ALPHABETICAL_AZ -> pinnedNotes.sortedBy { it.title.lowercase() }
                    NoteSortType.ALPHABETICAL_ZA -> pinnedNotes.sortedByDescending { it.title.lowercase() }
                    NoteSortType.MANUAL -> pinnedNotes.sortedBy { it.orderIndex }
                }
                sections.add(NoteSection("pinned", sectionPinned, sortedPinned.map { it.toNote() }))
            }

            // Add collaborative notes section
            val collaborativeFiltered = filtered.filter {
                it.isCollaborative &&
                        it.collaborativeId?.isNotBlank() == true &&
                        it.collaboratorCount > 0
            }
            if (collaborativeFiltered.isNotEmpty()) {
                val sortedCollab = when (currentSort) {
                    NoteSortType.DATE_NEWEST -> collaborativeFiltered.sortedByDescending { it.lastModifiedAt }
                    NoteSortType.DATE_OLDEST -> collaborativeFiltered.sortedBy { it.lastModifiedAt }
                    NoteSortType.ALPHABETICAL_AZ -> collaborativeFiltered.sortedBy { it.title.lowercase() }
                    NoteSortType.ALPHABETICAL_ZA -> collaborativeFiltered.sortedByDescending { it.title.lowercase() }
                    NoteSortType.MANUAL -> collaborativeFiltered.sortedBy { it.orderIndex }
                }
                sections.add(NoteSection("collaborative", "Shared Notes", sortedCollab.map { it.toNote() }))
            }

            // Group unpinned regular notes by category (exclude collaborative notes)
            val unpinnedRegularNotes = filtered.filter { !it.isPinned && !it.isCollaborative }

            // Add categories
            categories.forEach { category ->
                val categoryNotes = unpinnedRegularNotes.filter { it.categoryId == category.id }
                if (categoryNotes.isNotEmpty() || (searchQuery.isEmpty() && currentFilter == NoteFilterType.ALL)) {
                    val sortedNotes = when (currentSort) {
                        NoteSortType.DATE_NEWEST -> categoryNotes.sortedByDescending { it.lastModifiedAt }
                        NoteSortType.DATE_OLDEST -> categoryNotes.sortedBy { it.lastModifiedAt }
                        NoteSortType.ALPHABETICAL_AZ -> categoryNotes.sortedBy { it.title.lowercase() }
                        NoteSortType.ALPHABETICAL_ZA -> categoryNotes.sortedByDescending { it.title.lowercase() }
                        NoteSortType.MANUAL -> categoryNotes.sortedBy { it.orderIndex }
                    }
                    sections.add(NoteSection("category_${category.id}", category.name, sortedNotes.map { it.toNote() }, true, category))
                }
            }

            // Add uncategorized section
            val uncategorizedNotes = unpinnedRegularNotes.filter { it.categoryId == null }
            if (uncategorizedNotes.isNotEmpty()) {
                val sortedNotes = when (currentSort) {
                    NoteSortType.DATE_NEWEST -> uncategorizedNotes.sortedByDescending { it.lastModifiedAt }
                    NoteSortType.DATE_OLDEST -> uncategorizedNotes.sortedBy { it.lastModifiedAt }
                    NoteSortType.ALPHABETICAL_AZ -> uncategorizedNotes.sortedBy { it.title.lowercase() }
                    NoteSortType.ALPHABETICAL_ZA -> uncategorizedNotes.sortedByDescending { it.title.lowercase() }
                    NoteSortType.MANUAL -> uncategorizedNotes.sortedBy { it.orderIndex }
                }
                sections.add(NoteSection("uncategorized", sectionOthers, sortedNotes.map { it.toNote() }))
            }

            sections
        } catch (e: Exception) {
            Log.e("NoteListScreen", "Error creating note sections", e)
            emptyList()
        }
    }

    // ✅ FIXED: FAB items without duplicate note creation
    val fabItems = remember(isUserSignedIn, fabTodoList, fabNote, fabNewCategory) {
        buildList {
            add(FabButtonItem(
                icon = Icons.Default.Checklist,
                label = fabTodoList,
                onClick = {
                    isFabExpanded = false
                    // Navigate with random color index as parameter
                    val randomColor = NoteColorPalette.getRandomColor()
                    navController.navigate("todoList/0?colorIndex=$randomColor")
                }
            ))

            add(FabButtonItem(
                icon = Icons.Default.EditNote,
                label = fabNote,
                onClick = {
                    isFabExpanded = false
                    // Navigate with random color index as parameter
                    val randomColor = NoteColorPalette.getRandomColor()
                    navController.navigate("noteDetail/0?colorIndex=$randomColor")
                }
            ))

            if (isUserSignedIn) {
                add(FabButtonItem(
                    icon = Icons.Default.CloudSync,
                    label = "Collaborative Todo",
                    onClick = {
                        isFabExpanded = false
                        // ✅ FIXED: Navigate to new collaborative todo
                        navController.navigate("collaborative_todo/new")
                    }
                ))
            }

            add(FabButtonItem(
                icon = Icons.Default.CreateNewFolder,
                label = fabNewCategory,
                onClick = {
                    isFabExpanded = false
                    showCategoryDialog = true
                }
            ))
        }
    }

    // Dialog handling
    if (showCategoryDialog || categoryToEdit != null) {
        CategoryDialog(
            category = categoryToEdit,
            onDismiss = {
                showCategoryDialog = false
                categoryToEdit = null
            },
            onConfirm = { name ->
                if (categoryToEdit != null) {
                    viewModel.updateCategory(categoryToEdit!!.copy(name = name))
                } else {
                    viewModel.createCategory(name)
                }
                showCategoryDialog = false
                categoryToEdit = null
            }
        )
    }

    // UI event handling
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is NoteViewModel.UiEvent.ShowUndoDeleteSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Note deleted",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoDelete()
                    }
                }
                is NoteViewModel.UiEvent.ShowUndoArchiveSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Note archived",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoArchive()
                    }
                }
                is NoteViewModel.UiEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short
                    )
                }
                is NoteViewModel.UiEvent.ShowError -> {
                    snackbarHostState.showSnackbar(
                        message = event.error,
                        duration = SnackbarDuration.Long
                    )
                }
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

    // Invitation dialog
    if (showInvitationDialog) {
        NoInvitationsDialog(
            onDismiss = { showInvitationDialog = false },
            onSignIn = {
                showInvitationDialog = false
                navController.navigate("firebase_test")
            }
        )
    }

    // Bottom sheet handling
    if (selectedNoteForOptions != null) {
        val currentNote by remember(selectedNoteForOptions) {
            viewModel.getNoteById(selectedNoteForOptions!!.id)
        }.collectAsState(initial = selectedNoteForOptions)

        if (currentNote != null) {
            val selectedUnifiedNote = remember(currentNote, allUnifiedNotes) {
                allUnifiedNotes.find { unifiedNote ->
                    when {
                        unifiedNote.isCollaborative -> {
                            // Check if the note has collaborative info
                            currentNote!!.collaborativeNoteId == unifiedNote.collaborativeId ||
                                    (currentNote!!.id < 0 && currentNote!!.title == unifiedNote.title)
                        }
                        else -> {
                            unifiedNote.id == currentNote!!.id.toString()
                        }
                    }
                }
            }

            ModalBottomSheet(
                onDismissRequest = { selectedNoteForOptions = null },
                sheetState = bottomSheetState
            ) {
                NoteOptionsBottomSheet(
                    note = currentNote!!,
                    categories = categories,
                    onPinToggle = {
                        viewModel.toggleNotePin(currentNote!!)
                        selectedNoteForOptions = null
                    },
                    onColorChange = {
                        selectedNoteForColor = currentNote
                        showColorPicker = true
                        selectedNoteForOptions = null
                    },
                    onCategoryChange = { categoryId ->
                        viewModel.moveNoteToCategory(currentNote!!, categoryId)
                        selectedNoteForOptions = null
                    },
                    onArchive = {
                        viewModel.triggerArchive(currentNote!!)
                        selectedNoteForOptions = null
                    },
                    onDelete = {
                        if (selectedUnifiedNote?.isCollaborative == true) {
                            selectedUnifiedNote.collaborativeId?.let { collabId ->
                                Log.d("NoteListScreen", "Leaving collaborative note: $collabId")
                                viewModel.leaveCollaborativeNote(collabId)
                            }
                        } else {
                            viewModel.triggerDelete(currentNote!!)
                        }
                        selectedNoteForOptions = null
                    },
                    onDismiss = { selectedNoteForOptions = null },
                    onTitleChange = { newTitle ->
                        viewModel.updateNoteTitle(currentNote!!, newTitle)
                    },
                    viewModel = viewModel,
                    isCollaborative = selectedUnifiedNote?.isCollaborative ?: false
                )
            }
        }
    }

    // Main UI
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    // Search bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f),
                                MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search notes...",
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChange(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onPrimary
                            ),
                            keyboardOptions = KeyboardOptions.Default.copy(
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = { focusManager.clearFocus() }
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.onPrimary)
                        )
                        if (searchQuery.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(
                                    onClick = { viewModel.onSearchQueryChange("") },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open Menu"
                        )
                    }
                },
                actions = {
                    // Invitations button (only if signed in)
                    if (isUserSignedIn) {
                        IconButton(
                            onClick = {
                                if (pendingInvites.isNotEmpty()) {
                                    navController.navigate("pending_invitations")
                                } else {
                                    showInvitationDialog = true
                                }
                            }
                        ) {
                            if (pendingInvites.isNotEmpty()) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.error
                                        ) {
                                            Text(
                                                text = pendingInvites.size.toString(),
                                                color = MaterialTheme.colorScheme.onError,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Mail,
                                        contentDescription = "Invitations",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            } else {
                                Icon(
                                    Icons.Default.MailOutline,
                                    contentDescription = "No Invitations",
                                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    // Filter button
                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter notes"
                            )
                        }

                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            // Filter options
                            Text(
                                "Filter",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )

                            DropdownMenuItem(
                                text = { Text("All Notes") },
                                onClick = {
                                    currentFilter = NoteFilterType.ALL
                                    showFilterMenu = false
                                },
                                leadingIcon = {
                                    if (currentFilter == NoteFilterType.ALL) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Text Only") },
                                onClick = {
                                    currentFilter = NoteFilterType.TEXT_ONLY
                                    showFilterMenu = false
                                },
                                leadingIcon = {
                                    if (currentFilter == NoteFilterType.TEXT_ONLY) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Checklist Only") },
                                onClick = {
                                    currentFilter = NoteFilterType.CHECKLIST_ONLY
                                    showFilterMenu = false
                                },
                                leadingIcon = {
                                    if (currentFilter == NoteFilterType.CHECKLIST_ONLY) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )

                            HorizontalDivider()

                            // Sort options
                            Text(
                                "Sort",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )

                            DropdownMenuItem(
                                text = { Text("Newest First") },
                                onClick = {
                                    currentSort = NoteSortType.DATE_NEWEST
                                    showFilterMenu = false
                                },
                                leadingIcon = {
                                    if (currentSort == NoteSortType.DATE_NEWEST) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Oldest First") },
                                onClick = {
                                    currentSort = NoteSortType.DATE_OLDEST
                                    showFilterMenu = false
                                },
                                leadingIcon = {
                                    if (currentSort == NoteSortType.DATE_OLDEST) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("A-Z") },
                                onClick = {
                                    currentSort = NoteSortType.ALPHABETICAL_AZ
                                    showFilterMenu = false
                                },
                                leadingIcon = {
                                    if (currentSort == NoteSortType.ALPHABETICAL_AZ) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Z-A") },
                                onClick = {
                                    currentSort = NoteSortType.ALPHABETICAL_ZA
                                    showFilterMenu = false
                                },
                                leadingIcon = {
                                    if (currentSort == NoteSortType.ALPHABETICAL_ZA) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                        }
                    }

                    // Layout toggle
                    IconButton(onClick = {
                        currentLayout = when (currentLayout) {
                            NoteLayoutType.LIST -> NoteLayoutType.GRID
                            NoteLayoutType.GRID -> NoteLayoutType.LIST
                        }
                    }) {
                        Icon(
                            imageVector = when (currentLayout) {
                                NoteLayoutType.LIST -> Icons.Default.GridView
                                NoteLayoutType.GRID -> Icons.Default.ViewList
                            },
                            contentDescription = "Change layout"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            MultiFloatingActionButton(
                isExpanded = isFabExpanded,
                onFabClick = { isFabExpanded = !isFabExpanded },
                items = fabItems
            )
        }
    ) { paddingValues ->
        if (noteSections.isEmpty()) {
            // Empty state
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
                    Text(
                        text = when {
                            searchQuery.isNotEmpty() -> "No notes found for \"$searchQuery\""
                            currentFilter != NoteFilterType.ALL -> "No notes match the current filter"
                            else -> "No notes yet. Create your first note!"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )

                    if (!isUserSignedIn) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { navController.navigate("firebase_test") }
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign in to collaborate")
                        }
                    }
                }
            }
        } else {
            // Proper layout switching between List and Grid
            when (currentLayout) {
                NoteLayoutType.LIST -> {
                    // LIST LAYOUT
                    LazyColumn(
                        modifier = Modifier.padding(paddingValues),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        noteSections.forEach { section ->
                            stickyHeader {
                                SectionHeader(
                                    title = section.title,
                                    count = section.notes.size,
                                    isExpanded = expandedSections.contains(section.id),
                                    isEditable = section.isEditable,
                                    onToggleExpand = {
                                        expandedSections = if (expandedSections.contains(section.id)) {
                                            expandedSections - section.id
                                        } else {
                                            expandedSections + section.id
                                        }
                                    },
                                    onEdit = {
                                        categoryToEdit = section.category
                                    },
                                    onDelete = {
                                        section.category?.let { viewModel.deleteCategory(it) }
                                    }
                                )
                            }

                            if (expandedSections.contains(section.id)) {
                                if (section.notes.isEmpty() && section.isEditable) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 24.dp, vertical = 32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No notes in this category",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                } else {
                                    items(
                                        items = section.notes,
                                        key = { note -> "${section.id}_note_${note.id}_${note.lastModifiedAt}" }
                                    ) { note ->
                                        val unifiedNote = remember(note, allUnifiedNotes) {
                                            allUnifiedNotes.find { unified ->
                                                when {
                                                    // For collaborative notes, check if this note matches
                                                    unified.isCollaborative -> {
                                                        // Match by negative ID or by title
                                                        (note.id < 0 && unified.title == note.title) ||
                                                                (note.collaborativeNoteId == unified.collaborativeId)
                                                    }
                                                    // For regular notes, match by ID
                                                    else -> unified.id == note.id.toString()
                                                }
                                            }
                                        }

                                        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                            SwipeableNoteItem(
                                                note = note,
                                                settings = settings,
                                                onNoteClick = {
                                                    if (unifiedNote?.isCollaborative == true) {
                                                        Log.d("NoteListScreen", "Navigating to collaborative note: ${unifiedNote.collaborativeId}")
                                                        navController.navigate("collaborative_todo/${unifiedNote.collaborativeId}")
                                                    } else {
                                                        when (note.type) {
                                                            NoteType.CHECKLIST -> {
                                                                navController.navigate("todoList/${note.id}")
                                                            }
                                                            NoteType.TEXT -> {
                                                                navController.navigate("noteDetail/${note.id}")
                                                            }
                                                        }
                                                    }
                                                },
                                                onDelete = { viewModel.triggerDelete(note) },
                                                onArchive = { viewModel.triggerArchive(note) },
                                                onLongClick = {
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    selectedNoteForOptions = note
                                                },
                                                onPinToggle = { viewModel.toggleNotePin(note) },
                                                isExpanded = true,
                                                isCollaborative = unifiedNote?.isCollaborative ?: false,
                                                collaboratorCount = unifiedNote?.collaboratorCount ?: 0
                                            )
                                        }
                                    }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }

                NoteLayoutType.GRID -> {
                    // GRID LAYOUT
                    LazyColumn(
                        modifier = Modifier.padding(paddingValues),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        noteSections.forEach { section ->
                            stickyHeader {
                                SectionHeader(
                                    title = section.title,
                                    count = section.notes.size,
                                    isExpanded = expandedSections.contains(section.id),
                                    isEditable = section.isEditable,
                                    onToggleExpand = {
                                        expandedSections = if (expandedSections.contains(section.id)) {
                                            expandedSections - section.id
                                        } else {
                                            expandedSections + section.id
                                        }
                                    },
                                    onEdit = {
                                        categoryToEdit = section.category
                                    },
                                    onDelete = {
                                        section.category?.let { viewModel.deleteCategory(it) }
                                    }
                                )
                            }

                            if (expandedSections.contains(section.id)) {
                                if (section.notes.isEmpty() && section.isEditable) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 24.dp, vertical = 32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No notes in this category",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                } else {
                                    // Manual grid layout without nested scrollable
                                    val chunkedNotes = section.notes.chunked(2)

                                    chunkedNotes.forEach { rowNotes ->
                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp)
                                                    .padding(bottom = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                rowNotes.forEach { note ->
                                                    val unifiedNote = remember(note, allUnifiedNotes) {
                                                        allUnifiedNotes.find { unified ->
                                                            when {
                                                                // For collaborative notes, check if this note matches
                                                                unified.isCollaborative -> {
                                                                    // Match by negative ID or by title
                                                                    (note.id < 0 && unified.title == note.title) ||
                                                                            (note.collaborativeNoteId == unified.collaborativeId)
                                                                }
                                                                // For regular notes, match by ID
                                                                else -> unified.id == note.id.toString()
                                                            }
                                                        }
                                                    }

                                                    Box(
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        GridNoteItem(
                                                            note = note,
                                                            onNoteClick = {
                                                                if (unifiedNote?.isCollaborative == true) {
                                                                    navController.navigate("collaborative_todo/${unifiedNote.collaborativeId}")
                                                                } else {
                                                                    when (note.type) {
                                                                        NoteType.CHECKLIST -> {
                                                                            navController.navigate("todoList/${note.id}")
                                                                        }
                                                                        NoteType.TEXT -> {
                                                                            navController.navigate("noteDetail/${note.id}")
                                                                        }
                                                                    }
                                                                }
                                                            },
                                                            onLongClick = {
                                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                selectedNoteForOptions = note
                                                            },
                                                            isCollaborative = unifiedNote?.isCollaborative ?: false,
                                                            collaboratorCount = unifiedNote?.collaboratorCount ?: 0,
                                                            onPinToggle = {
                                                                viewModel.toggleNotePin(note)
                                                            }
                                                        )
                                                    }
                                                }

                                                // Add empty box if odd number of items in last row
                                                if (rowNotes.size == 1) {
                                                    Box(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Category selection menu
    showCategoryMenu?.let { (fromCategory, note) ->
        if (note != null) {
            CategorySelectionDialog(
                currentCategoryId = note.categoryId,
                categories = categories,
                onCategorySelected = { categoryId ->
                    viewModel.moveNoteToCategory(note, categoryId)
                    showCategoryMenu = null
                },
                onDismiss = { showCategoryMenu = null }
            )
        }
    }
}

// Grid Note Item Component with proper text line breaks and collaborative preview
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridNoteItem(
    note: Note,
    onNoteClick: () -> Unit,
    onLongClick: () -> Unit,
    isCollaborative: Boolean = false,
    collaboratorCount: Int = 0,
    onPinToggle: () -> Unit = {}
) {
    var isContentExpanded by remember(note.id) { mutableStateOf(false) }

    // Get note color and calculate appropriate text color
    val noteColor = NoteColorPalette.getColorByIndex(note.colorIndex)
    val textColor = getTextColorForBackground(noteColor)

    // Calculate task progress for checklists
    val taskProgress = remember(note.content, note.type) {
        if (note.type == NoteType.CHECKLIST) {
            try {
                val noteLines = note.content.toNoteLines()
                val checklistItems = noteLines.filter { it.type == LineType.CHECKLIST }
                val completedItems = checklistItems.filter { it.isChecked }
                if (checklistItems.isNotEmpty()) {
                    completedItems.size to checklistItems.size
                } else {
                    0 to 0
                }
            } catch (e: Exception) {
                0 to 0
            }
        } else {
            0 to 0
        }
    }

    // Content preview with image filtering
    val contentPreview = remember(note.content, note.type) {
        if (note.content.isBlank()) {
            ""
        } else {
            try {
                when (note.type) {
                    NoteType.CHECKLIST -> {
                        val noteLines = note.content.toNoteLines()
                        val activeChecklistItems = noteLines.filter {
                            it.type == LineType.CHECKLIST &&
                                    !it.isChecked &&
                                    it.content.isNotBlank()
                        }

                        if (activeChecklistItems.isNotEmpty()) {
                            activeChecklistItems.joinToString("\n") { line ->
                                "○ ${line.content}"
                            }
                        } else {
                            val completedItems = noteLines.filter {
                                it.type == LineType.CHECKLIST &&
                                        it.isChecked &&
                                        it.content.isNotBlank()
                            }
                            completedItems.joinToString("\n") { line ->
                                "✓ ${line.content}"
                            }
                        }
                    }
                    NoteType.TEXT -> {
                        val noteLines = note.content.toNoteLines()
                        noteLines
                            .filter { line ->
                                val content = line.content.lowercase()
                                !content.contains(".jpg") &&
                                        !content.contains(".jpeg") &&
                                        !content.contains(".png") &&
                                        !content.contains(".gif") &&
                                        !content.contains(".bmp") &&
                                        !content.contains(".webp") &&
                                        !content.contains("note_image") &&
                                        !content.contains("/data/user/") &&
                                        !content.contains("/files/") &&
                                        !content.startsWith("file://") &&
                                        !content.startsWith("content://")
                            }
                            .map { line ->
                                when (line.type) {
                                    LineType.TEXT -> line.content
                                    LineType.CHECKLIST -> {
                                        if (line.isChecked) "✓ ${line.content}" else "○ ${line.content}"
                                    }
                                    LineType.BULLET -> "• ${line.content}"
                                    else -> line.content
                                }
                            }
                            .filter { it.isNotBlank() }
                            .joinToString("\n")
                            .take(300)
                    }
                }
            } catch (e: Exception) {
                note.content.take(300)
            }
        }
    }

    // Check if content has more than 3 lines
    val contentLines = contentPreview.split("\n")
    val hasMoreContent = contentLines.size > 3
    val displayContent = if (isContentExpanded || !hasMoreContent) {
        contentPreview
    } else {
        contentLines.take(3).joinToString("\n")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .combinedClickable(
                onClick = onNoteClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = noteColor
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .padding(bottom = 30.dp)
            ) {
                // Header with title and pin button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = note.title.ifEmpty { "Untitled" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = textColor, // ✅ Fixed text color
                        modifier = Modifier.weight(1f),
                        maxLines = if (isContentExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isCollaborative) {
                            Icon(
                                Icons.Default.CloudSync,
                                contentDescription = "Collaborative",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            IconButton(
                                onClick = onPinToggle,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                    contentDescription = if (note.isPinned) "Unpin" else "Pin",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (note.isPinned) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        textColor.copy(alpha = 0.6f) // ✅ Fixed icon color
                                    }
                                )
                            }
                        }
                    }
                }

                // Task progress for checklists (including collaborative)
                if (note.type == NoteType.CHECKLIST && taskProgress.second > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { taskProgress.first.toFloat() / taskProgress.second.toFloat() },
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = textColor.copy(alpha = 0.1f) // ✅ Fixed track color
                        )
                        Text(
                            text = "${taskProgress.first}/${taskProgress.second}",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.6f), // ✅ Fixed text color
                            fontSize = 10.sp
                        )
                    }
                }

                // Content preview
                if (displayContent.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = displayContent,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = if (isContentExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        color = textColor.copy(alpha = 0.7f), // ✅ Fixed text color
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.2f
                    )

                    // Show More/Less button
                    if (hasMoreContent) {
                        TextButton(
                            onClick = { isContentExpanded = !isContentExpanded },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isContentExpanded) "Show Less" else "Show More",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp
                            )
                            Icon(
                                imageVector = if (isContentExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(12.dp)
                                    .padding(start = 2.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Footer pinned to bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Full date and time
                val formattedDate = remember(note.lastModifiedAt) {
                    val date = Date(note.lastModifiedAt ?: note.createdAt)
                    SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(date)
                }

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.5f), // ✅ Fixed text color
                    fontSize = 10.sp
                )

                if (isCollaborative && collaboratorCount > 0) {
                    Text(
                        text = "$collaboratorCount collaborators",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
fun NoInvitationsDialog(
    onDismiss: () -> Unit,
    onSignIn: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("No Invitations") },
        text = {
            Text("You don't have any pending invitations. Sign in to start collaborating with others!")
        },
        confirmButton = {
            Button(onClick = onSignIn) {
                Text("Sign In")
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
fun NoteOptionsBottomSheet(
    note: Note,
    categories: List<Category>,
    onPinToggle: () -> Unit,
    onColorChange: () -> Unit,
    onCategoryChange: (Int?) -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onTitleChange: (String) -> Unit,
    viewModel: NoteViewModel? = null,
    isCollaborative: Boolean = false
) {
    var isEditingTitle by remember { mutableStateOf(false) }
    var editedTitle by remember(note.title) { mutableStateOf(TextFieldValue(note.title)) }
    val focusRequester = remember { FocusRequester() }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        // Title editing section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isEditingTitle) {
                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = { editedTitle = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Note title") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onTitleChange(editedTitle.text)
                            isEditingTitle = false
                        }
                    )
                )
                IconButton(
                    onClick = {
                        onTitleChange(editedTitle.text)
                        isEditingTitle = false
                    }
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Save")
                }
                IconButton(
                    onClick = {
                        editedTitle = TextFieldValue(note.title)
                        isEditingTitle = false
                    }
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
            } else {
                Text(
                    text = note.title.ifEmpty { "Untitled Note" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        editedTitle = TextFieldValue(
                            text = note.title,
                            selection = TextRange(note.title.length)
                        )
                        isEditingTitle = true
                    }
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit title",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        LaunchedEffect(isEditingTitle) {
            if (isEditingTitle) {
                focusRequester.requestFocus()
            }
        }

        HorizontalDivider()

        if (isCollaborative) {
            // Collaborative options
            ListItem(
                headlineContent = { Text("View Collaborators") },
                supportingContent = { Text("See who has access to this note") },
                leadingContent = {
                    Icon(Icons.Default.Group, contentDescription = null)
                },
                modifier = Modifier.clickable {
                    // TODO: Implement collaborators dialog
                }
            )

            ListItem(
                headlineContent = { Text("Share Link") },
                supportingContent = { Text("Get a shareable link for this note") },
                leadingContent = {
                    Icon(Icons.Default.Share, contentDescription = null)
                },
                modifier = Modifier.clickable {
                    // TODO: Implement share link
                }
            )
        } else {
            // Regular note options
            ListItem(
                headlineContent = { Text(if (note.isPinned) "Unpin" else "Pin") },
                leadingContent = {
                    Icon(
                        imageVector = if (note.isPinned) Icons.Outlined.PushPin else Icons.Filled.PushPin,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { onPinToggle() }
            )

            // Category change
            var showCategoryDialog by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text("Move to Category") },
                supportingContent = {
                    val currentCategory = categories.find { it.id == note.categoryId }
                    Text(currentCategory?.name ?: "Others")
                },
                leadingContent = {
                    Icon(Icons.Default.Folder, contentDescription = null)
                },
                modifier = Modifier.clickable { showCategoryDialog = true }
            )

            if (showCategoryDialog) {
                CategorySelectionDialog(
                    currentCategoryId = note.categoryId,
                    categories = categories,
                    onCategorySelected = {
                        onCategoryChange(it)
                        showCategoryDialog = false
                    },
                    onDismiss = { showCategoryDialog = false }
                )
            }

            // Color change
            ListItem(
                headlineContent = { Text("Change Color") },
                leadingContent = {
                    Icon(Icons.Default.Palette, contentDescription = null)
                },
                trailingContent = {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(NoteColorPalette.getColorByIndex(note.colorIndex))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                },
                modifier = Modifier.clickable { onColorChange() }
            )

            // Archive
            ListItem(
                headlineContent = { Text("Archive") },
                leadingContent = {
                    Icon(Icons.Default.Archive, contentDescription = null)
                },
                modifier = Modifier.clickable { onArchive() }
            )
        }

        // Delete/Leave option
        ListItem(
            headlineContent = {
                Text(
                    text = if (isCollaborative) "Leave Collaborative Note" else "Delete",
                    color = MaterialTheme.colorScheme.error
                )
            },
            leadingContent = {
                Icon(
                    imageVector = if (isCollaborative) Icons.Default.ExitToApp else Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            modifier = Modifier.clickable {
                showDeleteConfirmation = true
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Text(
                    text = if (isCollaborative) "Leave Collaborative Note?" else "Delete Note?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isCollaborative) {
                        "Are you sure you want to leave \"${note.title.ifEmpty { "Untitled Note" }}\"?\n\n" +
                                "• You will lose access to this collaborative note\n" +
                                "• You'll need a new invitation to rejoin\n" +
                                "• The note will remain accessible to other collaborators"
                    } else {
                        "Are you sure you want to delete \"${note.title.ifEmpty { "Untitled Note" }}\"?\n\nThis action cannot be undone."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = if (isCollaborative) "Leave Note" else "Delete",
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    count: Int,
    isExpanded: Boolean,
    isEditable: Boolean,
    onToggleExpand: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(if (isExpanded) 0f else -90f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (isEditable) {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Options",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                onEdit()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                onDelete()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            }
                        )
                    }
                }
            }
        }
    }
}

// FAB with proper theme colors
@Composable
fun MultiFloatingActionButton(
    isExpanded: Boolean,
    onFabClick: () -> Unit,
    items: List<FabButtonItem>,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "fab_rotation"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(300)
            ),
            exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(200)
            )
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Label
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            shadowElevation = 4.dp,
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = item.label,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Small FAB with proper primary colors
                        SmallFloatingActionButton(
                            onClick = item.onClick,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            elevation = FloatingActionButtonDefaults.elevation(
                                defaultElevation = 6.dp,
                                pressedElevation = 8.dp
                            )
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label
                            )
                        }
                    }
                }
            }
        }

        // Main FAB
        FloatingActionButton(
            onClick = onFabClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 8.dp
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = if (isExpanded) "Close" else "Add",
                modifier = Modifier.rotate(rotation),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
fun CategoryDialog(
    category: Category?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var categoryName by remember { mutableStateOf(category?.name ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "New Category" else "Edit Category") },
        text = {
            OutlinedTextField(
                value = categoryName,
                onValueChange = { categoryName = it },
                label = { Text("Category Name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (categoryName.isNotBlank()) {
                        onConfirm(categoryName.trim())
                    }
                },
                enabled = categoryName.isNotBlank()
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
}

@Composable
fun CategorySelectionDialog(
    currentCategoryId: Int?,
    categories: List<Category>,
    onCategorySelected: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to Category") },
        text = {
            Column {
                // Uncategorized option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCategorySelected(null) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentCategoryId == null,
                        onClick = { onCategorySelected(null) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Others")
                }

                if (categories.isNotEmpty()) {
                    HorizontalDivider()
                }

                // Category options
                categories.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCategorySelected(category.id) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentCategoryId == category.id,
                            onClick = { onCategorySelected(category.id) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(category.name)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SwipeableNoteItem(
    note: Note,
    settings: SettingsData,
    onNoteClick: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onLongClick: () -> Unit = {},
    onPinToggle: () -> Unit = {},
    isExpanded: Boolean = false,
    isCollaborative: Boolean = false,
    collaboratorCount: Int = 0
) {
    var isContentExpanded by remember(note.id) { mutableStateOf(false) }

    // Get note color and calculate appropriate text color
    val noteColor = NoteColorPalette.getColorByIndex(note.colorIndex)
    val textColor = getTextColorForBackground(noteColor)

    // Content preview with image filtering
    val contentPreview = remember(note.content, note.type) {
        if (note.content.isBlank()) {
            ""
        } else {
            try {
                when (note.type) {
                    NoteType.CHECKLIST -> {
                        val noteLines = note.content.toNoteLines()
                        val activeChecklistItems = noteLines.filter {
                            it.type == LineType.CHECKLIST &&
                                    !it.isChecked &&
                                    it.content.isNotBlank()
                        }

                        if (activeChecklistItems.isNotEmpty()) {
                            activeChecklistItems.joinToString("\n") { line ->
                                "○ ${line.content}"
                            }
                        } else {
                            // If no active items, show completed ones
                            val completedItems = noteLines.filter {
                                it.type == LineType.CHECKLIST &&
                                        it.isChecked &&
                                        it.content.isNotBlank()
                            }
                            completedItems.joinToString("\n") { line ->
                                "✓ ${line.content}"
                            }
                        }
                    }
                    NoteType.TEXT -> {
                        val noteLines = note.content.toNoteLines()
                        noteLines
                            .filter { line ->
                                val content = line.content.lowercase()
                                !content.contains(".jpg") &&
                                        !content.contains(".jpeg") &&
                                        !content.contains(".png") &&
                                        !content.contains(".gif") &&
                                        !content.contains(".bmp") &&
                                        !content.contains(".webp") &&
                                        !content.contains("note_image") &&
                                        !content.contains("/data/user/") &&
                                        !content.contains("/files/") &&
                                        !content.startsWith("file://") &&
                                        !content.startsWith("content://")
                            }
                            .map { line ->
                                when (line.type) {
                                    LineType.TEXT -> line.content
                                    LineType.CHECKLIST -> {
                                        if (line.isChecked) "✓ ${line.content}" else "○ ${line.content}"
                                    }
                                    LineType.BULLET -> "• ${line.content}"
                                    else -> line.content
                                }
                            }
                            .filter { it.isNotBlank() }
                            .joinToString("\n")
                    }
                }
            } catch (e: Exception) {
                note.content.split("\n").filter { it.isNotBlank() }.joinToString("\n")
            }
        }
    }

    // Check if content has more than 3 lines
    val contentLines = contentPreview.split("\n")
    val hasMoreContent = contentLines.size > 3
    val displayContent = if (isContentExpanded || !hasMoreContent) {
        contentPreview
    } else {
        contentLines.take(3).joinToString("\n")
    }

    // Simple date formatting
    val formattedDate = remember(note.lastModifiedAt) {
        val date = Date(note.lastModifiedAt ?: note.createdAt)
        SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(date)
    }

    // For collaborative notes or when swipe is disabled
    if (!settings.noteSwipeEnabled || isCollaborative) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNoteClick() }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onLongClick() }
                    )
                },
            colors = CardDefaults.cardColors(
                containerColor = noteColor
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header with title and icons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = note.title.ifEmpty { "Untitled" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor, // ✅ Fixed text color
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isCollaborative) {
                            Icon(
                                Icons.Default.CloudSync,
                                contentDescription = "Collaborative",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (note.isPinned && !isCollaborative) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Content preview
                if (displayContent.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = displayContent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor.copy(alpha = 0.7f), // ✅ Fixed text color
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                    )

                    // Show More/Less button
                    if (hasMoreContent) {
                        TextButton(
                            onClick = { isContentExpanded = !isContentExpanded },
                            modifier = Modifier.padding(top = 4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isContentExpanded) "Show Less" else "Show More",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = if (isContentExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(start = 4.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Footer with date and collaborator info
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.5f) // ✅ Fixed text color
                    )

                    // Collaborative info
                    if (isCollaborative && collaboratorCount > 0) {
                        Text(
                            text = "$collaboratorCount collaborators",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
        return
    }

    // Swipe implementation for regular notes
    var lastSwipeDirection by remember { mutableStateOf<DismissDirection?>(null) }
    var swipeStartTime by remember { mutableStateOf(0L) }
    var hasReachedThreshold by remember { mutableStateOf(false) }

    val dismissState = rememberDismissState(
        confirmStateChange = { dismissValue ->
            if (!hasReachedThreshold) {
                return@rememberDismissState false
            }

            val currentTime = System.currentTimeMillis()
            val swipeDuration = currentTime - swipeStartTime

            if (swipeDuration < 150L) {
                return@rememberDismissState false
            }

            when (dismissValue) {
                DismissValue.DismissedToStart -> {
                    when (settings.swipeLeftAction) {
                        SwipeAction.DELETE -> { onDelete(); true }
                        SwipeAction.ARCHIVE -> { onArchive(); true }
                        SwipeAction.NONE -> false
                    }
                }
                DismissValue.DismissedToEnd -> {
                    when (settings.swipeRightAction) {
                        SwipeAction.DELETE -> { onDelete(); true }
                        SwipeAction.ARCHIVE -> { onArchive(); true }
                        SwipeAction.NONE -> false
                    }
                }
                else -> false
            }
        }
    )

    LaunchedEffect(dismissState) {
        snapshotFlow {
            Triple(dismissState.offset.value, dismissState.progress.fraction, dismissState.targetValue)
        }.collect { (offset, progress, _) ->
            val currentDirection = when {
                offset > 50f -> DismissDirection.StartToEnd
                offset < -50f -> DismissDirection.EndToStart
                else -> null
            }

            if (currentDirection != lastSwipeDirection) {
                lastSwipeDirection = currentDirection
                swipeStartTime = System.currentTimeMillis()
                hasReachedThreshold = false
            }

            if (currentDirection != null && progress >= 0.4f) {
                hasReachedThreshold = true
            }

            if (offset == 0f) {
                lastSwipeDirection = null
                hasReachedThreshold = false
            }
        }
    }

    val enabledDirections = mutableSetOf<DismissDirection>()
    if (settings.swipeLeftAction != SwipeAction.NONE) {
        enabledDirections.add(DismissDirection.EndToStart)
    }
    if (settings.swipeRightAction != SwipeAction.NONE) {
        enabledDirections.add(DismissDirection.StartToEnd)
    }

    SwipeToDismiss(
        state = dismissState,
        directions = enabledDirections,
        dismissThresholds = { _ -> FractionalThreshold(0.4f) },
        background = {
            EnhancedSwipeBackground(
                dismissState = dismissState,
                leftAction = settings.swipeLeftAction,
                rightAction = settings.swipeRightAction,
                hasReachedThreshold = hasReachedThreshold
            )
        },
        dismissContent = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = noteColor
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(note.id) {
                            detectTapGestures(
                                onTap = { onNoteClick() },
                                onLongPress = { onLongClick() }
                            )
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Header with title and pin icon
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = note.title.ifEmpty { "Untitled" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = textColor, // ✅ Fixed text color
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (note.isPinned) {
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = "Pinned",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Content preview
                        if (displayContent.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = displayContent,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor.copy(alpha = 0.7f), // ✅ Fixed text color
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                            )

                            // Show More/Less button
                            if (hasMoreContent) {
                                TextButton(
                                    onClick = { isContentExpanded = !isContentExpanded },
                                    modifier = Modifier.padding(top = 4.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isContentExpanded) "Show Less" else "Show More",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = if (isContentExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .padding(start = 4.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // Date
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.5f) // ✅ Fixed text color
                        )
                    }
                }
            }
        }
    )
}


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun EnhancedSwipeBackground(
    dismissState: DismissState,
    leftAction: SwipeAction,
    rightAction: SwipeAction,
    hasReachedThreshold: Boolean = false
) {
    val isSwipingLeft = dismissState.offset.value < -50f
    val isSwipingRight = dismissState.offset.value > 50f

    val currentAction = when {
        isSwipingLeft -> leftAction
        isSwipingRight -> rightAction
        else -> SwipeAction.NONE
    }

    val progress = dismissState.progress.fraction
    val threshold = 0.4f
    val isReady = hasReachedThreshold && progress >= threshold

    val color = when (currentAction) {
        SwipeAction.DELETE -> {
            if (isReady) {
                ErrorRed.copy(alpha = 0.9f)
            } else if (hasReachedThreshold) {
                ErrorRed.copy(alpha = 0.7f)
            } else {
                ErrorRed.copy(alpha = (progress * 1.2f).coerceIn(0f, 0.4f))
            }
        }
        SwipeAction.ARCHIVE -> {
            if (isReady) {
                Color(0xFF4CAF50).copy(alpha = 0.9f)
            } else if (hasReachedThreshold) {
                Color(0xFF4CAF50).copy(alpha = 0.7f)
            } else {
                Color(0xFF4CAF50).copy(alpha = (progress * 1.2f).coerceIn(0f, 0.4f))
            }
        }
        SwipeAction.NONE -> Color.Transparent
    }

    val icon = when (currentAction) {
        SwipeAction.DELETE -> Icons.Default.Delete
        SwipeAction.ARCHIVE -> Icons.Default.Archive
        SwipeAction.NONE -> null
    }

    val alignment = when {
        isSwipingLeft -> Alignment.CenterEnd
        isSwipingRight -> Alignment.CenterStart
        else -> Alignment.Center
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color, shape = MaterialTheme.shapes.medium)
            .padding(horizontal = 20.dp),
        contentAlignment = alignment
    ) {
        if (icon != null && (isSwipingLeft || isSwipingRight)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = currentAction.name,
                    tint = Color.White,
                    modifier = Modifier
                        .size(
                            when {
                                isReady -> 40.dp
                                hasReachedThreshold -> 32.dp
                                else -> 24.dp
                            }
                        )
                        .alpha(
                            when {
                                isReady -> 1f
                                hasReachedThreshold -> 0.8f
                                else -> 0.6f
                            }
                        )
                )

                if (progress > 0.1f) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val actionText = when {
                        isReady -> "Release to ${currentAction.name.lowercase()}"
                        hasReachedThreshold -> "Almost there!"
                        progress >= 0.3f -> "Keep swiping..."
                        else -> "Swipe to ${currentAction.name.lowercase()}"
                    }
                    Text(
                        text = actionText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = if (isReady) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.alpha(
                            when {
                                isReady -> 1f
                                hasReachedThreshold -> 0.9f
                                else -> 0.7f
                            }
                        )
                    )
                }
            }
        }
    }
}
