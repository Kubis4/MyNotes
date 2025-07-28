package sk.kubdev.selfnote.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
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
import com.google.firebase.auth.FirebaseAuth
import sk.kubdev.selfnote.CollaborationDialog
import sk.kubdev.selfnote.CollaboratorsDialog
import sk.kubdev.selfnote.FormattingToolbar
import sk.kubdev.selfnote.InviteManagementDialog
import sk.kubdev.selfnote.LineType
import sk.kubdev.selfnote.NoteLine
import sk.kubdev.selfnote.NoteViewModel
import sk.kubdev.selfnote.R
import sk.kubdev.selfnote.SerializableSpanStyle
import sk.kubdev.selfnote.data.remote.local.entities.NoteType
import sk.kubdev.selfnote.toAnnotatedString
import sk.kubdev.selfnote.toJson
import sk.kubdev.selfnote.toNoteLines
import sk.kubdev.selfnote.data.remote.models.CollaboratorInfo
import sk.kubdev.selfnote.data.remote.models.CollaboratorRole
import sk.kubdev.selfnote.data.remote.models.InviteStatus

// Active formatting for typing - using Int for fontSize
data class ActiveFormatting(
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val color: Color? = null,
    val fontSize: Int? = null
)

// User info for collaboration
data class UserInfo(
    val email: String,
    val displayName: String?,
    val color: Color
)

// Helper function to get user initials
fun getInitials(name: String): String {
    return name.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .uppercase()
}

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
    val boldSpans = spans.filter { it.fontWeight != null }.sortedBy { it.start }
    val italicSpans = spans.filter { it.fontStyle != null }.sortedBy { it.start }
    val underlineSpans = spans.filter { it.textDecoration != null }.sortedBy { it.start }
    val fontSizeSpans = spans.filter { it.fontSize != null }.sortedBy { it.start }

    val result = mutableListOf<SerializableSpanStyle>()

    // For color spans, ensure no overlaps (newer spans take precedence)
    val cleanedColorSpans = mutableListOf<SerializableSpanStyle>()
    colorSpans.forEach { newSpan ->
        val overlapping = cleanedColorSpans.filter { existing ->
            (newSpan.start < existing.end && newSpan.end > existing.start)
        }

        if (overlapping.isEmpty()) {
            cleanedColorSpans.add(newSpan)
        } else {
            // Remove overlapping spans and add adjusted versions
            cleanedColorSpans.removeAll(overlapping)

            overlapping.forEach { existing ->
                // Adjust existing span to not overlap with new span
                if (existing.start < newSpan.start) {
                    cleanedColorSpans.add(existing.copy(end = newSpan.start))
                }
                if (existing.end > newSpan.end) {
                    cleanedColorSpans.add(existing.copy(start = newSpan.end))
                }
            }

            cleanedColorSpans.add(newSpan)
        }
    }

    result.addAll(cleanedColorSpans)
    result.addAll(mergeAdjacentSpans(boldSpans))
    result.addAll(mergeAdjacentSpans(italicSpans))
    result.addAll(mergeAdjacentSpans(underlineSpans))
    result.addAll(fontSizeSpans)

    return result.sortedBy { it.start }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToDoListScreen(
    noteIdArg: Int,
    navController: NavController,
    viewModel: NoteViewModel = hiltViewModel(),
    // ✅ NEW: Collaboration parameters
    isCollaborative: Boolean = false,
    collaborativeNoteId: String? = null
) {
    var noteId by remember { mutableStateOf(noteIdArg) }
    var title by remember { mutableStateOf("") }

    // Simple list of lines
    val lines = remember { mutableStateListOf<NoteLine>() }

    // ✅ NEW: Collaboration states
    var showCollaborationDialog by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var showCollaboratorsDialog by remember { mutableStateOf(false) }
    var collaborators by remember { mutableStateOf<List<CollaboratorInfo>>(emptyList()) }

    // ✅ NEW: Get collaboration data
    val collaborativeNotes by viewModel.collaborativeNotes.collectAsStateWithLifecycle()
    val pendingInvites by viewModel.pendingInvites.collectAsStateWithLifecycle()
    val collaborationError by viewModel.collaborationError.collectAsStateWithLifecycle()

    // ✅ FIXED: Formatting states with proper active formatting tracking
    var isToolbarVisible by remember { mutableStateOf(false) }
    var editingLineId by remember { mutableStateOf<String?>(null) }
    var focusedLineIndex by remember { mutableStateOf<Int?>(null) }
    var selection by remember { mutableStateOf(TextRange.Zero) }
    var toggledStyles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var activeColor by remember { mutableStateOf<Color?>(null) }
    var activeFontSize by remember { mutableStateOf(16) }

    // Selection tracking
    var selectedLineId by remember { mutableStateOf<String?>(null) }
    var selectedTextRange by remember { mutableStateOf<TextRange?>(null) }

    // ✅ NEW: Track cursor position for active formatting
    var currentCursorPosition by remember { mutableStateOf(0) }

    // Title focus
    val titleFocusRequester = remember { FocusRequester() }
    var isTitleFocused by remember { mutableStateOf(false) }
    val lineFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Pre-load string resources
    val sectionDividerText = stringResource(R.string.todo_section_divider)
    val itemDeletedMessage = stringResource(R.string.item_deleted)
    val undoLabel = stringResource(R.string.undo)

    // Enhanced drag state - track by ID, not index
    var draggedItemId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var dropTargetId by remember { mutableStateOf<String?>(null) }

    // Auto-scroll state
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

    // Undo state
    var deletedLine by remember { mutableStateOf<Pair<Int, NoteLine>?>(null) }

    // DEBOUNCED AUTOSAVE STATES
    var autoSaveJob by remember { mutableStateOf<Job?>(null) }
    var isUserEditing by remember { mutableStateOf(false) }

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

    // ✅ FIXED: Create computed activeFormatting that properly reflects current state
    val currentActiveFormatting = remember(toggledStyles, activeColor, activeFontSize, selectedLineId, selectedTextRange) {
        ActiveFormatting(
            isBold = toggledStyles.contains("BOLD"),
            isItalic = toggledStyles.contains("ITALIC"),
            isUnderline = toggledStyles.contains("UNDERLINE"),
            color = activeColor,
            fontSize = activeFontSize
        )
    }


    // ✅ NEW: Start collaborative sync
    LaunchedEffect(isCollaborative, collaborativeNoteId) {
        if (isCollaborative && collaborativeNoteId != null) {
            viewModel.startCollaborativeSync()
            // Listen for real-time updates
            viewModel.getCollaborativeNoteFlow(collaborativeNoteId).collect { note ->
                if (note != null && !isUserEditing) {
                    title = note.title
                    val newLines = note.content.toNoteLines()
                    if (lines.map { it.content } != newLines.map { it.content }) {
                        lines.clear()
                        lines.addAll(newLines)
                    }
                }
            }
        }
    }

    // ✅ FIXED: Auto-save functions for both local and collaborative
    fun triggerImmediateAutoSave() {
        autoSaveJob?.cancel()
        coroutineScope.launch {
            if (isCollaborative && collaborativeNoteId != null) {
                // Update collaborative note
                viewModel.updateCollaborativeNote(collaborativeNoteId, title, lines.toList())
            } else {
                // ✅ FIXED: Use triggerAutoSave for local notes
                viewModel.triggerAutoSave(
                    noteId = noteId,
                    title = title,
                    lines = lines,
                    noteType = NoteType.CHECKLIST,
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
                viewModel.updateCollaborativeNote(collaborativeNoteId, title, lines.toList())
            } else {
                // ✅ FIXED: Use triggerAutoSave for local notes
                viewModel.triggerAutoSave(
                    noteId = noteId,
                    title = title,
                    lines = lines,
                    noteType = NoteType.CHECKLIST,
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

    // Function to calculate drop target based on drag position
    fun calculateDropTarget(currentDragOffset: Float): String? {
        val layoutInfo = lazyListState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo

        if (visibleItems.isEmpty() || draggedItem == null) return null

        // Find the dragged item's current visual position
        val draggedItemInfo = visibleItems.find { it.key == draggedItemId }
        if (draggedItemInfo == null) return null

        // Calculate the center position of the dragged item (convert to Float)
        val draggedItemCenter = draggedItemInfo.offset.toFloat() + draggedItemInfo.size.toFloat() / 2 + currentDragOffset

        // Find which item we're hovering over
        for (itemInfo in visibleItems) {
            // Skip non-content items (headers, etc.)
            if (itemInfo.key !is String) continue

            val itemTop = itemInfo.offset.toFloat()
            val itemBottom = (itemInfo.offset + itemInfo.size).toFloat()

            // Check if we're in the drop zone of this item
            if (draggedItemCenter >= itemTop && draggedItemCenter <= itemBottom) {
                // Return the ID of the item we're hovering over
                return itemInfo.key as? String
            }
        }

        return null
    }

    fun performDrop() {
        if (draggedItem != null && dropTargetId != null && draggedItemId != dropTargetId) {
            val fromItem = draggedItem!!
            val toItem = lines.find { it.id == dropTargetId }

            if (toItem != null) {
                val fromIndex = lines.indexOf(fromItem)
                val toIndex = lines.indexOf(toItem)

                if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                    // Calculate if we should place before or after based on drag offset
                    val draggedItemActiveIndex = activeItems.indexOfFirst { it.id == draggedItemId }
                    val targetItemActiveIndex = activeItems.indexOfFirst { it.id == dropTargetId }

                    lines.removeAt(fromIndex)

                    // Adjust target index after removal
                    val adjustedToIndex = if (fromIndex < toIndex) toIndex - 1 else toIndex

                    // Determine final position based on drag direction
                    val finalIndex = when {
                        draggedItemActiveIndex < targetItemActiveIndex -> adjustedToIndex + 1
                        dragOffset > 0 -> adjustedToIndex + 1
                        else -> adjustedToIndex
                    }.coerceIn(0, lines.size)

                    lines.add(finalIndex, fromItem)

                    // Immediate save after reordering
                    triggerImmediateAutoSave()
                }
            }
        }
        draggedItemId = null
        dropTargetId = null
        dragOffset = 0f
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

    // ✅ FIXED: Function to apply formatting to selected text with toggle
    fun applyFormattingToSelection(
        toggleBold: Boolean = false,
        toggleItalic: Boolean = false,
        toggleUnderline: Boolean = false,
        color: Color? = null,
        fontSize: Int? = null
    ) {
        if (selectedLineId != null && selectedTextRange != null) {
            val lineIndex = lines.indexOfFirst { it.id == selectedLineId }
            if (lineIndex != -1) {
                val line = lines[lineIndex]
                val start = selectedTextRange!!.min
                val end = selectedTextRange!!.max

                if (start < end && start >= 0 && end <= line.content.length) {
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

                    // Process FONT SIZE (not a toggle, always sets)
                    if (fontSize != null) {
                        val newSpans = mutableListOf<SerializableSpanStyle>()
                        existingSpans.forEach { span ->
                            if (span.fontSize == null) {
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
                                fontSize = fontSize.toFloat()
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

    // UPDATED: Delete with undo function with immediate save
    fun deleteLineWithUndo(line: NoteLine) {
        val index = lines.indexOf(line)
        if (index != -1) {
            deletedLine = Pair(index, line)
            lines.remove(line)

            // Immediate save when deleting
            triggerImmediateAutoSave()

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
                            // Immediate save when undoing delete
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

    // ✅ FIXED: handleTextChange with proper active formatting application
    fun handleTextChange(lineId: String, newText: String, cursorPos: Int, fontSizeMessage: String? = null) {
        val lineIndex = lines.indexOfFirst { it.id == lineId }
        if (lineIndex == -1) return

        val line = lines[lineIndex]

        // CRITICAL: Avoid processing if text hasn't changed
        if (line.content == newText) return

        // Mark user as editing
        isUserEditing = true
        currentCursorPosition = cursorPos

        // Clear formatting buttons when text becomes empty
        if (newText.isEmpty()) {
            toggledStyles = emptySet()
            activeColor = null
            activeFontSize = 16
            lines[lineIndex] = line.copy(content = newText, spanStyles = emptyList())
            triggerImmediateAutoSave()
            return
        }

        // ✅ FIXED: Better span handling for text changes
        val existingSpans = line.spanStyles.toMutableList()

        // Adjust existing spans based on text length changes
        val lengthDiff = newText.length - line.content.length

        if (lengthDiff != 0) {
            // Text length changed, adjust spans
            val adjustedSpans = existingSpans.mapNotNull { span ->
                when {
                    span.end <= cursorPos -> {
                        // Span is before cursor, keep as is
                        span
                    }
                    span.start >= cursorPos -> {
                        // Span is after cursor, shift by length difference
                        val newStart = (span.start + lengthDiff).coerceAtLeast(0)
                        val newEnd = (span.end + lengthDiff).coerceAtLeast(newStart)
                        if (newStart < newText.length && newEnd <= newText.length) {
                            span.copy(start = newStart, end = newEnd)
                        } else null
                    }
                    else -> {
                        // Span crosses cursor position
                        if (lengthDiff > 0) {
                            // Text was added, extend the span
                            val newEnd = (span.end + lengthDiff).coerceAtMost(newText.length)
                            span.copy(end = newEnd)
                        } else {
                            // Text was removed, shrink the span
                            val newEnd = (span.end + lengthDiff).coerceAtLeast(span.start)
                            if (newEnd > span.start) {
                                span.copy(end = newEnd)
                            } else null
                        }
                    }
                }
            }

            existingSpans.clear()
            existingSpans.addAll(adjustedSpans)
        }

        // ✅ FIXED: Apply active formatting to newly typed text (only for additions)
        if (lengthDiff > 0 && (toggledStyles.isNotEmpty() || activeColor != null || activeFontSize != 16)) {
            val insertStart = cursorPos - lengthDiff
            val insertEnd = cursorPos

            if (insertStart >= 0 && insertStart < insertEnd) {
                // Apply bold
                if (toggledStyles.contains("BOLD")) {
                    existingSpans.add(
                        SerializableSpanStyle(
                            start = insertStart,
                            end = insertEnd,
                            fontWeight = FontWeight.Bold.weight
                        )
                    )
                }

                // Apply italic
                if (toggledStyles.contains("ITALIC")) {
                    existingSpans.add(
                        SerializableSpanStyle(
                            start = insertStart,
                            end = insertEnd,
                            fontStyle = "italic"
                        )
                    )
                }

                // Apply underline
                if (toggledStyles.contains("UNDERLINE")) {
                    existingSpans.add(
                        SerializableSpanStyle(
                            start = insertStart,
                            end = insertEnd,
                            textDecoration = "underline"
                        )
                    )
                }

                // Apply color
                activeColor?.let { color ->
                    existingSpans.add(
                        SerializableSpanStyle(
                            start = insertStart,
                            end = insertEnd,
                            color = color.value
                        )
                    )
                }

                // Apply font size
                if (activeFontSize != 16) {
                    existingSpans.add(
                        SerializableSpanStyle(
                            start = insertStart,
                            end = insertEnd,
                            fontSize = activeFontSize.toFloat()
                        )
                    )
                }
            }
        }

        // Update the line
        lines[lineIndex] = line.copy(
            content = newText,
            spanStyles = cleanupOverlappingSpans(mergeAdjacentSpans(existingSpans))
        )

        // Trigger debounced autosave for continuous typing
        triggerDebouncedAutoSave()
    }

    // UPDATED: Memoized checkbox handler with immediate save
    val handleCheckChange = remember<(NoteLine, Boolean) -> Unit> {
        { line, isChecked ->
            val index = lines.indexOf(line)
            if (index != -1) {
                lines[index] = line.copy(isChecked = isChecked)

                // Immediate save for structural changes
                triggerImmediateAutoSave()

                if (isChecked) {
                    coroutineScope.launch {
                        delay(300)
                        val currentIndex = lines.indexOf(line)
                        if (currentIndex != -1) {
                            val item = lines.removeAt(currentIndex)
                            lines.add(item)
                            // Another immediate save after reordering
                            triggerImmediateAutoSave()
                        }
                    }
                }
            }
        }
    }

    // ✅ FIXED: Load existing note - only when NOT editing and NOT collaborative
    LaunchedEffect(noteIdArg) {
        if (noteIdArg != 0 && !isCollaborative) {
            viewModel.getNoteById(noteIdArg).collectLatest { note ->
                if (note != null) {
                    // CRITICAL: Only update if user is NOT editing
                    if (!isUserEditing) {
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

    // ✅ FIXED: Load collaborative note
    LaunchedEffect(collaborativeNoteId) {
        if (isCollaborative && collaborativeNoteId != null) {
            viewModel.getCollaborativeNoteById(collaborativeNoteId) { result ->
                result.onSuccess { note ->
                    if (note != null) {
                        title = note.title
                        val loadedLines = note.content.toNoteLines()
                        lines.clear()
                        if (loadedLines.isNotEmpty()) {
                            lines.addAll(loadedLines)
                        } else {
                            lines.add(NoteLine(type = LineType.CHECKLIST, content = ""))
                        }

                        // ✅ FIXED: Create CollaboratorInfo objects with correct parameter names
                        collaborators = note.collaborators.mapIndexed { index, userId ->
                            CollaboratorInfo(
                                userId = userId,
                                email = if (userId == note.ownerId) note.lastEditedByEmail else "collaborator${index + 1}@email.com",
                                displayName = if (userId == note.ownerId) "Owner" else "Collaborator ${index + 1}",
                                role = if (userId == note.ownerId) CollaboratorRole.OWNER else CollaboratorRole.EDITOR,
                                invitedAt = note.createdAt,
                                status = InviteStatus.ACCEPTED
                            )
                        }
                    }
                }
            }
        }
    }

    // Initialize with empty line for new notes
    LaunchedEffect(noteIdArg, isCollaborative) {
        if (noteIdArg == 0 && !isCollaborative && lines.isEmpty()) {
            lines.add(NoteLine(type = LineType.CHECKLIST, content = ""))
            delay(100)
            titleFocusRequester.requestFocus()
        }
    }

    // ✅ NEW: Collaboration dialogs
    if (showCollaborationDialog) {
        CollaborationDialog(
            onDismiss = { showCollaborationDialog = false },
            onCreateCollaborative = {
                viewModel.createCollaborativeNote(
                    title = title.ifBlank { "Collaborative Todo" },
                    lines = lines,
                    noteType = NoteType.CHECKLIST
                ) { newCollaborativeId ->
                    // Navigate to the new collaborative note
                    navController.navigate("todo_list/0?isCollaborative=true&collaborativeNoteId=$newCollaborativeId") {
                        popUpTo("todo_list/$noteIdArg") { inclusive = true }
                    }
                }
                showCollaborationDialog = false
            },
            onJoinExisting = {
                // Show pending invites or allow manual joining
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
                    viewModel.sendCollaborationInvite(collaborativeNoteId, email) { success ->
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
                viewModel.declineInvitation(inviteId) { success ->
                    // Handle result
                }
            }
        )
    }

    if (showCollaboratorsDialog) {
        CollaboratorsDialog(
            collaborators = collaborators,
            onDismiss = { showCollaboratorsDialog = false },
            onRemoveCollaborator = { collaboratorEmail ->
                // Handle removal if you're the owner
            }
        )
    }

    // Show collaboration error
    LaunchedEffect(collaborationError) {
        collaborationError?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // ✅ NEW: Show collaboration indicator
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
                                    text = "Collaborative • ${collaborators.size} members",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                )
                            }
                        } else {
                            Text(
                                text = if (noteIdArg == 0) "New Todo" else "Edit Todo",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            )
                        }

                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BasicTextField(
                                value = title,
                                onValueChange = {
                                    title = it
                                    // Immediate save for title changes
                                    triggerImmediateAutoSave()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(titleFocusRequester)
                                    .onFocusChanged { isTitleFocused = it.isFocused },
                                textStyle = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = {
                                    lineFocusRequesters[lines.firstOrNull()?.id]?.requestFocus()
                                }),
                                singleLine = true,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.onPrimary)
                            )
                            if (title.isEmpty() && !isTitleFocused) {
                                Text(
                                    text = if (isCollaborative)
                                        "Enter collaborative title..."
                                    else
                                        "Enter title...",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // ✅ FIXED: Only show collaboration actions if already collaborative
                    if (isCollaborative && collaborativeNoteId != null) {
                        // Show collaborators
                        IconButton(onClick = { showCollaboratorsDialog = true }) {
                            Icon(Icons.Default.Group, "View Collaborators")
                        }

                        // Invite others
                        IconButton(onClick = { showInviteDialog = true }) {
                            Icon(Icons.Default.PersonAdd, "Invite Others")
                        }

                        // Leave collaboration
                        IconButton(onClick = {
                            viewModel.leaveCollaborativeNote(collaborativeNoteId)
                            navController.navigateUp()
                        }) {
                            Icon(Icons.Default.ExitToApp, "Leave Collaboration")
                        }
                    }

                    // Toggle toolbar visibility
                    IconButton(onClick = { isToolbarVisible = !isToolbarVisible }) {
                        Icon(Icons.Default.TextFields, "Toggle Toolbar")
                    }

                    // ✅ FIXED: Only show regular share for non-collaborative notes
                    if (!isCollaborative) {
                        IconButton(onClick = {
                            val shareText = buildString {
                                appendLine(title.ifBlank { "My Todo List" })
                                appendLine()
                                lines.filter { it.type != LineType.SEPARATOR }.forEach { line ->
                                    val prefix = if (line.isChecked) "☑" else "☐"
                                    appendLine("$prefix ${line.content}")
                                }
                            }

                            val intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Todo List"))
                        }) {
                            Icon(Icons.Default.Share, "Share")
                        }
                    }

                    // Delete note (only for non-collaborative or if owner)
                    if (noteId != 0 && !isCollaborative) {
                        IconButton(onClick = {
                            viewModel.deleteNoteById(noteId)
                            navController.navigateUp()
                        }) {
                            Icon(Icons.Default.Delete, "Delete")
                        }
                    }

                    // ✅ FIXED: Save and exit
                    IconButton(onClick = {
                        coroutineScope.launch {
                            if (isCollaborative && collaborativeNoteId != null) {
                                viewModel.updateCollaborativeNote(collaborativeNoteId, title, lines.toList())
                            } else {
                                // ✅ FIXED: Convert to JSON before passing to finaliseAndSave
                                val contentJson = lines.toList().toJson()
                                viewModel.finaliseAndSave(noteId, title, contentJson, NoteType.CHECKLIST).join()
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
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ✅ UPDATED: Formatting toolbar with proper handlers
                AnimatedVisibility(visible = isToolbarVisible) {
                    FormattingToolbar(
                        toggledStyles = toggledStyles,
                        activeColor = activeColor,
                        activeFontSize = activeFontSize,
                        onStyleToggle = { styleKey ->
                            if (selectedLineId != null && selectedTextRange != null && !selectedTextRange!!.collapsed) {
                                // ✅ FIXED: Apply to selection
                                applyFormattingToSelection(
                                    toggleBold = styleKey == "BOLD",
                                    toggleItalic = styleKey == "ITALIC",
                                    toggleUnderline = styleKey == "UNDERLINE"
                                )
                            } else {
                                // Toggle for future typing
                                toggledStyles = if (toggledStyles.contains(styleKey)) {
                                    toggledStyles - styleKey
                                } else {
                                    toggledStyles + styleKey
                                }
                            }
                        },
                        onColorChange = { color ->
                            if (selectedLineId != null && selectedTextRange != null && !selectedTextRange!!.collapsed) {
                                // Apply color to selection
                                applyFormattingToSelection(color = color)
                            } else {
                                // Set color for future typing
                                activeColor = if (activeColor == color) null else color
                            }
                        },
                        onFontSizeChange = { size ->
                            if (selectedLineId != null && selectedTextRange != null && !selectedTextRange!!.collapsed) {
                                // Apply font size to selection
                                applyFormattingToSelection(fontSize = size)
                            } else {
                                // Set font size for future typing
                                activeFontSize = size ?: 16
                            }
                        },
                        onAddSeparator = {
                            val separatorLine = NoteLine(
                                type = LineType.SEPARATOR,
                                content = sectionDividerText
                            )
                            val insertIndex = lines.indexOfFirst { it.isChecked && it.type != LineType.SEPARATOR }
                                .takeIf { it >= 0 } ?: lines.size
                            lines.add(insertIndex, separatorLine)
                            triggerImmediateAutoSave()
                        }
                    )
                }

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
                                text = "Progress",
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
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = 80.dp
                    )
                ) {
                    // Active items section
                    if (activeItems.isNotEmpty()) {
                        item(key = "active_header") {
                            if (completedItems.isNotEmpty()) {
                                Text(
                                    text = "Active ($activeTaskCount)",
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

                            when (line.type) {
                                LineType.SEPARATOR -> {
                                    // ✅ FIXED: Separator with drag handle and matching style
                                    SeparatorItem(
                                        text = line.content.ifEmpty { sectionDividerText },
                                        isDragging = isDragged,
                                        dragOffset = if (isDragged) dragOffset else 0f,
                                        onDelete = { deleteLineWithUndo(line) },
                                        onDragStart = {
                                            draggedItemId = line.id
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragOffset = { offset ->
                                            if (draggedItemId == line.id) {
                                                dragOffset = offset
                                                dropTargetId = calculateDropTarget(offset)

                                                // Auto-scroll logic
                                                val layoutInfo = lazyListState.layoutInfo
                                                val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset

                                                when {
                                                    offset < -viewportHeight * 0.2f -> startAutoScroll(-1)
                                                    offset > viewportHeight * 0.2f -> startAutoScroll(1)
                                                    else -> stopAutoScroll()
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            if (draggedItemId == line.id) {
                                                performDrop()
                                            }
                                        },
                                        modifier = Modifier
                                            .zIndex(if (isDragged) 1f else 0f)
                                            .graphicsLayer {
                                                alpha = if (isDragged) 0.9f else 1f
                                                shadowElevation = if (isDragged) 8.dp.toPx() else 0f
                                            }
                                    )
                                }
                                else -> {
                                    PlainTextToDoItem(
                                        line = line,
                                        isEditing = editingLineId == line.id,
                                        isDragging = isDragged,
                                        dragOffset = if (isDragged) dragOffset else 0f,
                                        activeFormatting = currentActiveFormatting,
                                        lines = lines,
                                        lineFocusRequesters = lineFocusRequesters,
                                        isCollaborative = isCollaborative,
                                        onTextChange = { newText, cursorPos ->
                                            handleTextChange(line.id, newText, cursorPos)
                                        },
                                        onCheckChange = { isChecked ->
                                            handleCheckChange(line, isChecked)
                                        },
                                        onDelete = { deleteLineWithUndo(line) },
                                        onFocusChange = { hasFocus ->
                                            editingLineId = if (hasFocus) line.id else null
                                            if (!hasFocus) {
                                                selectedLineId = null
                                                selectedTextRange = null
                                            }
                                        },
                                        onBackspaceOnEmptyLine = {
                                            if (line.content.isEmpty()) {
                                                deleteLineWithUndo(line)
                                            }
                                        },
                                        onEnterPressed = {
                                            val lineIndex = lines.indexOf(line)
                                            if (lineIndex != -1) {
                                                val newLine = NoteLine(type = LineType.CHECKLIST, content = "")
                                                lines.add(lineIndex + 1, newLine)
                                                // Immediate save when adding new line
                                                triggerImmediateAutoSave()

                                                // ✅ FIXED: Clear current editing first, then set new line
                                                editingLineId = null

                                                coroutineScope.launch {
                                                    delay(100) // Give time for the list to update
                                                    editingLineId = newLine.id

                                                    // ✅ FIXED: Ensure focus requester exists and request focus
                                                    val newFocusRequester = lineFocusRequesters.getOrPut(newLine.id) { FocusRequester() }
                                                    delay(50) // Small delay to ensure the component is composed
                                                    try {
                                                        newFocusRequester.requestFocus()
                                                    } catch (e: Exception) {
                                                        // Fallback: try again after a longer delay
                                                        delay(100)
                                                        newFocusRequester.requestFocus()
                                                    }

                                                    val newItemIndex = activeItems.indexOfFirst { it.id == newLine.id }
                                                    if (newItemIndex >= 0) {
                                                        lazyListState.animateScrollToItem(newItemIndex + 1)
                                                    }
                                                }
                                            }
                                        },
                                        onSelectionChange = { lineId, range ->
                                            selectedLineId = lineId
                                            selectedTextRange = range
                                            selection = range

                                            // ✅ FIXED: Update toolbar state based on selection
                                            if (!range.collapsed && lineId == line.id) {
                                                val lineIndex = lines.indexOfFirst { it.id == lineId }
                                                if (lineIndex != -1) {
                                                    val currentLine = lines[lineIndex]

                                                    // Check what formatting is applied to the selection
                                                    val selectionStart = range.min
                                                    val selectionEnd = range.max

                                                    // Helper function to check if formatting covers entire selection
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

                                                    // Check formatting
                                                    val hasBold = hasFormattingInSelection { it.fontWeight != null }
                                                    val hasItalic = hasFormattingInSelection { it.fontStyle != null }
                                                    val hasUnderline = hasFormattingInSelection { it.textDecoration != null }

                                                    // Update toolbar state
                                                    val newToggledStyles = mutableSetOf<String>()
                                                    if (hasBold) newToggledStyles.add("BOLD")
                                                    if (hasItalic) newToggledStyles.add("ITALIC")
                                                    if (hasUnderline) newToggledStyles.add("UNDERLINE")

                                                    toggledStyles = newToggledStyles

                                                    // Check for color (take the most common color in selection)
                                                    val colorSpans = currentLine.spanStyles.filter { span ->
                                                        span.color != null &&
                                                                span.start < selectionEnd && span.end > selectionStart
                                                    }
                                                    activeColor = colorSpans.firstOrNull()?.let { Color(it.color!!) }

                                                    // Check for font size (take the most common size in selection)
                                                    val fontSizeSpans = currentLine.spanStyles.filter { span ->
                                                        span.fontSize != null &&
                                                                span.start < selectionEnd && span.end > selectionStart
                                                    }
                                                    activeFontSize = fontSizeSpans.firstOrNull()?.fontSize?.toInt() ?: 16
                                                }
                                            }
                                        },
                                        onDragStart = {
                                            if (!line.isChecked || line.type == LineType.SEPARATOR) {
                                                draggedItemId = line.id
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        },
                                        onDragOffset = { offset ->
                                            if (draggedItemId == line.id) {
                                                dragOffset = offset
                                                dropTargetId = calculateDropTarget(offset)

                                                // Auto-scroll logic
                                                val layoutInfo = lazyListState.layoutInfo
                                                val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset

                                                when {
                                                    offset < -viewportHeight * 0.2f -> startAutoScroll(-1)
                                                    offset > viewportHeight * 0.2f -> startAutoScroll(1)
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
                                        modifier = Modifier
                                            .zIndex(if (isDragged) 1f else 0f)
                                            .graphicsLayer {
                                                alpha = if (isDragged) 0.9f else 1f
                                                shadowElevation = if (isDragged) 8.dp.toPx() else 0f
                                            }
                                    )
                                }
                            }
                        }
                    }

                    // Completed items section
                    if (completedItems.isNotEmpty()) {
                        item(key = "completed_header") {
                            Text(
                                text = "Completed (${completedItems.size})",
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
                                line = line,
                                isCollaborative = isCollaborative,
                                onCheckChange = { isChecked ->
                                    handleCheckChange(line, isChecked)
                                },
                                onDelete = { deleteLineWithUndo(line) }
                            )
                        }
                    }
                }
            }

            // ✅ FIXED: FABs positioned correctly using Box
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ✅ FIXED: Collaboration FAB (only when not collaborative)
                if (!isCollaborative) {
                    FloatingActionButton(
                        onClick = { showCollaborationDialog = true },
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Start Collaboration")
                    }
                }

                // ✅ FIXED: Add Item FAB (always visible)
                FloatingActionButton(
                    onClick = {
                        val newLine = NoteLine(type = LineType.CHECKLIST, content = "")
                        lines.add(newLine)
                        // Immediate save when adding new line
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
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Item")
                }
            }
        }
    }

}

@Composable
fun PlainTextToDoItem(
    line: NoteLine,
    isEditing: Boolean,
    isDragging: Boolean,
    dragOffset: Float,
    activeFormatting: ActiveFormatting,
    lines: SnapshotStateList<NoteLine>,
    lineFocusRequesters: MutableMap<String, FocusRequester>,
    isCollaborative: Boolean = false,
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
    modifier: Modifier = Modifier
) {
    // Get or create focus requester for this line
    val focusRequester = lineFocusRequesters.getOrPut(line.id) { FocusRequester() }

    // ✅ FIXED: Stable text field value management
    var textFieldValue by remember(line.id) {
        mutableStateOf(TextFieldValue(""))
    }

    // ✅ FIXED: Better text field value management
    LaunchedEffect(line.content, line.spanStyles.size) {
        // Only update if we're not currently editing or if there's a significant difference
        val currentPlainText = textFieldValue.text

        try {
            val newAnnotatedString = line.toAnnotatedString()

            if (!isEditing || currentPlainText != line.content) {
                // Calculate appropriate cursor position
                val newCursorPos = if (isEditing && currentPlainText.length <= newAnnotatedString.length) {
                    // Try to preserve cursor position
                    textFieldValue.selection.start.coerceAtMost(newAnnotatedString.length)
                } else {
                    // Place cursor at end
                    newAnnotatedString.length
                }

                textFieldValue = TextFieldValue(
                    annotatedString = newAnnotatedString,
                    selection = TextRange(newCursorPos)
                )
            }
        } catch (e: Exception) {
            // Fallback to plain text if annotation fails
            textFieldValue = TextFieldValue(
                text = line.content,
                selection = if (isEditing) TextRange(line.content.length) else TextRange.Zero
            )
        }
    }

    // ✅ FIXED: Focus management
    LaunchedEffect(isEditing) {
        if (isEditing) {
            // When editing starts, ensure cursor is at end if text field is empty
            if (textFieldValue.text.isEmpty()) {
                textFieldValue = textFieldValue.copy(
                    selection = TextRange(line.content.length)
                )
            }
        }
    }

    val currentUser = if (isCollaborative) FirebaseAuth.getInstance().currentUser else null
    val lastEditedBy = if (isCollaborative) {
        when {
            line.content.isEmpty() -> null
            line.content.contains("test") -> UserInfo("test@example.com", "Test User", Color(0xFF4CAF50))
            line.content.length > 20 -> UserInfo("collaborator@gmail.com", "John Doe", Color(0xFF2196F3))
            else -> UserInfo(currentUser?.email ?: "you@gmail.com", currentUser?.displayName ?: "You", Color(0xFFFF9800))
        }
    } else null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = if (isDragging) dragOffset else 0f
                alpha = if (isDragging) 0.8f else 1f
                shadowElevation = if (isDragging) 8.dp.toPx() else 0f
            }
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
                    // ✅ FIXED: Use detectDragGestures instead of detectDragGesturesAfterLongPress
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

        // User avatar for collaborative mode
        if (isCollaborative && lastEditedBy != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(lastEditedBy.color.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getInitials(lastEditedBy.displayName ?: lastEditedBy.email),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

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

        // ✅ FIXED: BasicTextField with proper handling
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newTfv ->
                // Handle enter key
                if (newTfv.text.contains("\n")) {
                    onEnterPressed()
                    return@BasicTextField
                }

                // Handle backspace on empty line
                if (newTfv.text.isEmpty() && textFieldValue.text.isNotEmpty()) {
                    onBackspaceOnEmptyLine()
                    return@BasicTextField
                }

                // ✅ FIXED: Update text field value immediately to prevent flickering
                textFieldValue = newTfv

                // Only trigger text change if content actually changed
                if (newTfv.text != line.content) {
                    onTextChange(newTfv.text, newTfv.selection.start)
                }

                // Always update selection
                onSelectionChange(line.id, newTfv.selection)
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    val hadFocus = isEditing
                    onFocusChange(focusState.isFocused)

                    if (hadFocus && !focusState.isFocused && line.content.isNotEmpty()) {
                        onImmediateSave()
                    }
                },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (line.isChecked)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                else
                    MaterialTheme.colorScheme.onSurface,
                textDecoration = if (line.isChecked) TextDecoration.LineThrough else null
            ),
            cursorBrush = SolidColor(
                if (isCollaborative)
                    MaterialTheme.colorScheme.secondary
                else
                    MaterialTheme.colorScheme.primary
            ),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.None,
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Text
            ),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    if (line.content.isEmpty() && !isEditing) {
                        Text(
                            text = "Add task...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                    innerTextField()
                }
            }
        )

        // Show sync indicator for collaborative items
        if (isCollaborative) {
            Icon(
                Icons.Default.CloudSync,
                contentDescription = "Synced",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        // ✅ FIXED: Delete button with matching separator style
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

@Composable
fun CompletedToDoItem(
    line: NoteLine,
    isCollaborative: Boolean = false,
    onCheckChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val currentUser = if (isCollaborative) FirebaseAuth.getInstance().currentUser else null
    val lastEditedBy = if (isCollaborative) {
        when {
            line.content.isEmpty() -> null
            line.content.contains("test") -> UserInfo("test@example.com", "Test User", Color(0xFF4CAF50))
            line.content.length > 20 -> UserInfo("collaborator@gmail.com", "John Doe", Color(0xFF2196F3))
            else -> UserInfo(currentUser?.email ?: "you@gmail.com", currentUser?.displayName ?: "You", Color(0xFFFF9800))
        }
    } else null

    Row(
        modifier = Modifier
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
        // Spacing for drag handle (completed items can't be dragged)
        Spacer(modifier = Modifier.width(40.dp))

        // User avatar for collaborative mode
        if (isCollaborative && lastEditedBy != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(lastEditedBy.color.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getInitials(lastEditedBy.displayName ?: lastEditedBy.email),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

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

        // ✅ FIXED: Text with proper annotation handling
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

        // Show sync indicator for collaborative items
        if (isCollaborative) {
            Icon(
                Icons.Default.CloudSync,
                contentDescription = "Synced",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Delete button
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

// ✅ FIXED: SeparatorItem with drag handle and matching style
@Composable
fun SeparatorItem(
    text: String,
    isDragging: Boolean = false,
    dragOffset: Float = 0f,
    onDelete: () -> Unit,
    onDragStart: () -> Unit = {},
    onDragOffset: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = if (isDragging) dragOffset else 0f
                alpha = if (isDragging) 0.8f else 1f
                shadowElevation = if (isDragging) 8.dp.toPx() else 0f
            }
            .padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ✅ FIXED: Add drag handle to match other items
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

        // ✅ FIXED: Separator content with proper spacing
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )

        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )

        // ✅ FIXED: Delete button with matching style
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(32.dp)
                .padding(start = 4.dp)
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
