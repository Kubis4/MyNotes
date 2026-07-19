package sk.kubdev.mynotes.ui.screens

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
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import sk.kubdev.mynotes.NoteViewModel
import sk.kubdev.mynotes.R
import sk.kubdev.mynotes.normalizeForSearch
import sk.kubdev.mynotes.data.remote.local.entities.Category
import sk.kubdev.mynotes.data.remote.local.entities.Note
import sk.kubdev.mynotes.data.remote.local.entities.NoteType
import sk.kubdev.mynotes.data.remote.models.toNoteLines
import sk.kubdev.mynotes.settings.SettingsViewModel
import sk.kubdev.mynotes.settings.model.SettingsData
import sk.kubdev.mynotes.settings.model.SwipeAction
import sk.kubdev.mynotes.toPlainTextPreview
import sk.kubdev.mynotes.ui.components.ColorPickerDialog
import sk.kubdev.mynotes.ui.components.GradientTopAppBar
import sk.kubdev.mynotes.ui.theme.notePattern
import sk.kubdev.mynotes.ui.theme.ErrorRed
import sk.kubdev.mynotes.ui.theme.NoteColorPalette
import sk.kubdev.mynotes.data.remote.models.CollaborativeNote
import androidx.compose.foundation.gestures.detectTapGestures
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import sk.kubdev.mynotes.LineType
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
    val collaboratorCount: Int,
    val isOwner: Boolean = false
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
                lastModifiedAt = note.lastModifiedAt,
                isArchived = note.isArchived,
                isDeleted = note.isDeleted,
                orderIndex = note.orderIndex,
                isCollaborative = false,
                collaborativeId = null,
                collaboratorCount = 0,
                isOwner = false
            )
        }

        fun fromCollaborativeNote(
            note: CollaborativeNote,
            currentUserId: String?,
            // Personal (device-local) color for this shared note - each collaborator
            // can pick their own without affecting the others (CollabLocalPrefs).
            colorIndex: Int = 6
        ): UnifiedNote {
            return UnifiedNote(
                id = note.id,
                title = note.title,
                content = note.content,
                type = when (note.type.uppercase()) {
                    "TEXT" -> NoteType.TEXT
                    "CHECKLIST" -> NoteType.CHECKLIST
                    else -> NoteType.CHECKLIST
                },
                colorIndex = colorIndex,
                isPinned = false,
                categoryId = null,
                lastModifiedAt = note.updatedAt,
                isArchived = false,
                isDeleted = false,
                orderIndex = 0,
                isCollaborative = true,
                collaborativeId = note.id,
                collaboratorCount = note.collaborators.size,
                isOwner = note.ownerId == currentUserId
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
            orderIndex = orderIndex,
            collaborativeNoteId = collaborativeId
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
    val isUserSignedIn = viewModel.isUserSignedIn()
    val currentUserId = remember(isUserSignedIn) { viewModel.getCurrentUserId() }

    // Filter collaborative notes
    val collaborativeNotes = remember(rawCollaborativeNotes) {
        rawCollaborativeNotes.filter { note ->
            note.id.isNotBlank() &&
                    note.title.isNotBlank() &&
                    note.collaborators.isNotEmpty()
        }
    }

    // String constants
    val sectionPinned = stringResource(R.string.section_pinned)
    val sectionOthers = stringResource(R.string.section_others)
    val sectionShared = stringResource(R.string.section_shared_notes)
    val fabTodoList = stringResource(R.string.fab_todo_list)
    val fabNote = stringResource(R.string.fab_note)
    val fabNewCategory = stringResource(R.string.fab_new_category)
    val fabCollabTodo = stringResource(R.string.collaborative_todo_label)
    val fabCollabNote = stringResource(R.string.collaborative_note_label)

    // State variables
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
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

    // Save preferences
    LaunchedEffect(currentLayout) {
        prefs.edit().putInt("layout_type", currentLayout.ordinal).apply()
    }

    LaunchedEffect(currentSort) {
        prefs.edit().putInt("sort_type", currentSort.ordinal).apply()
    }

    // Bumped after changing a collaborative note's personal color so the list
    // rebuilds (SharedPreferences aren't observable by themselves).
    var collabColorVersion by remember { mutableIntStateOf(0) }

    // Unified notes creation
    val allUnifiedNotes = remember(notes, collaborativeNotes, collabColorVersion) {
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

            val collabNotes = collaborativeNotes.map {
                UnifiedNote.fromCollaborativeNote(
                    it,
                    currentUserId,
                    colorIndex = sk.kubdev.mynotes.CollabLocalPrefs.getColorIndex(context, it.id)
                )
            }

            (regularNotes + collabNotes).sortedByDescending { it.lastModifiedAt }
        } catch (e: Exception) {
            Log.e("NoteListScreen", "Error creating unified notes", e)
            notes.map { UnifiedNote.fromNote(it) }
        }
    }

    // O(1) lookup tables for matching a rendered Note back to its UnifiedNote,
    // built once per allUnifiedNotes change instead of linearly scanning the
    // whole list for every visible note item (that scan was O(notes^2) overall).
    val unifiedByLocalId = remember(allUnifiedNotes) {
        allUnifiedNotes.filter { !it.isCollaborative }.associateBy { it.id }
    }
    val unifiedByCollaborativeId = remember(allUnifiedNotes) {
        allUnifiedNotes.filter { it.isCollaborative }.associateBy { it.collaborativeId }
    }
    val unifiedByCollaborativeTitle = remember(allUnifiedNotes) {
        allUnifiedNotes.filter { it.isCollaborative }.associateBy { it.title }
    }
    fun findUnifiedNote(note: Note): UnifiedNote? = when {
        note.collaborativeNoteId != null -> unifiedByCollaborativeId[note.collaborativeNoteId]
        note.id < 0 -> unifiedByCollaborativeTitle[note.title]
        else -> unifiedByLocalId[note.id.toString()]
    }

    // Create sections from unified notes
    val noteSections = remember(allUnifiedNotes, categories, currentFilter, selectedColorFilter, currentSort, sectionPinned, sectionOthers, sectionShared, searchQuery) {
        try {
            // Filter unified notes
            val typeAndColorFiltered = when (currentFilter) {
                NoteFilterType.ALL -> allUnifiedNotes
                NoteFilterType.TEXT_ONLY -> allUnifiedNotes.filter { it.type == NoteType.TEXT }
                NoteFilterType.CHECKLIST_ONLY -> allUnifiedNotes.filter { it.type == NoteType.CHECKLIST }
                NoteFilterType.BY_COLOR -> {
                    selectedColorFilter?.let { colorIndex ->
                        allUnifiedNotes.filter { it.colorIndex == colorIndex }
                    } ?: allUnifiedNotes
                }
            }

            // Regular notes are already search-filtered at the DB layer (see
            // NoteViewModel.allNotes), but collaborative notes come from a
            // separate, unfiltered Firestore stream - without this they show up
            // regardless of the search box content. Filtering here uniformly
            // covers both.
            val filtered = if (searchQuery.isBlank()) {
                typeAndColorFiltered
            } else {
                // Diacritic-insensitive, matching NoteViewModel.allNotes's local search.
                val normalizedQuery = searchQuery.normalizeForSearch()
                typeAndColorFiltered.filter {
                    it.title.normalizeForSearch().contains(normalizedQuery) ||
                            it.content.normalizeForSearch().contains(normalizedQuery)
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
                sections.add(NoteSection("collaborative", sectionShared, sortedCollab.map { it.toNote() }))
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
    val fabItems = remember(isUserSignedIn, fabTodoList, fabNote, fabNewCategory, fabCollabTodo, fabCollabNote) {
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
                    label = fabCollabTodo,
                    onClick = {
                        isFabExpanded = false
                        // ✅ FIXED: Navigate to new collaborative todo
                        navController.navigate("collaborative_todo/new")
                    }
                ))
                add(FabButtonItem(
                    icon = Icons.Default.Groups,
                    label = fabCollabNote,
                    onClick = {
                        isFabExpanded = false
                        navController.navigate("collaborative_todo/new_note")
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
                        message = if (event.count > 1) context.getString(R.string.notes_deleted, event.count)
                        else context.getString(R.string.note_deleted),
                        actionLabel = context.getString(R.string.undo),
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoDelete()
                    }
                }
                is NoteViewModel.UiEvent.ShowUndoArchiveSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.note_archived),
                        actionLabel = context.getString(R.string.undo),
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoArchive()
                    }
                }
                // Plain ShowMessage/ShowError have no undo to offer, so no snackbar -
                // only the Undo* events above get one.
                is NoteViewModel.UiEvent.ShowMessage -> Unit
                is NoteViewModel.UiEvent.ShowError -> Unit
            }
        }
    }

    // Pop up a snackbar for newly-arrived invites while the user is in the app,
    // instead of only surfacing them silently as the toolbar badge. Only fires for
    // invites that show up mid-session (not the initial batch loaded on sign-in/launch),
    // otherwise every app open with pre-existing invites would spam a snackbar.
    var seenInviteIds by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(pendingInvites) {
        val previouslySeen = seenInviteIds
        val currentIds = pendingInvites.map { it.id }.toSet()
        if (previouslySeen != null) {
            val newInvite = pendingInvites.firstOrNull { it.id !in previouslySeen }
            if (newInvite != null) {
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(
                        R.string.collab_new_invite,
                        newInvite.senderDisplayName ?: newInvite.senderEmail ?: "",
                        newInvite.noteTitle.ifEmpty { context.getString(R.string.untitled_note) }
                    ),
                    actionLabel = context.getString(R.string.action_view),
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    navController.navigate("pending_invitations")
                }
            }
        }
        seenInviteIds = currentIds
    }

    // Color picker dialog
    if (showColorPicker && selectedNoteForColor != null) {
        ColorPickerDialog(
            currentColorIndex = selectedNoteForColor!!.colorIndex,
            onColorSelected = { colorIndex ->
                val target = selectedNoteForColor!!
                val collabId = target.collaborativeNoteId
                if (collabId != null) {
                    // Shared note: the color is a personal, device-local choice -
                    // writing it to the DB row would do nothing (the row is a
                    // synthetic mapping of the Firestore doc), so store it in prefs
                    // and nudge the list to rebuild.
                    sk.kubdev.mynotes.CollabLocalPrefs.setColorIndex(context, collabId, colorIndex)
                    collabColorVersion++
                } else {
                    viewModel.updateNoteColor(target, colorIndex)
                }
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
                navController.navigate("sign_in")
            }
        )
    }

    // Bottom sheet handling
    if (selectedNoteForOptions != null) {
        val currentNote by remember(selectedNoteForOptions) {
            if (selectedNoteForOptions!!.collaborativeNoteId != null) {
                // Collaborative notes have a synthetic id with no matching Room row.
                // Querying Room for it would emit null and dismiss the sheet before
                // the user can act, so keep the selected snapshot instead.
                flowOf(selectedNoteForOptions)
            } else {
                viewModel.getNoteById(selectedNoteForOptions!!.id)
            }
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
                                if (selectedUnifiedNote.isOwner) {
                                    Log.d("NoteListScreen", "Deleting collaborative note as owner: $collabId")
                                    viewModel.deleteCollaborativeNote(collabId)
                                } else {
                                    Log.d("NoteListScreen", "Leaving collaborative note: $collabId")
                                    viewModel.leaveCollaborativeNote(collabId)
                                }
                            }
                        } else {
                            viewModel.triggerDelete(currentNote!!)
                        }
                        selectedNoteForOptions = null
                    },
                    onTitleChange = { newTitle ->
                        viewModel.updateNoteTitle(currentNote!!, newTitle)
                    },
                    isCollaborative = selectedUnifiedNote?.isCollaborative ?: false,
                    isOwner = selectedUnifiedNote?.isOwner ?: false
                )
            }
        }
    }

    // Main UI
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            GradientTopAppBar(
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
                                text = stringResource(R.string.search_notes_hint),
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
                                        // onPrimary like every other bar icon (was secondary,
                                        // which stood out as a different color); the badge
                                        // already signals the pending invites.
                                        tint = MaterialTheme.colorScheme.onPrimary
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
                                text = { Text(stringResource(R.string.filter_all)) },
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
                                text = { Text(stringResource(R.string.filter_text_only)) },
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
                                text = { Text(stringResource(R.string.filter_checklist_only)) },
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
                                text = { Text(stringResource(R.string.sort_newest)) },
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
                                text = { Text(stringResource(R.string.sort_oldest)) },
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
                                text = { Text(stringResource(R.string.sort_az)) },
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
                                text = { Text(stringResource(R.string.sort_za)) },
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
                                NoteLayoutType.GRID -> Icons.AutoMirrored.Filled.ViewList
                            },
                            contentDescription = "Change layout"
                        )
                    }
                }
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
                            onClick = { navController.navigate("sign_in") }
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.sign_in_to_collaborate))
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
                                                text = stringResource(R.string.notes_none_in_category),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                } else {
                                    items(
                                        items = section.notes,
                                        key = { note -> "${section.id}_note_${note.id}" }
                                    ) { note ->
                                        val unifiedNote = remember(note, unifiedByLocalId, unifiedByCollaborativeId, unifiedByCollaborativeTitle) {
                                            findUnifiedNote(note)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .animateItemPlacement()
                                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            SwipeableNoteItem(
                                                note = note,
                                                settings = settings,
                                                onNoteClick = {
                                                    // Use note.collaborativeNoteId directly instead of re-matching
                                                    // against allUnifiedNotes - that match can fail (e.g. title
                                                    // changed since last sync) and silently open the wrong screen.
                                                    val collaborativeId = note.collaborativeNoteId
                                                    if (collaborativeId != null) {
                                                        Log.d("NoteListScreen", "Navigating to collaborative note: $collaborativeId")
                                                        navController.navigate("collaborative_todo/$collaborativeId")
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
                                                // Same fix as onNoteClick above: derive from the note's own
                                                // field, not the unifiedNote re-match, which can be stale/miss.
                                                isCollaborative = note.collaborativeNoteId != null,
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
                                                text = stringResource(R.string.notes_none_in_category),
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
                                                    val unifiedNote = remember(note, unifiedByLocalId, unifiedByCollaborativeId, unifiedByCollaborativeTitle) {
                                                        findUnifiedNote(note)
                                                    }

                                                    Box(
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        GridNoteItem(
                                                            note = note,
                                                            onNoteClick = {
                                                                // Use note.collaborativeNoteId directly - see List layout for why.
                                                                val collaborativeId = note.collaborativeNoteId
                                                                if (collaborativeId != null) {
                                                                    navController.navigate("collaborative_todo/$collaborativeId")
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
                                                            isCollaborative = note.collaborativeNoteId != null,
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
    showCategoryMenu?.let { (_, note) ->
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

    // Parse the JSON content once and reuse it for both the progress count and the preview
    // (previously each was parsed independently, doubling JSON deserialization per list item).
    val parsedLines = remember(note.content) { note.content.toNoteLines() }

    // Calculate task progress for checklists
    val taskProgress = remember(parsedLines, note.type) {
        if (note.type == NoteType.CHECKLIST) {
            val checklistItems = parsedLines.filter { it.type == LineType.CHECKLIST }
            val completedItems = checklistItems.filter { it.isChecked }
            if (checklistItems.isNotEmpty()) {
                completedItems.size to checklistItems.size
            } else {
                0 to 0
            }
        } else {
            0 to 0
        }
    }

    // Content preview with image filtering
    val contentPreview = remember(parsedLines, note.type) {
        if (note.content.isBlank()) {
            ""
        } else {
            try {
                when (note.type) {
                    NoteType.CHECKLIST -> {
                        val activeChecklistItems = parsedLines.filter {
                            it.type == LineType.CHECKLIST &&
                                    !it.isChecked &&
                                    it.content.isNotBlank()
                        }

                        if (activeChecklistItems.isNotEmpty()) {
                            activeChecklistItems.joinToString("\n") { line ->
                                "○ ${line.content}"
                            }
                        } else {
                            val completedItems = parsedLines.filter {
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
                        parsedLines
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

    // Whole grid card carries the note color now (Keep-style) instead of a tiny dot.
    val cardColor = NoteColorPalette.getCardBackground(
        note.colorIndex,
        isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .combinedClickable(
                onClick = onNoteClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp)
                .notePattern(note.patternIndex, MaterialTheme.colorScheme.onSurface)
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
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = note.title.ifEmpty { "Untitled" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = if (isContentExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

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
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                        Text(
                            text = "${taskProgress.first}/${taskProgress.second}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
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
                                text = if (isContentExpanded) stringResource(R.string.show_less) else stringResource(R.string.show_more),
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
                    val date = Date(note.lastModifiedAt)
                    SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(date)
                }

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )

                if (isCollaborative && collaboratorCount > 0) {
                    Text(
                        text = stringResource(R.string.collaborators_count, collaboratorCount),
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
        title = { Text(stringResource(R.string.no_invitations_title)) },
        text = {
            Text(stringResource(R.string.no_invitations_message))
        },
        confirmButton = {
            Button(onClick = onSignIn) {
                Text(stringResource(R.string.sign_in_title))
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
fun NoteOptionsBottomSheet(
    note: Note,
    categories: List<Category>,
    onPinToggle: () -> Unit,
    onColorChange: () -> Unit,
    onCategoryChange: (Int?) -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onTitleChange: (String) -> Unit,
    isCollaborative: Boolean = false,
    isOwner: Boolean = false
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
                    placeholder = { Text(stringResource(R.string.note_title_hint)) },
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
                    text = note.title.ifEmpty { stringResource(R.string.untitled_note) },
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

        if (!isCollaborative) {
            // Regular note options
            ListItem(
                headlineContent = {
                    Text(
                        if (note.isPinned) stringResource(R.string.action_unpin)
                        else stringResource(R.string.action_pin)
                    )
                },
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
                headlineContent = { Text(stringResource(R.string.move_to_category)) },
                supportingContent = {
                    val currentCategory = categories.find { it.id == note.categoryId }
                    Text(currentCategory?.name ?: stringResource(R.string.section_others))
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
                headlineContent = { Text(stringResource(R.string.change_color)) },
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
                headlineContent = { Text(stringResource(R.string.swipe_action_archive)) },
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
                    text = when {
                        isCollaborative && isOwner -> stringResource(R.string.collab_delete_option)
                        isCollaborative -> stringResource(R.string.collab_leave_option)
                        else -> stringResource(R.string.action_delete)
                    },
                    color = MaterialTheme.colorScheme.error
                )
            },
            leadingContent = {
                Icon(
                    imageVector = if (isCollaborative && !isOwner) Icons.AutoMirrored.Filled.ExitToApp else Icons.Default.Delete,
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
                    text = when {
                        isCollaborative && isOwner -> stringResource(R.string.dialog_delete_collab_title)
                        isCollaborative -> stringResource(R.string.dialog_leave_collab_title)
                        else -> stringResource(R.string.dialog_delete_note_title)
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val displayTitle = note.title.ifEmpty { stringResource(R.string.untitled_note) }
                Text(
                    text = when {
                        isCollaborative && isOwner -> stringResource(R.string.dialog_delete_collab_message, displayTitle)
                        isCollaborative -> stringResource(R.string.dialog_leave_collab_message, displayTitle)
                        else -> stringResource(R.string.dialog_delete_note_message, displayTitle)
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
                        text = when {
                            isCollaborative && isOwner -> stringResource(R.string.delete_everywhere)
                            isCollaborative -> stringResource(R.string.leave_note)
                            else -> stringResource(R.string.action_delete)
                        },
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel))
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
                            text = { Text(stringResource(R.string.action_edit)) },
                            onClick = {
                                onEdit()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
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

                        // Small FAB in the brand color, matching the header
                        sk.kubdev.mynotes.ui.components.BrandSmallFloatingActionButton(
                            onClick = item.onClick
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

        // Main FAB in the brand color, matching the header
        sk.kubdev.mynotes.ui.components.BrandFloatingActionButton(
            onClick = onFabClick
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = if (isExpanded) "Close" else "Add",
                modifier = Modifier.rotate(rotation)
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
        title = {
            Text(
                if (category == null) stringResource(R.string.fab_new_category)
                else stringResource(R.string.edit_category)
            )
        },
        text = {
            OutlinedTextField(
                value = categoryName,
                onValueChange = { categoryName = it },
                label = { Text(stringResource(R.string.category_name_label)) },
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
                Text(stringResource(R.string.action_save))
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
fun CategorySelectionDialog(
    currentCategoryId: Int?,
    categories: List<Category>,
    onCategorySelected: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_to_category)) },
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
                    Text(stringResource(R.string.section_others))
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
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

// NOTE: Uses the (deprecated but fully functional) Material 2 Swipeable/SwipeToDismiss
// API for the custom animated swipe-to-delete/archive background. A migration to the
// Material 3 SwipeToDismissBox / Foundation AnchoredDraggable APIs is recommended, but
// should be validated with real on-device gesture testing before replacing this core UX.
@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun SwipeableNoteItem(
    note: Note,
    settings: SettingsData,
    onNoteClick: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onLongClick: () -> Unit = {},
    onPinToggle: () -> Unit = {},
    isCollaborative: Boolean = false,
    collaboratorCount: Int = 0
) {
    var isContentExpanded by remember(note.id) { mutableStateOf(false) }

    // The note's color now paints the WHOLE row (Keep-style), not just a dot -
    // pastel directly in the light theme, blended toward dark in the dark theme
    // so onSurface text stays readable in both. Dark-mode detection reads the
    // actual resolved theme (works for the in-app Light/Dark/System setting, not
    // just the OS toggle).
    val cardColor = NoteColorPalette.getCardBackground(
        note.colorIndex,
        isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    )

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
        val date = Date(note.lastModifiedAt)
        SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(date)
    }

    // For collaborative notes or when swipe is disabled
    if (!settings.noteSwipeEnabled || isCollaborative) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(cardColor)
                .notePattern(note.patternIndex, MaterialTheme.colorScheme.onSurface)
                // combinedClickable (not a separate clickable + pointerInput/detectTapGestures)
                // so the tap and long-press gestures don't fight over the same pointer events -
                // that conflict was swallowing taps on collaborative notes in list view.
                .combinedClickable(onClick = onNoteClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header with title and icons (the whole card carries the note color now,
            // so the old color dot is gone - it was invisible against the same-colored
            // background anyway)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = note.title.ifEmpty { "Untitled" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

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
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = displayContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
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
                            text = if (isContentExpanded) stringResource(R.string.show_less) else stringResource(R.string.show_more),
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
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                // Collaborative info
                if (isCollaborative && collaboratorCount > 0) {
                    Text(
                        text = stringResource(R.string.collaborators_count, collaboratorCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
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
        dismissThresholds = { _ -> androidx.compose.material.FractionalThreshold(0.4f) },
        background = {
            EnhancedSwipeBackground(
                dismissState = dismissState,
                leftAction = settings.swipeLeftAction,
                rightAction = settings.swipeRightAction,
                hasReachedThreshold = hasReachedThreshold
            )
        },
        dismissContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(cardColor)
                    .notePattern(note.patternIndex, MaterialTheme.colorScheme.onSurface)
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
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column {
                        // Header with title and pin icon (whole card carries the color)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = note.title.ifEmpty { "Untitled" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

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
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    }
                                )
                            }
                        }

                        // Content preview
                        if (displayContent.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = displayContent,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
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
                                        text = if (isContentExpanded) stringResource(R.string.show_less) else stringResource(R.string.show_more),
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
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    )
}


@Suppress("DEPRECATION")
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
