package sk.kubisdev.mynotes.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import coil.compose.AsyncImage
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.text.style.TextDirection
import com.google.firebase.auth.FirebaseAuth
import sk.kubisdev.mynotes.CollaborationDialog
import sk.kubisdev.mynotes.CollaboratorsDialog
import sk.kubisdev.mynotes.FormattingToolbar
import sk.kubisdev.mynotes.InviteManagementDialog
import sk.kubisdev.mynotes.LineType
import sk.kubisdev.mynotes.NoteLine
import sk.kubisdev.mynotes.NoteViewModel
import sk.kubisdev.mynotes.R
import sk.kubisdev.mynotes.SerializableSpanStyle
import sk.kubisdev.mynotes.data.remote.local.entities.NoteType
import sk.kubisdev.mynotes.RichTextField
import sk.kubisdev.mynotes.RichTextFieldController
import sk.kubisdev.mynotes.toAnnotatedString
import sk.kubisdev.mynotes.replaceAllSmart
import sk.kubisdev.mynotes.toJson
import sk.kubisdev.mynotes.toNoteLines
import sk.kubisdev.mynotes.data.remote.models.CollaboratorInfo
import sk.kubisdev.mynotes.data.remote.models.CollaboratorRole
import sk.kubisdev.mynotes.data.remote.models.InviteStatus
import sk.kubisdev.mynotes.ui.components.GradientTopAppBar
import sk.kubisdev.mynotes.ui.theme.NoteColorPalette
import sk.kubisdev.mynotes.ui.theme.notePattern
import androidx.compose.ui.graphics.luminance

// Helper function to merge adjacent spans with identical properties
fun mergeAdjacentSpans(spans: List<SerializableSpanStyle>): List<SerializableSpanStyle> {
    if (spans.isEmpty()) return spans

    val sorted = spans.sortedBy { it.start }
    val merged = mutableListOf<SerializableSpanStyle>()

    sorted.forEach { span ->
        val lastSpan = merged.lastOrNull()
        if (lastSpan != null &&
            lastSpan.end >= span.start && // Adjacent or overlapping
            lastSpan.fontWeight == span.fontWeight &&
            lastSpan.fontStyle == span.fontStyle &&
            lastSpan.textDecoration == span.textDecoration &&
            lastSpan.color == span.color &&
            lastSpan.fontSize == span.fontSize) {
            // Merge with the last span
            merged[merged.lastIndex] = lastSpan.copy(end = maxOf(lastSpan.end, span.end))
        } else {
            merged.add(span)
        }
    }

    return merged
}

// Helper function to clean up overlapping spans
fun cleanupOverlappingSpans(spans: List<SerializableSpanStyle>): List<SerializableSpanStyle> {
    // Group spans by type
    val colorSpans = spans.filter { it.color != null }.sortedBy { it.start }
    val fontSizeSpans = spans.filter { it.fontSize != null }.sortedBy { it.start }
    val boldSpans = spans.filter { it.fontWeight != null }.sortedBy { it.start }
    val italicSpans = spans.filter { it.fontStyle != null }.sortedBy { it.start }
    val underlineSpans = spans.filter { it.textDecoration != null }.sortedBy { it.start }

    val result = mutableListOf<SerializableSpanStyle>()

    // Overlap resolution shared by any span kind where a NEWER span should win outright
    // over an older one for the overlapping range (color, size) rather than just merge.
    fun resolveOverlaps(sorted: List<SerializableSpanStyle>): List<SerializableSpanStyle> {
        val cleaned = mutableListOf<SerializableSpanStyle>()
        sorted.forEach { newSpan ->
            val overlapping = cleaned.filter { existing ->
                (newSpan.start < existing.end && newSpan.end > existing.start)
            }

            if (overlapping.isEmpty()) {
                cleaned.add(newSpan)
            } else {
                // Remove overlapping spans and add adjusted versions
                cleaned.removeAll(overlapping)

                overlapping.forEach { existing ->
                    // Adjust existing span to not overlap with new span
                    if (existing.start < newSpan.start) {
                        cleaned.add(existing.copy(end = newSpan.start))
                    }
                    if (existing.end > newSpan.end) {
                        cleaned.add(existing.copy(start = newSpan.end))
                    }
                }

                cleaned.add(newSpan)
            }
        }
        return cleaned
    }

    result.addAll(resolveOverlaps(colorSpans))
    result.addAll(resolveOverlaps(fontSizeSpans))
    result.addAll(mergeAdjacentSpans(boldSpans))
    result.addAll(mergeAdjacentSpans(italicSpans))
    result.addAll(mergeAdjacentSpans(underlineSpans))

    return result.sortedBy { it.start }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ToDoListScreen(
    noteIdArg: Int,
    navController: NavController,
    viewModel: NoteViewModel = hiltViewModel(),
    // Collaboration parameters
    isCollaborative: Boolean = false,
    collaborativeNoteId: String? = null
) {
    var noteId by remember { mutableStateOf(noteIdArg) }
    var title by remember { mutableStateOf("") }
    var isOwner by remember { mutableStateOf(false) }
    // Local notes tint the editor background with their note color (same palette
    // entry as their card in the list); collaborative notes have no colorIndex.
    var noteColorIndex by remember { mutableStateOf(-1) }
    var notePatternIndex by remember { mutableStateOf(0) }
    // Room entity of the loaded LOCAL note - needed to persist color/pattern picks
    // (collaborative notes store them device-locally via CollabLocalPrefs instead).
    var loadedNote by remember { mutableStateOf<sk.kubisdev.mynotes.data.remote.local.entities.Note?>(null) }

    // Simple list of lines
    val lines = remember { mutableStateListOf<NoteLine>() }

    // Collaboration states
    var showCollaborationDialog by remember { mutableStateOf(false) }
    var showCollabColorPicker by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var showCollaboratorsDialog by remember { mutableStateOf(false) }
    var collaborators by remember { mutableStateOf<List<CollaboratorInfo>>(emptyList()) }
    var showReminderDialog by remember { mutableStateOf(false) }
    val requestNotificationPermission = sk.kubisdev.mynotes.ui.components.rememberNotificationPermissionRequester()
    // uid -> Google account photo URL, for the per-line "who edited this" avatar.
    var collaboratorPhotos by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    val currentUserId = remember { viewModel.getCurrentUserId() }

    // Get collaboration data
    val pendingInvites by viewModel.pendingInvites.collectAsStateWithLifecycle()

    // Formatting states with proper active formatting tracking
    var isToolbarVisible by remember { mutableStateOf(true) } // ✅ FIXED: Default to true
    var editingLineId by remember { mutableStateOf<String?>(null) }
    // editingLineId is nulled the instant a text field blurs, but tapping the toolbar
    // to insert a divider causes exactly that blur right before the click handler
    // runs - so it's always null by then. This tracks the same thing but only updates
    // on focus *gain*, so it still reflects "where the cursor last was" afterwards.
    var lastActiveLineId by remember { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf(TextRange.Zero) }
    var toggledStyles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var activeColor by remember { mutableStateOf<Color?>(null) }

    // Selection tracking
    var selectedLineId by remember { mutableStateOf<String?>(null) }
    var selectedTextRange by remember { mutableStateOf<TextRange?>(null) }

    // Which tap-to-reveal row (completed items have no focusable field) currently
    // shows its delete X - hoisted so revealing one hides the previous one.
    var revealedDeleteId by remember { mutableStateOf<String?>(null) }

    // Track cursor position for active formatting
    var currentCursorPosition by remember { mutableStateOf(0) }

    // Title focus
    val titleFocusRequester = remember { FocusRequester() }
    var isTitleFocused by remember { mutableStateOf(false) }
    val lineFocusRequesters = remember { mutableMapOf<String, RichTextFieldController>() }

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Pre-load string resources
    val sectionDividerText = stringResource(R.string.todo_section_divider)
    val itemDeletedMessage = stringResource(R.string.item_deleted)
    val undoLabel = stringResource(R.string.undo)

    // Enhanced drag state - track by ID, not index
    var draggedItemId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var dropTargetId by remember { mutableStateOf<String?>(null) }
    var initialDragPosition by remember { mutableStateOf(0f) }

    // Auto-scroll state
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

    // Undo state
    var deletedLine by remember { mutableStateOf<Pair<Int, NoteLine>?>(null) }

    // DEBOUNCED AUTOSAVE STATES
    var autoSaveJob by remember { mutableStateOf<Job?>(null) }
    var isUserEditing by remember { mutableStateOf(false) }

    // ✅ FIXED: Track if we should save on exit
    var shouldSaveOnExit by remember { mutableStateOf(!isCollaborative) }

    // The initial load and the real-time listener can both populate `lines`
    // in quick succession right after opening a collaborative note. Scrolling
    // to top only on that first population (and never again) avoids yanking
    // the user's scroll position during later live-collaboration updates,
    // while still guaranteeing the note opens at the top instead of wherever
    // the LazyColumn happened to land after the second replace.
    var hasScrolledToInitialPosition by remember { mutableStateOf(false) }

    // Performance: Use derivedStateOf for computed values
    val activeItems by remember {
        derivedStateOf {
            lines.filter { !it.isChecked || it.type == LineType.SEPARATOR }
        }
    }

    val completedItems by remember {
        derivedStateOf {
            lines.filter { it.isChecked && it.type != LineType.SEPARATOR }
        }
    }

    val activeTaskCount by remember {
        derivedStateOf {
            activeItems.count { it.type != LineType.SEPARATOR }
        }
    }

    // Get the currently dragged item
    val draggedItem by remember {
        derivedStateOf {
            draggedItemId?.let { id -> lines.find { it.id == id } }
        }
    }

    // Auto-save functions for both local and collaborative
    fun triggerImmediateAutoSave() {
        autoSaveJob?.cancel()
        coroutineScope.launch {
            if (isCollaborative && collaborativeNoteId != null) {
                // Awaited (not fire-and-forget updateCollaborativeNote): isUserEditing must
                // stay true until the write actually lands, or the live-sync listener can
                // race a stale snapshot into `lines` mid-edit and yank focus/cursor away.
                viewModel.updateCollaborativeNoteAndAwait(collaborativeNoteId, title, lines.toList())
            } else if (shouldSaveOnExit) { // ✅ FIXED: Only save if we should
                // Use triggerAutoSave for local notes
                viewModel.triggerAutoSave(
                    noteId = noteId,
                    title = title,
                    lines = lines,
                    noteType = NoteType.CHECKLIST,
                    colorIndex = noteColorIndex.takeIf { it >= 0 },
                    onIdReceived = { newId ->
                        if (noteId == 0) {
                            noteId = newId
                        }
                    }
                )
            }
            isUserEditing = false
        }
    }

    fun triggerDebouncedAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = coroutineScope.launch {
            delay(2000)
            if (isCollaborative && collaborativeNoteId != null) {
                // See triggerImmediateAutoSave: must await the real write, not just its dispatch.
                viewModel.updateCollaborativeNoteAndAwait(collaborativeNoteId, title, lines.toList())
            } else if (shouldSaveOnExit) { // ✅ FIXED: Only save if we should
                // Use triggerAutoSave for local notes
                viewModel.triggerAutoSave(
                    noteId = noteId,
                    title = title,
                    lines = lines,
                    noteType = NoteType.CHECKLIST,
                    colorIndex = noteColorIndex.takeIf { it >= 0 },
                    onIdReceived = { newId ->
                        if (noteId == 0) {
                            noteId = newId
                        }
                    }
                )
            }
            isUserEditing = false
        }
    }

    // Auto-scroll function
    fun startAutoScroll(direction: Int) {
        autoScrollJob?.cancel()
        autoScrollJob = coroutineScope.launch {
            while (isActive) {
                val currentIndex = lazyListState.firstVisibleItemIndex
                val targetIndex = (currentIndex + direction).coerceIn(0,
                    lazyListState.layoutInfo.totalItemsCount - 1)
                if (targetIndex != currentIndex) {
                    try {
                        lazyListState.animateScrollToItem(targetIndex)
                    } catch (e: Exception) {
                        // Handle edge cases
                    }
                }
                delay(300) // Scroll speed
            }
        }
    }

    fun stopAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = null
    }

    // Fixed calculateDropTarget function
    fun calculateDropTarget(currentDragOffset: Float): String? {
        val layoutInfo = lazyListState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo

        if (visibleItems.isEmpty() || draggedItem == null) return null

        // Get the dragged item's current position
        val draggedItemInfo = visibleItems.find { it.key == draggedItemId } ?: return null
        val draggedItemY = draggedItemInfo.offset + currentDragOffset
        val draggedItemCenter = draggedItemY + draggedItemInfo.size / 2

        // Find the best drop target
        var bestTarget: String? = null
        var bestDistance = Float.MAX_VALUE

        visibleItems.forEach { itemInfo ->
            if (itemInfo.key is String && itemInfo.key != draggedItemId) {
                val itemCenter = itemInfo.offset + itemInfo.size / 2
                val distance = kotlin.math.abs(draggedItemCenter - itemCenter)

                if (distance < bestDistance) {
                    bestDistance = distance
                    bestTarget = itemInfo.key as String
                }
            }
        }

        return bestTarget
    }

    // Fixed performDrop function
    fun performDrop() {
        if (draggedItem != null && dropTargetId != null && draggedItemId != dropTargetId) {
            val fromIndex = lines.indexOfFirst { it.id == draggedItemId }
            val toIndex = lines.indexOfFirst { it.id == dropTargetId }

            if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                val item = lines.removeAt(fromIndex)

                // Calculate final position based on relative positions
                val finalIndex = if (fromIndex < toIndex) {
                    // Moving down
                    toIndex
                } else {
                    // Moving up
                    toIndex
                }

                lines.add(finalIndex.coerceIn(0, lines.size), item)
                triggerImmediateAutoSave()
            }
        }

        draggedItemId = null
        dropTargetId = null
        dragOffset = 0f
        initialDragPosition = 0f
        stopAutoScroll()
    }

    @Composable
    fun DropIndicator() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .padding(horizontal = 16.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(2.dp)
                )
        )
    }

    // ✅ FIXED: Handle text change to preserve formatting when deleting
    fun handleTextChange(lineId: String, newText: String, cursorPos: Int) {
        val lineIndex = lines.indexOfFirst { it.id == lineId }
        if (lineIndex == -1) return

        val line = lines[lineIndex]

        // CRITICAL: Avoid processing if text hasn't changed
        if (line.content == newText) return

        // Mark user as editing
        isUserEditing = true
        currentCursorPosition = cursorPos

        val existingSpans = line.spanStyles.toMutableList()
        val lengthDiff = newText.length - line.content.length

        // Handle text changes
        if (lengthDiff < 0) {
            // Text was deleted
            val deleteStart = cursorPos
            val deleteEnd = cursorPos - lengthDiff

            // Adjust spans for deletion
            val adjustedSpans = existingSpans.mapNotNull { span ->
                when {
                    // Span is completely before deletion
                    span.end <= deleteStart -> span
                    // Span is completely after deletion
                    span.start >= deleteEnd -> {
                        span.copy(
                            start = (span.start + lengthDiff).coerceAtLeast(0),
                            end = (span.end + lengthDiff).coerceAtLeast(0)
                        )
                    }
                    // Span contains the deletion
                    span.start < deleteStart && span.end > deleteEnd -> {
                        span.copy(end = (span.end + lengthDiff).coerceAtLeast(span.start))
                    }
                    // Span starts in deletion range but ends after
                    span.start >= deleteStart && span.start < deleteEnd && span.end > deleteEnd -> {
                        span.copy(
                            start = deleteStart,
                            end = (span.end + lengthDiff).coerceAtLeast(deleteStart)
                        )
                    }
                    // Span ends in deletion range but starts before
                    span.end > deleteStart && span.end <= deleteEnd && span.start < deleteStart -> {
                        span.copy(end = deleteStart)
                    }
                    // Span is completely within deletion range
                    span.start >= deleteStart && span.end <= deleteEnd -> null
                    else -> null
                }
            }.filter { it.start < newText.length && it.end <= newText.length && it.start < it.end }

            existingSpans.clear()
            existingSpans.addAll(adjustedSpans)

        } else if (lengthDiff > 0) {
            // Text was added
            val insertStart = (cursorPos - lengthDiff).coerceAtLeast(0)
            val insertEnd = cursorPos

            // Adjust existing spans for insertion
            val adjustedSpans = existingSpans.map { span ->
                when {
                    span.end < insertStart -> span
                    span.start >= insertStart -> {
                        span.copy(
                            start = span.start + lengthDiff,
                            end = span.end + lengthDiff
                        )
                    }
                    // span.end == insertStart falls in here too: typing right at the end of a
                    // formatted run (e.g. continuing bold text) should keep extending that
                    // run's formatting instead of the new characters silently falling outside it.
                    span.start < insertStart && span.end >= insertStart -> {
                        // Span contains (or ends at) the insertion point - extend it
                        span.copy(end = span.end + lengthDiff)
                    }
                    else -> span
                }
            }

            existingSpans.clear()
            existingSpans.addAll(adjustedSpans)

            // Apply active formatting to newly typed characters
            if (toggledStyles.isNotEmpty() || activeColor != null) {
                if (insertStart < insertEnd && insertStart >= 0 && insertEnd <= newText.length) {
                    if (toggledStyles.contains("BOLD")) {
                        existingSpans.add(
                            SerializableSpanStyle(
                                start = insertStart,
                                end = insertEnd,
                                fontWeight = FontWeight.Bold.weight
                            )
                        )
                    }

                    if (toggledStyles.contains("ITALIC")) {
                        existingSpans.add(
                            SerializableSpanStyle(
                                start = insertStart,
                                end = insertEnd,
                                fontStyle = "italic"
                            )
                        )
                    }

                    if (toggledStyles.contains("UNDERLINE")) {
                        existingSpans.add(
                            SerializableSpanStyle(
                                start = insertStart,
                                end = insertEnd,
                                textDecoration = "underline"
                            )
                        )
                    }

                    activeColor?.let { color ->
                        existingSpans.add(
                            SerializableSpanStyle(
                                start = insertStart,
                                end = insertEnd,
                                color = color.value
                            )
                        )
                    }
                }
            }
        }

        // Update the line
        lines[lineIndex] = line.copy(
            content = newText,
            spanStyles = cleanupOverlappingSpans(mergeAdjacentSpans(existingSpans)),
            // Tag the line with its editor so shared checklists show whose change it
            // was (only meaningful for collaborative notes; harmless otherwise).
            editorId = if (isCollaborative) currentUserId ?: line.editorId else line.editorId
        )

        triggerDebouncedAutoSave()
    }

    // Function to apply formatting to selected text with toggle
    fun applyFormattingToSelection(
        toggleBold: Boolean = false,
        toggleItalic: Boolean = false,
        toggleUnderline: Boolean = false,
        color: Color? = null
    ) {
        // lastActiveLineId/selection (not selectedLineId/selectedTextRange) deliberately: the
        // color picker is a DropdownMenu, a focusable popup that steals Android view focus the
        // instant it opens, blurring the text field and nulling selectedLineId/selectedTextRange
        // before the user picks a color. lastActiveLineId/selection are only ever updated on
        // selection change, never cleared on blur.
        if (lastActiveLineId != null) {
            val lineIndex = lines.indexOfFirst { it.id == lastActiveLineId }
            if (lineIndex != -1) {
                val line = lines[lineIndex]
                val start = selection.min
                val end = selection.max

                if (start < end && start >= 0 && end <= line.content.length) {
                    // The collaborative live-sync listener is only guarded by !isUserEditing,
                    // so a Firestore snapshot from just before this formatting change can land
                    // while the debounced/immediate autosave is still in flight and revert
                    // `lines` right back to the pre-change state.
                    isUserEditing = true

                    val existingSpans = line.spanStyles.toMutableList()

                    // Helper function to check if the entire selection has a specific formatting
                    fun hasFormattingInEntireSelection(checkSpan: (SerializableSpanStyle) -> Boolean): Boolean {
                        val positions = BooleanArray(end - start) { false }

                        existingSpans.forEach { span ->
                            if (checkSpan(span)) {
                                val overlapStart = maxOf(span.start, start)
                                val overlapEnd = minOf(span.end, end)
                                if (overlapStart < overlapEnd) {
                                    for (i in overlapStart until overlapEnd) {
                                        if (i >= start && i < end) {
                                            positions[i - start] = true
                                        }
                                    }
                                }
                            }
                        }

                        return positions.all { it }
                    }

                    // Process BOLD toggle
                    if (toggleBold) {
                        val hasBold = hasFormattingInEntireSelection { it.fontWeight != null }

                        if (hasBold) {
                            val newSpans = mutableListOf<SerializableSpanStyle>()
                            existingSpans.forEach { span ->
                                if (span.fontWeight == null) {
                                    newSpans.add(span)
                                } else {
                                    if (span.end <= start || span.start >= end) {
                                        newSpans.add(span)
                                    } else if (span.start < start && span.end > end) {
                                        newSpans.add(span.copy(end = start))
                                        newSpans.add(span.copy(start = end))
                                    } else if (span.start < start && span.end > start) {
                                        newSpans.add(span.copy(end = start))
                                    } else if (span.start < end && span.end > end) {
                                        newSpans.add(span.copy(start = end))
                                    }
                                }
                            }
                            existingSpans.clear()
                            existingSpans.addAll(newSpans)
                        } else {
                            existingSpans.add(
                                SerializableSpanStyle(
                                    start = start,
                                    end = end,
                                    fontWeight = FontWeight.Bold.weight
                                )
                            )
                        }
                    }

                    // Process ITALIC toggle
                    if (toggleItalic) {
                        val hasItalic = hasFormattingInEntireSelection { it.fontStyle != null }

                        if (hasItalic) {
                            val newSpans = mutableListOf<SerializableSpanStyle>()
                            existingSpans.forEach { span ->
                                if (span.fontStyle == null) {
                                    newSpans.add(span)
                                } else {
                                    if (span.end <= start || span.start >= end) {
                                        newSpans.add(span)
                                    } else if (span.start < start && span.end > end) {
                                        newSpans.add(span.copy(end = start))
                                        newSpans.add(span.copy(start = end))
                                    } else if (span.start < start && span.end > start) {
                                        newSpans.add(span.copy(end = start))
                                    } else if (span.start < end && span.end > end) {
                                        newSpans.add(span.copy(start = end))
                                    }
                                }
                            }
                            existingSpans.clear()
                            existingSpans.addAll(newSpans)
                        } else {
                            existingSpans.add(
                                SerializableSpanStyle(
                                    start = start,
                                    end = end,
                                    fontStyle = "italic"
                                )
                            )
                        }
                    }

                    // Process UNDERLINE toggle
                    if (toggleUnderline) {
                        val hasUnderline = hasFormattingInEntireSelection { it.textDecoration != null }

                        if (hasUnderline) {
                            val newSpans = mutableListOf<SerializableSpanStyle>()
                            existingSpans.forEach { span ->
                                if (span.textDecoration == null) {
                                    newSpans.add(span)
                                } else {
                                    if (span.end <= start || span.start >= end) {
                                        newSpans.add(span)
                                    } else if (span.start < start && span.end > end) {
                                        newSpans.add(span.copy(end = start))
                                        newSpans.add(span.copy(start = end))
                                    } else if (span.start < start && span.end > start) {
                                        newSpans.add(span.copy(end = start))
                                    } else if (span.start < end && span.end > end) {
                                        newSpans.add(span.copy(start = end))
                                    }
                                }
                            }
                            existingSpans.clear()
                            existingSpans.addAll(newSpans)
                        } else {
                            existingSpans.add(
                                SerializableSpanStyle(
                                    start = start,
                                    end = end,
                                    textDecoration = "underline"
                                )
                            )
                        }
                    }

                    // Process COLOR (not a toggle, always sets)
                    if (color != null) {
                        val newSpans = mutableListOf<SerializableSpanStyle>()
                        existingSpans.forEach { span ->
                            if (span.color == null) {
                                newSpans.add(span)
                            } else {
                                if (span.end <= start || span.start >= end) {
                                    newSpans.add(span)
                                } else if (span.start < start && span.end > end) {
                                    newSpans.add(span.copy(end = start))
                                    newSpans.add(span.copy(start = end))
                                } else if (span.start < start && span.end > start) {
                                    newSpans.add(span.copy(end = start))
                                } else if (span.start < end && span.end > end) {
                                    newSpans.add(span.copy(start = end))
                                }
                            }
                        }
                        existingSpans.clear()
                        existingSpans.addAll(newSpans)

                        existingSpans.add(
                            SerializableSpanStyle(
                                start = start,
                                end = end,
                                color = color.value
                            )
                        )
                    }

                    lines[lineIndex] = line.copy(
                        spanStyles = cleanupOverlappingSpans(mergeAdjacentSpans(existingSpans))
                    )

                    // Trigger immediate save after formatting
                    triggerImmediateAutoSave()
                }
            }
        }
    }

    // Delete with undo function with immediate save
    fun deleteLineWithUndo(line: NoteLine) {
        val index = lines.indexOf(line)
        if (index != -1) {
            lines.remove(line)

            // Remove focus requester
            lineFocusRequesters.remove(line.id)

            // Immediate save when deleting
            triggerImmediateAutoSave()

            // Empty lines get deleted constantly while typing (backspace on a blank
            // item) - a 4s undo snackbar for each was pure noise over the keyboard.
            // Only content that could actually be lost earns the Undo offer.
            if (line.content.isBlank()) return
            deletedLine = Pair(index, line)

            coroutineScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = itemDeletedMessage,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Short
                )

                when (result) {
                    SnackbarResult.ActionPerformed -> {
                        deletedLine?.let { (oldIndex, oldLine) ->
                            val insertIndex = minOf(oldIndex, lines.size)
                            lines.add(insertIndex, oldLine)
                            triggerImmediateAutoSave()
                        }
                    }
                    SnackbarResult.Dismissed -> {
                        deletedLine = null
                    }
                }
            }
        }
    }

    // Memoized checkbox handler with immediate save
    val handleCheckChange = remember<(NoteLine, Boolean) -> Unit>(isCollaborative, currentUserId) {
        { line, isChecked ->
            val index = lines.indexOf(line)
            if (index != -1) {
                lines[index] = line.copy(
                    isChecked = isChecked,
                    editorId = if (isCollaborative) currentUserId ?: line.editorId else line.editorId
                )

                // Immediate save for structural changes
                triggerImmediateAutoSave()

                if (isChecked) {
                    coroutineScope.launch {
                        delay(300)
                        val currentIndex = lines.indexOf(line)
                        if (currentIndex != -1) {
                            val item = lines.removeAt(currentIndex)
                            lines.add(item)
                            triggerImmediateAutoSave()
                        }
                    }
                }
            }
        }
    }

    // Load existing note - only when NOT editing and NOT collaborative
    LaunchedEffect(noteIdArg) {
        if (noteIdArg != 0 && !isCollaborative) {
            viewModel.getNoteById(noteIdArg).collectLatest { note ->
                if (note != null) {
                    loadedNote = note
                    // CRITICAL: Only update if user is NOT editing
                    if (!isUserEditing) {
                        noteColorIndex = note.colorIndex
                        notePatternIndex = note.patternIndex
                        if (title != note.title) {
                            title = note.title
                        }

                        val loadedLines = try {
                            note.content.toNoteLines()
                        } catch (e: Exception) {
                            println("Error parsing note content: ${e.message}")
                            listOf(NoteLine(content = note.content, type = LineType.CHECKLIST))
                        }

                        lines.clear()
                        if (loadedLines.isNotEmpty()) {
                            lines.addAll(loadedLines)
                        } else {
                            lines.add(NoteLine(type = LineType.CHECKLIST, content = ""))
                        }

                        if (lines.any { it.content.isNotEmpty() }) {
                            isToolbarVisible = true
                        }
                    }
                }
            }
        }
    }

    // Load collaborative note - FIXED to load properly
    LaunchedEffect(collaborativeNoteId) {
        if (isCollaborative && collaborativeNoteId != null) {
            // First, clear any existing content
            lines.clear()
            title = ""

            // Load the collaborative note
            viewModel.getCollaborativeNoteById(collaborativeNoteId) { result ->
                result.onSuccess { note ->
                    if (note != null) {
                        title = note.title
                        isOwner = note.ownerId == FirebaseAuth.getInstance().currentUser?.uid
                        // Personal (device-local) color/pattern for this shared list
                        noteColorIndex = sk.kubisdev.mynotes.CollabLocalPrefs.getColorIndex(context, collaborativeNoteId)
                        notePatternIndex = sk.kubisdev.mynotes.CollabLocalPrefs.getPatternIndex(context, collaborativeNoteId)
                        val loadedLines = note.content.toNoteLines()

                        lines.clear() // Clear again to be safe
                        if (loadedLines.isNotEmpty()) {
                            lines.addAll(loadedLines)
                        } else {
                            // Only add empty line if there's truly no content
                            lines.add(NoteLine(type = LineType.CHECKLIST, content = ""))
                            // Push the seeded placeholder to Firestore immediately so the
                            // live-sync listener's first snapshot (500ms later) already
                            // matches local state - otherwise it sees the still-blank
                            // remote doc and wipes this placeholder back to zero lines
                            // before the user gets a chance to type.
                            triggerImmediateAutoSave()
                        }

                        // Load collaborator information
                        collaborators = note.collaborators.mapIndexed { index, userId ->
                            CollaboratorInfo(
                                userId = userId,
                                email = if (userId == FirebaseAuth.getInstance().currentUser?.uid) {
                                    FirebaseAuth.getInstance().currentUser?.email ?: "user@email.com"
                                } else {
                                    "User ${index + 1}"
                                },
                                displayName = if (userId == note.ownerId) "Owner" else "Collaborator ${index + 1}",
                                role = if (userId == note.ownerId) CollaboratorRole.OWNER else CollaboratorRole.EDITOR,
                                invitedAt = note.createdAt,
                                status = InviteStatus.ACCEPTED
                            )
                        }

                        if (lines.any { it.content.isNotEmpty() }) {
                            isToolbarVisible = true
                        }

                        if (!hasScrolledToInitialPosition) {
                            hasScrolledToInitialPosition = true
                            coroutineScope.launch { lazyListState.scrollToItem(0) }
                        }

                        // Start real-time sync after initial load
                        viewModel.startCollaborativeSync()
                    }
                }
                result.onFailure { error ->
                    // No undo to offer here, so no snackbar - just log for diagnostics.
                    android.util.Log.e("ToDoListScreen", "Failed to load collaborative note", error)
                }
            }
        }
    }

    // Real-time updates - separate LaunchedEffect
    LaunchedEffect(isCollaborative, collaborativeNoteId) {
        if (isCollaborative && collaborativeNoteId != null) {
            // Small delay to ensure initial load completes first
            delay(500)

            // Listen for real-time updates. try/catch is defense-in-depth: the flow
            // itself no longer rethrows Firestore permission errors (e.g. right after
            // leaving/being removed from this note), but a collect{} that crashes the
            // whole app on any future stream error is one bad state away regardless.
            try {
                viewModel.getCollaborativeNoteFlow(collaborativeNoteId).collect { note ->
                if (note != null && !isUserEditing) {
                    // Only update if content has actually changed
                    val newLines = note.content.toNoteLines()
                    val currentContent = lines.map { it.content }
                    val newContent = newLines.map { it.content }

                    if (currentContent != newContent || title != note.title) {
                        title = note.title
                        isOwner = note.ownerId == FirebaseAuth.getInstance().currentUser?.uid
                        lines.replaceAllSmart(newLines)

                        // Update collaborators
                        collaborators = note.collaborators.mapIndexed { index, userId ->
                            CollaboratorInfo(
                                userId = userId,
                                email = if (userId == FirebaseAuth.getInstance().currentUser?.uid) {
                                    FirebaseAuth.getInstance().currentUser?.email ?: "user@email.com"
                                } else {
                                    "User ${index + 1}"
                                },
                                displayName = if (userId == note.ownerId) "Owner" else "Collaborator ${index + 1}",
                                role = if (userId == note.ownerId) CollaboratorRole.OWNER else CollaboratorRole.EDITOR,
                                invitedAt = note.createdAt,
                                status = InviteStatus.ACCEPTED
                            )
                        }

                        if (!hasScrolledToInitialPosition) {
                            hasScrolledToInitialPosition = true
                            lazyListState.scrollToItem(0)
                        }
                    }
                }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ToDoListScreen", "Real-time collaborative note listener failed", e)
            }
        }
    }

    // Initialize with empty line for new notes - FIXED condition
    LaunchedEffect(noteIdArg, isCollaborative, collaborativeNoteId) {
        if (noteIdArg == 0 && !isCollaborative && lines.isEmpty()) {
            lines.add(NoteLine(type = LineType.CHECKLIST, content = ""))
            delay(300)
            titleFocusRequester.requestFocus()
        }
    }

    // Load member photos whenever the collaborator set changes (initial load + when
    // someone joins), so each line can show its editor's Google avatar.
    val collaboratorIds = collaborators.map { it.userId }
    LaunchedEffect(collaboratorIds) {
        if (isCollaborative && collaboratorIds.isNotEmpty()) {
            viewModel.loadCollaboratorProfiles(collaboratorIds) { profiles ->
                collaboratorPhotos = profiles.mapValues { it.value.photoUrl }
            }
        }
    }

    // Collaboration dialogs
    if (showCollaborationDialog) {
        CollaborationDialog(
            onDismiss = { showCollaborationDialog = false },
            onCreateCollaborative = {
                coroutineScope.launch {
                    // Create collaborative note with CURRENT content
                    val currentTitle = title.ifBlank { "Collaborative Todo" }
                    val currentLines = lines.toList()

                    // ✅ FIXED: Set flag to not save empty note when exiting
                    shouldSaveOnExit = false

                    // If this is an existing note, delete the local version
                    if (noteId != 0) {
                        viewModel.deleteNoteById(noteId)
                    } else {
                        // ✅ FIXED: For new notes, just navigate without saving
                        // Don't create an empty local note
                    }

                    viewModel.createCollaborativeNote(
                        title = currentTitle,
                        lines = currentLines,
                        noteType = NoteType.CHECKLIST
                    ) { newCollaborativeId: String ->
                        // Navigate using the collaborative route
                        navController.navigate("collaborative_todo/$newCollaborativeId") {
                            popUpTo(navController.graph.id) {
                                inclusive = false
                            }
                        }
                    }
                }
                showCollaborationDialog = false
            },
            onJoinExisting = {
                showInviteDialog = true
                showCollaborationDialog = false
            }
        )
    }

    if (showInviteDialog) {
        InviteManagementDialog(
            collaborativeNoteId = collaborativeNoteId,
            pendingInvites = pendingInvites,
            onDismiss = { showInviteDialog = false },
            onSendInvite = { email ->
                if (collaborativeNoteId != null) {
                    viewModel.sendCollaborationInvite(collaborativeNoteId, email) { _ ->
                        // Handle result
                    }
                }
            },
            onAcceptInvite = { inviteId ->
                viewModel.acceptInvitation(inviteId) { success ->
                    if (success) {
                        showInviteDialog = false
                        viewModel.startCollaborativeSync()
                    }
                }
            },
            onDeclineInvite = { inviteId ->
                viewModel.declineInvitation(inviteId) { _ ->
                    // Handle result
                }
            }
        )
    }

    if (showCollabColorPicker) {
        sk.kubisdev.mynotes.ui.components.ColorPickerDialog(
            currentColorIndex = noteColorIndex.coerceAtLeast(0),
            onColorSelected = { picked ->
                noteColorIndex = picked
                if (collaborativeNoteId != null) {
                    // Personal, device-local color - each collaborator picks their own
                    sk.kubisdev.mynotes.CollabLocalPrefs.setColorIndex(context, collaborativeNoteId, picked)
                } else {
                    // Saved note writes immediately; a new (unsaved) note keeps the pick in
                    // noteColorIndex and flushes it via a save.
                    loadedNote?.let { viewModel.updateNoteColor(it, picked) } ?: triggerImmediateAutoSave()
                }
                showCollabColorPicker = false
            },
            onDismiss = { showCollabColorPicker = false },
            currentPatternIndex = notePatternIndex,
            onPatternSelected = { picked ->
                notePatternIndex = picked
                if (collaborativeNoteId != null) {
                    sk.kubisdev.mynotes.CollabLocalPrefs.setPatternIndex(context, collaborativeNoteId, picked)
                } else {
                    loadedNote?.let { viewModel.updateNotePattern(it, picked) }
                }
            }
        )
    }

    if (showReminderDialog && noteId != 0) {
        sk.kubisdev.mynotes.ui.components.ReminderDialog(
            currentReminder = loadedNote?.reminderAt,
            onSet = { millis ->
                viewModel.setReminder(noteId, millis)
                if (millis > System.currentTimeMillis()) {
                    android.widget.Toast.makeText(
                        context,
                        sk.kubisdev.mynotes.ui.components.reminderLeadToastText(context, millis),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                showReminderDialog = false
            },
            onRemove = {
                viewModel.clearReminder(noteId)
                showReminderDialog = false
            },
            onDismiss = { showReminderDialog = false }
        )
    }

    if (showCollaboratorsDialog) {
        CollaboratorsDialog(
            collaborators = collaborators,
            onDismiss = { showCollaboratorsDialog = false },
            onRemoveCollaborator = { _ ->
                // Owner-side removal of a specific collaborator is not yet
                // supported by the collaboration backend (only self-removal).
            }
        )
    }

    Scaffold(
        // Container-level tint so the note color also fills the strip behind the
        // gesture-navigation area (content is inset above it, so a content-level
        // background alone leaves a default-colored band at the bottom edge).
        containerColor = if (noteColorIndex >= 0) {
            NoteColorPalette.getEditorBackground(
                noteColorIndex,
                isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            )
        } else {
            MaterialTheme.colorScheme.background
        },
        // windowSoftInputMode="adjustResize" already shrinks the window when the IME
        // opens, so WindowInsets.ime is already folded into the resized layout here.
        // Adding Modifier.imePadding() on top double-counted the keyboard height and
        // pushed the snackbar clean off the top of the (already-shrunk) screen.
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            GradientTopAppBar(
                title = {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Collaboration indicator
                        if (isCollaborative) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 2.dp)
                            ) {
                                Icon(
                                    Icons.Default.Group,
                                    contentDescription = "Collaborative",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.collab_members_count, collaborators.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Long titles shrink and may wrap to a second line instead of
                            // being clipped at the bar's edge.
                            val titleFontSize = when {
                                title.length > 45 -> 14.sp
                                title.length > 28 -> 17.sp
                                else -> MaterialTheme.typography.titleLarge.fontSize
                            }
                            BasicTextField(
                                value = title,
                                onValueChange = {
                                    title = it
                                    triggerDebouncedAutoSave()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(titleFocusRequester)
                                    .onFocusChanged { isTitleFocused = it.isFocused },
                                textStyle = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = titleFontSize,
                                    color = MaterialTheme.colorScheme.onPrimary
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = {
                                    lineFocusRequesters[lines.firstOrNull()?.id]?.requestFocus()
                                }),
                                maxLines = 2,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.onPrimary)
                            )
                            if (title.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.enter_title_hint),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        // ✅ FIXED: Don't create empty note when going back
                        if (!isCollaborative || collaborativeNoteId == null || collaborativeNoteId == "new") {
                            navController.navigateUp()
                        } else {
                            navController.navigateUp()
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    // Toggle toolbar visibility - FIXED ICON COLOR
                    IconButton(onClick = { isToolbarVisible = !isToolbarVisible }) {
                        Icon(
                            Icons.Default.TextFields,
                            "Toggle Toolbar",
                            tint = MaterialTheme.colorScheme.onPrimary // ✅ FIXED: Always use onPrimary
                        )
                    }

                    // Save and exit
                    IconButton(onClick = {
                        coroutineScope.launch {
                            // ✅ FIXED: Don't save empty notes when exiting
                            val hasContent = title.isNotBlank() || lines.any { it.content.isNotBlank() }

                            if (isCollaborative && collaborativeNoteId != null) {
                                viewModel.updateCollaborativeNote(collaborativeNoteId, title, lines.toList())
                            } else if (hasContent && shouldSaveOnExit) { // ✅ FIXED: Also check shouldSaveOnExit
                                val contentJson = lines.toList().toJson()
                                viewModel.finaliseAndSave(noteId, title, contentJson, NoteType.CHECKLIST, noteColorIndex.takeIf { it >= 0 }).join()
                            }
                            navController.navigateUp()
                        }
                    }) {
                        Icon(
                            Icons.Default.Done,
                            "Save",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    // Overflow menu (make collaborative + collab actions + color +
                    // delete), identical in structure and ordering to the text-note
                    // editor's three-dot menu - collaboration controls previously lived
                    // in a separate FAB here, which made the two editors behave
                    // differently for the exact same feature.
                    var showOverflowMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more_options),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            if (!isCollaborative) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.collab_make_collaborative)) },
                                    leadingIcon = { Icon(Icons.Default.Group, null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        // Sharing needs an account; send the user to sign-in
                                        // first instead of failing silently in Firestore.
                                        if (viewModel.isUserSignedIn()) {
                                            showCollaborationDialog = true
                                        } else {
                                            navController.navigate("sign_in")
                                        }
                                    }
                                )
                            }
                            // Color is available even before the first save; the pick is kept
                            // in noteColorIndex and written with the note on autosave/Done.
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.note_color)) },
                                leadingIcon = { Icon(Icons.Default.Palette, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showCollabColorPicker = true
                                }
                            )
                            if (isCollaborative && collaborativeNoteId != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.collab_invite)) },
                                    leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showInviteDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.collab_members)) },
                                    leadingIcon = { Icon(Icons.Default.People, null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showCollaboratorsDialog = true
                                    }
                                )
                                // Shared notes live only in Firestore and aren't part of
                                // the Drive backup, so offer a private local snapshot.
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.collab_save_copy)) },
                                    leadingIcon = { Icon(Icons.Default.SaveAlt, null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        // No undo to offer here (the copy is a separate
                                        // note the user can just delete), so a toast is
                                        // enough confirmation.
                                        viewModel.saveCollaborativeNoteLocally(
                                            title = context.getString(R.string.collab_local_copy_title, title),
                                            lines = lines.toList(),
                                            noteType = NoteType.CHECKLIST
                                        ) {
                                            android.widget.Toast.makeText(
                                                context,
                                                context.getString(R.string.collab_local_copy_saved),
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (isOwner) stringResource(R.string.action_delete)
                                            else stringResource(R.string.collab_leave)
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (isOwner) Icons.Default.Delete else Icons.AutoMirrored.Filled.ExitToApp,
                                            null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        if (isOwner) {
                                            viewModel.deleteCollaborativeNote(collaborativeNoteId)
                                        } else {
                                            viewModel.leaveCollaborativeNote(collaborativeNoteId)
                                        }
                                        navController.navigateUp()
                                    }
                                )
                            } else {
                                // Reminder stays visible before the note is saved but greyed:
                                // scheduling needs a saved note id, so tapping it while unsaved
                                // just explains why.
                                val reminderEnabled = noteId != 0
                                val reminderTint = if (reminderEnabled) Color.Unspecified
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reminder_menu), color = reminderTint) },
                                    leadingIcon = {
                                        Icon(
                                            if (loadedNote?.reminderAt != null) Icons.Default.NotificationsActive
                                            else Icons.Default.NotificationAdd,
                                            null,
                                            tint = if (reminderEnabled) LocalContentColor.current else reminderTint
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        if (reminderEnabled) {
                                            requestNotificationPermission()
                                            showReminderDialog = true
                                        } else {
                                            android.widget.Toast.makeText(
                                                context,
                                                context.getString(R.string.reminder_save_note_first),
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                                if (noteId != 0) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_delete)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.deleteNoteById(noteId)
                                            navController.navigateUp()
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                bottomContent = {
                    // Hosted inside the top bar's gradient so the bar + toolbar render
                    // as one continuous surface instead of two mismatched gradients.
                    AnimatedVisibility(visible = isToolbarVisible) {
                        FormattingToolbar(
                            toggledStyles = toggledStyles,
                            activeColor = activeColor,
                            activeFontSize = 16,
                            showFontSizeButton = false,
                            onStyleToggle = { styleKey ->
                                if (lastActiveLineId != null && !selection.collapsed) {
                                    applyFormattingToSelection(
                                        toggleBold = styleKey == "BOLD",
                                        toggleItalic = styleKey == "ITALIC",
                                        toggleUnderline = styleKey == "UNDERLINE"
                                    )
                                } else {
                                    toggledStyles = if (toggledStyles.contains(styleKey)) {
                                        toggledStyles - styleKey
                                    } else {
                                        toggledStyles + styleKey
                                    }
                                }
                            },
                            onColorChange = { color ->
                                if (lastActiveLineId != null && !selection.collapsed) {
                                    applyFormattingToSelection(color = color)
                                } else {
                                    activeColor = if (activeColor == color) null else color
                                }
                            },
                            onFontSizeChange = {
                                // Font size button is hidden here (showFontSizeButton = false) -
                                // to-do items only offer color, not size, per-item sizing doesn't
                                // read well on short checklist rows.
                            },
                            onAddSeparator = {
                                // If the cursor sits on an empty item, convert that row in
                                // place - same as the notes editor - instead of leaving a
                                // stray blank row above the new divider.
                                val currentIndex = lastActiveLineId
                                    ?.let { id -> lines.indexOfFirst { it.id == id } }
                                    ?.takeIf { it >= 0 } ?: -1
                                val current = lines.getOrNull(currentIndex)
                                if (current != null && current.type != LineType.SEPARATOR && current.content.isEmpty()) {
                                    lines[currentIndex] = current.copy(
                                        type = LineType.SEPARATOR,
                                        content = sectionDividerText,
                                        isChecked = false
                                    )
                                } else {
                                    val separatorLine = NoteLine(
                                        type = LineType.SEPARATOR,
                                        content = sectionDividerText
                                    )
                                    // Insert right after wherever the cursor last was; only fall back to
                                    // "before the completed section" when nothing has been focused yet.
                                    val insertIndex = (currentIndex.takeIf { it >= 0 }?.let { it + 1 })
                                        ?: lines.indexOfFirst { it.isChecked && it.type != LineType.SEPARATOR }
                                            .takeIf { it >= 0 }
                                        ?: lines.size
                                    lines.add(insertIndex.coerceIn(0, lines.size), separatorLine)
                                }
                                triggerImmediateAutoSave()
                            },
                            drawOwnBackground = false
                        )
                    }
                }
            )
        },
        // No FAB here: collaboration actions (invite/members/save copy/leave/color)
        // now live in the top bar's overflow menu, matching the text-note editor
        // exactly instead of using a different pattern per screen.
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .then(
                    if (noteColorIndex >= 0) {
                        Modifier
                            .background(
                                NoteColorPalette.getEditorBackground(
                                    noteColorIndex,
                                    isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                                )
                            )
                            .notePattern(notePatternIndex, MaterialTheme.colorScheme.onSurface)
                    } else {
                        Modifier
                    }
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                // Progress indicator
                if (lines.isNotEmpty() && lines.any { it.type == LineType.CHECKLIST }) {
                    val totalTasks = lines.count { it.type == LineType.CHECKLIST }
                    val completedTasks = lines.count { it.type == LineType.CHECKLIST && it.isChecked }
                    val progress = if (totalTasks > 0) completedTasks.toFloat() / totalTasks.toFloat() else 0f

                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.todo_progress),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "$completedTasks / $totalTasks",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = lazyListState,
                    // Same proven setup as the notes editor: a fixed bottom runway is all
                    // bringIntoView() needs, since it targets the cursor's line plus a
                    // 64.dp margin (not the whole field). The old IME-conditional 320.dp
                    // just left a huge empty gap while the keyboard was up and still didn't
                    // lift the last task reliably. 96.dp doubles as FAB clearance.
                    contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
                ) {
                    // Active items section
                    if (activeItems.isNotEmpty()) {
                        item(key = "active_header") {
                            if (completedItems.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.todo_active_count, activeTaskCount),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }

                        itemsIndexed(
                            items = activeItems,
                            key = { _, item -> item.id }
                        ) { _, line ->
                            val isDragged = draggedItemId == line.id
                            val isDropTarget = dropTargetId == line.id

                            // Show drop indicator above if this is the target
                            if (isDropTarget && !isDragged && draggedItem != null) {
                                DropIndicator()
                            }

                            Box(
                                modifier = Modifier
                                    .then(
                                        if (isDragged) {
                                            Modifier
                                                .zIndex(1f)
                                                .graphicsLayer {
                                                    translationY = dragOffset
                                                    scaleX = 1.05f
                                                    scaleY = 1.05f
                                                    shadowElevation = 16.dp.toPx()
                                                    alpha = 0.9f
                                                }
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                when (line.type) {
                                    LineType.SEPARATOR -> {
                                        SeparatorItem(
                                            text = line.content.ifEmpty { sectionDividerText },
                                            onTextChange = { newText ->
                                                val index = lines.indexOf(line)
                                                if (index != -1) {
                                                    lines[index] = line.copy(content = newText)
                                                    triggerDebouncedAutoSave()
                                                }
                                            },
                                            onDelete = { deleteLineWithUndo(line) },
                                            onDragStart = {
                                                draggedItemId = line.id
                                                initialDragPosition = 0f
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragOffset = { offset ->
                                                if (draggedItemId == line.id) {
                                                    dragOffset += offset
                                                    dropTargetId = calculateDropTarget(dragOffset)

                                                    // Auto-scroll logic
                                                    val layoutInfo = lazyListState.layoutInfo
                                                    val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset

                                                    when {
                                                        dragOffset < -viewportHeight * 0.2f -> startAutoScroll(-1)
                                                        dragOffset > viewportHeight * 0.2f -> startAutoScroll(1)
                                                        else -> stopAutoScroll()
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                if (draggedItemId == line.id) {
                                                    performDrop()
                                                }
                                            }
                                        )
                                    }
                                    else -> {
                                        PlainTextToDoItem(
                                            line = line,
                                            isEditing = editingLineId == line.id,
                                            lineFocusRequesters = lineFocusRequesters,
                                            isCollaborative = isCollaborative,
                                            editorPhotoUrl = line.editorId?.let { collaboratorPhotos[it] },
                                            onTextChange = { newText, cursorPos ->
                                                handleTextChange(line.id, newText, cursorPos)
                                            },
                                            onCheckChange = { isChecked ->
                                                handleCheckChange(line, isChecked)
                                            },
                                            onDelete = { deleteLineWithUndo(line) },
                                            onFocusChange = { hasFocus ->
                                                editingLineId = if (hasFocus) line.id else null
                                                if (hasFocus) {
                                                    lastActiveLineId = line.id
                                                    // Focusing an active row dismisses any
                                                    // tap-revealed X on completed rows.
                                                    revealedDeleteId = null
                                                } else {
                                                    selectedLineId = null
                                                    selectedTextRange = null
                                                }
                                            },
                                            onBackspaceOnEmptyLine = {
                                                deleteLineWithUndo(line)
                                            },
                                            onEnterPressed = {
                                                val lineIndex = lines.indexOf(line)
                                                if (lineIndex != -1) {
                                                    val newLine = NoteLine(type = LineType.CHECKLIST, content = "")
                                                    lines.add(lineIndex + 1, newLine)
                                                    triggerImmediateAutoSave()

                                                    editingLineId = null

                                                    coroutineScope.launch {
                                                        delay(100)
                                                        editingLineId = newLine.id

                                                        // Scroll the new line into the LazyColumn's composed
                                                        // window BEFORE requesting focus - a focus target whose
                                                        // item hasn't been laid out yet (e.g. inserted below the
                                                        // visible area) isn't attached to anything, so
                                                        // requestFocus() on it silently does nothing.
                                                        val newItemIndex = activeItems.indexOfFirst { it.id == newLine.id }
                                                        if (newItemIndex >= 0) {
                                                            lazyListState.animateScrollToItem(newItemIndex + 1)
                                                        }

                                                        val newFocusRequester = lineFocusRequesters.getOrPut(newLine.id) { RichTextFieldController() }
                                                        delay(50)
                                                        try {
                                                            newFocusRequester.requestFocus()
                                                        } catch (e: Exception) {
                                                            delay(100)
                                                            newFocusRequester.requestFocus()
                                                        }
                                                    }
                                                }
                                            },
                                            onSelectionChange = { lineId, range ->
                                                selectedLineId = lineId
                                                selectedTextRange = range
                                                selection = range

                                                if (!range.collapsed && lineId == line.id) {
                                                    val lineIndex = lines.indexOfFirst { it.id == lineId }
                                                    if (lineIndex != -1) {
                                                        val currentLine = lines[lineIndex]

                                                        val selectionStart = range.min
                                                        val selectionEnd = range.max

                                                        fun hasFormattingInSelection(checkSpan: (SerializableSpanStyle) -> Boolean): Boolean {
                                                            if (selectionStart >= selectionEnd) return false

                                                            val positions = BooleanArray(selectionEnd - selectionStart) { false }

                                                            currentLine.spanStyles.forEach { span ->
                                                                if (checkSpan(span)) {
                                                                    val overlapStart = maxOf(span.start, selectionStart)
                                                                    val overlapEnd = minOf(span.end, selectionEnd)
                                                                    if (overlapStart < overlapEnd) {
                                                                        for (i in overlapStart until overlapEnd) {
                                                                            if (i >= selectionStart && i < selectionEnd) {
                                                                                positions[i - selectionStart] = true
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }

                                                            return positions.all { it }
                                                        }

                                                        val hasBold = hasFormattingInSelection { it.fontWeight != null }
                                                        val hasItalic = hasFormattingInSelection { it.fontStyle != null }
                                                        val hasUnderline = hasFormattingInSelection { it.textDecoration != null }

                                                        val newToggledStyles = mutableSetOf<String>()
                                                        if (hasBold) newToggledStyles.add("BOLD")
                                                        if (hasItalic) newToggledStyles.add("ITALIC")
                                                        if (hasUnderline) newToggledStyles.add("UNDERLINE")

                                                        toggledStyles = newToggledStyles

                                                        val colorSpans = currentLine.spanStyles.filter { span ->
                                                            span.color != null &&
                                                                    span.start < selectionEnd && span.end > selectionStart
                                                        }
                                                        activeColor = colorSpans.firstOrNull()?.let { Color(it.color!!) }
                                                    }
                                                }
                                            },
                                            onDragStart = {
                                                if (!line.isChecked || line.type == LineType.SEPARATOR) {
                                                    draggedItemId = line.id
                                                    initialDragPosition = 0f
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            },
                                            onDragOffset = { offset ->
                                                if (draggedItemId == line.id) {
                                                    dragOffset += offset
                                                    dropTargetId = calculateDropTarget(dragOffset)

                                                    // Auto-scroll logic
                                                    val layoutInfo = lazyListState.layoutInfo
                                                    val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset

                                                    when {
                                                        dragOffset < -viewportHeight * 0.2f -> startAutoScroll(-1)
                                                        dragOffset > viewportHeight * 0.2f -> startAutoScroll(1)
                                                        else -> stopAutoScroll()
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                if (draggedItemId == line.id) {
                                                    performDrop()
                                                }
                                            },
                                            onImmediateSave = { triggerImmediateAutoSave() },
                                            coroutineScope = coroutineScope
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Add Field Button - always available, even when every item is checked
                    // (previously nested inside "if (activeItems.isNotEmpty())", so it vanished
                    // once every checklist item was checked off, with no other way to add a line)
                    item(key = "add_field_button") {
                        TextButton(
                            onClick = {
                                val newLine = NoteLine(type = LineType.CHECKLIST, content = "")
                                // Find insertion point - after last active item
                                val insertIndex = lines.indexOfLast { !it.isChecked || it.type == LineType.SEPARATOR } + 1
                                lines.add(insertIndex.coerceAtMost(lines.size), newLine)
                                triggerImmediateAutoSave()

                                coroutineScope.launch {
                                    delay(100)
                                    editingLineId = newLine.id
                                    val newItemIndex = activeItems.indexOfFirst { it.id == newLine.id }
                                    if (newItemIndex >= 0) {
                                        lazyListState.animateScrollToItem(newItemIndex + 1)
                                    }
                                    lineFocusRequesters[newLine.id]?.requestFocus()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.todo_add_field),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.todo_add_field),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    // Completed items section
                    if (completedItems.isNotEmpty()) {
                        item(key = "completed_header") {
                            Text(
                                text = stringResource(R.string.todo_completed_count, completedItems.size),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        itemsIndexed(
                            items = completedItems,
                            key = { _, item -> "completed_${item.id}" }
                        ) { _, line ->
                            CompletedToDoItem(
                                modifier = Modifier.animateItemPlacement(),
                                line = line,
                                isCollaborative = isCollaborative,
                                editorPhotoUrl = line.editorId?.let { collaboratorPhotos[it] },
                                showDelete = revealedDeleteId == line.id,
                                onToggleDelete = {
                                    revealedDeleteId = if (revealedDeleteId == line.id) null else line.id
                                },
                                onCheckChange = { isChecked ->
                                    handleCheckChange(line, isChecked)
                                },
                                onDelete = { deleteLineWithUndo(line) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// Small round avatar for the member who last edited a shared checklist line. Falls
// back to a neutral person glyph when the photo URL is missing or hasn't loaded.
@Composable
private fun EditorAvatar(photoUrl: String?) {
    val ringColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Editor",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
            )
        } else {
            Icon(
                Icons.Default.Person,
                contentDescription = "Editor",
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun PlainTextToDoItem(
    line: NoteLine,
    isEditing: Boolean,
    lineFocusRequesters: MutableMap<String, RichTextFieldController>,
    isCollaborative: Boolean = false,
    editorPhotoUrl: String? = null,
    onTextChange: (String, Int) -> Unit,
    onCheckChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onBackspaceOnEmptyLine: () -> Unit,
    onEnterPressed: () -> Unit,
    onSelectionChange: (String, TextRange) -> Unit,
    onDragStart: () -> Unit,
    onDragOffset: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onImmediateSave: () -> Unit,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    modifier: Modifier = Modifier
) {
    val controller = remember(line.id) { lineFocusRequesters.getOrPut(line.id) { RichTextFieldController() } }
    val cursorColor = if (isCollaborative) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    val displayMetrics = LocalContext.current.resources.displayMetrics
    // The delete X stays hidden until the row is actually being edited - keeps the
    // list visually clean and prevents accidental deletes while scrolling.
    var isRowFocused by remember(line.id) { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .then(
                if (isCollaborative) {
                    Modifier.background(
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drag Handle
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .pointerInput(line.id) {
                    detectDragGestures(
                        onDragStart = { _ -> onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDrag = { _, dragAmount -> onDragOffset(dragAmount.y) }
                    )
                }
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Checkbox
        Checkbox(
            checked = line.isChecked,
            onCheckedChange = onCheckChange,
            modifier = Modifier.padding(end = 8.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = if (isCollaborative)
                    MaterialTheme.colorScheme.secondary
                else
                    MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                checkmarkColor = MaterialTheme.colorScheme.onPrimary
            )
        )

        RichTextField(
            line = line,
            textColor = if (line.isChecked) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            strikeThrough = line.isChecked,
            cursorColor = cursorColor,
            baseFontSizeSp = 16f,
            scaledDensity = displayMetrics.scaledDensity,
            controller = controller,
            onTextChange = { text, cursorPos -> onTextChange(text, cursorPos) },
            onSelectionChange = { start, end -> onSelectionChange(line.id, TextRange(start, end)) },
            onFocusChange = { hasFocus ->
                isRowFocused = hasFocus
                onFocusChange(hasFocus)
                if (!hasFocus && line.content.isNotEmpty()) onImmediateSave()
            },
            onEnterPressed = onEnterPressed,
            onBackspaceOnEmptyLine = onBackspaceOnEmptyLine,
            onBackspaceAtStart = { false },
            hint = "Add task...",
            modifier = Modifier.weight(1f)
        )

        // Editor avatar for collaborative items - shows who last edited this line.
        if (isCollaborative) {
            EditorAvatar(photoUrl = editorPhotoUrl)
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Delete button - only while the row is focused (see isRowFocused above)
        if (isRowFocused) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(32.dp)
                    .padding(start = 4.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Delete item",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun CompletedToDoItem(
    line: NoteLine,
    isCollaborative: Boolean = false,
    editorPhotoUrl: String? = null,
    // Completed rows have no text field to focus, so the delete X is revealed by
    // tapping the row itself. Hoisted so only one row shows its X at a time.
    showDelete: Boolean,
    onToggleDelete: () -> Unit,
    onCheckChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .pointerInput(line.id) {
                detectTapGestures { onToggleDelete() }
            }
            .then(
                if (isCollaborative) {
                    Modifier.background(
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Spacing for drag handle (completed items can't be dragged)
        Spacer(modifier = Modifier.width(40.dp))

        // Checkbox
        Checkbox(
            checked = line.isChecked,
            onCheckedChange = onCheckChange,
            modifier = Modifier.padding(end = 8.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = if (isCollaborative)
                    MaterialTheme.colorScheme.secondary
                else
                    MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                checkmarkColor = MaterialTheme.colorScheme.onPrimary
            )
        )

        // Text with proper annotation handling
        Text(
            text = remember(line.content, line.spanStyles.hashCode()) {
                try {
                    line.toAnnotatedString()
                } catch (e: Exception) {
                    AnnotatedString(line.content)
                }
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textDecoration = TextDecoration.LineThrough
            )
        )

        // Editor avatar for collaborative items - shows who last edited this line.
        if (isCollaborative) {
            EditorAvatar(photoUrl = editorPhotoUrl)
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Delete button - revealed by tapping the row (see showDelete above)
        if (showDelete) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(32.dp)
                    .padding(start = 4.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Delete item",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SeparatorItem(
    text: String,
    onTextChange: (String) -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit = {},
    onDragOffset: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    // The to-do list keeps the explicit handle (expected in a list app); the Notes
    // editor hides it for a cleaner page and reorders via long-press on the divider
    // itself instead.
    showDragHandle: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Use placeholder as default if text is empty
    val dividerPlaceholder = stringResource(R.string.todo_section_divider)
    val defaultText = text.ifEmpty { dividerPlaceholder }
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = defaultText))
    }
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    // Only update from external text when not editing
    LaunchedEffect(text) {
        if (!isFocused) {
            textFieldValue = TextFieldValue(text = text.ifEmpty { dividerPlaceholder })
        }
    }

    // Same keyboard-avoidance as the checklist line fields (RichTextField): without this,
    // a divider near the bottom of the list stays hidden behind the IME once it opens.
    val dividerBringIntoViewRequester = remember { BringIntoViewRequester() }
    val dividerImeVisible = WindowInsets.isImeVisible
    val dividerExtraRect = with(LocalDensity.current) {
        // Fixed card height (64.dp) plus breathing room below it - see RichTextField's
        // matching comment: bringIntoView() with no rect only scrolls the minimum needed
        // to touch the field's own bounds, leaving it flush against the keyboard edge.
        androidx.compose.ui.geometry.Rect(0f, 0f, 1000f, (64.dp + 64.dp).toPx())
    }
    LaunchedEffect(dividerImeVisible, isFocused) {
        if (dividerImeVisible && isFocused) {
            // isImeVisible flips true as the show animation starts, not once it's
            // finished, so wait it out (and re-check once more) instead of scrolling
            // into view mid-animation.
            delay(300)
            dividerBringIntoViewRequester.bringIntoView(dividerExtraRect)
            delay(150)
            dividerBringIntoViewRequester.bringIntoView(dividerExtraRect)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp) // Increased height to accommodate text descenders
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .then(
                if (!showDragHandle) {
                    // Handle-less mode: press-and-hold anywhere on the divider (the
                    // line segments around the label) and drag to reorder it.
                    Modifier.pointerInput("reorder_separator") {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { _, dragAmount -> onDragOffset(dragAmount.y) }
                        )
                    }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isFocused)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 2.dp else 0.dp
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showDragHandle) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { _ -> onDragStart() },
                                onDragEnd = { onDragEnd() },
                                onDrag = { _, dragAmount -> onDragOffset(dragAmount.y) }
                            )
                        }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "Drag to reorder",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Separator with line and text
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = 2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )

                // Use BasicTextField for better control
                Box(
                    modifier = Modifier
                        .widthIn(min = 100.dp, max = 250.dp)
                        .padding(horizontal = 8.dp, vertical = 8.dp), // Add vertical padding here
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            textFieldValue = newValue
                            onTextChange(newValue.text)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .bringIntoViewRequester(dividerBringIntoViewRequester)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused && !isFocused) {
                                    // Select all text when gaining focus
                                    textFieldValue = textFieldValue.copy(
                                        selection = TextRange(0, textFieldValue.text.length)
                                    )
                                }
                                isFocused = focusState.isFocused
                            },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            // onSurface, not primary: dark schemes (Navy, Coffee, Plum...)
                            // have a primary that's nearly invisible against the dark
                            // surface this label sits on.
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                        decorationBox = { innerTextField ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                innerTextField()
                            }
                        }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = 2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
            }

            // Delete button - only while the divider's label field is focused. The
            // 32.dp slot is always reserved so the centered label doesn't shift
            // sideways when the X appears/disappears.
            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                if (isFocused) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Delete separator",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
