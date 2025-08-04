package sk.kubdev.selfnote.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
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
import sk.kubdev.selfnote.ui.theme.NoteColorPalette

// Active formatting for typing - using Int for fontSize
data class ActiveFormatting(
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val color: Color? = null,
    val fontSize: Int? = null
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

// Helper function to build annotated string with active formatting
fun buildAnnotatedStringWithActiveFormatting(
    text: String,
    spans: List<SerializableSpanStyle>,
    activeFormatting: ActiveFormatting,
    cursorPosition: Int
): AnnotatedString {
    return buildAnnotatedString {
        append(text)

        // Apply existing spans
        spans.forEach { span ->
            if (span.start < text.length && span.end <= text.length && span.start < span.end) {
                val spanStyle = SpanStyle(
                    fontWeight = span.fontWeight?.let { FontWeight(it) },
                    fontStyle = span.fontStyle?.let {
                        if (it == "italic") FontStyle.Italic else FontStyle.Normal
                    },
                    textDecoration = span.textDecoration?.let {
                        if (it == "underline") TextDecoration.Underline else null
                    },
                    color = span.color?.let { Color(it) } ?: Color.Unspecified,
                    fontSize = span.fontSize?.sp ?: TextUnit.Unspecified
                )
                addStyle(spanStyle, span.start, span.end)
            }
        }

        // Apply active formatting at cursor position for visual feedback
        if (cursorPosition >= 0 && cursorPosition <= text.length) {
            val previewStart = (cursorPosition - 1).coerceAtLeast(0)
            val previewEnd = cursorPosition

            if (previewStart < previewEnd) {
                val activeStyle = SpanStyle(
                    fontWeight = if (activeFormatting.isBold) FontWeight.Bold else null,
                    fontStyle = if (activeFormatting.isItalic) FontStyle.Italic else null,
                    textDecoration = if (activeFormatting.isUnderline) TextDecoration.Underline else null,
                    color = activeFormatting.color ?: Color.Unspecified,
                    fontSize = activeFormatting.fontSize?.sp ?: TextUnit.Unspecified
                )
                try {
                    addStyle(activeStyle, previewStart, previewEnd)
                } catch (e: Exception) {
                    // Ignore if out of bounds
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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

    // Simple list of lines
    val lines = remember { mutableStateListOf<NoteLine>() }

    // Collaboration states
    var showCollaborationDialog by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var showCollaboratorsDialog by remember { mutableStateOf(false) }
    var collaborators by remember { mutableStateOf<List<CollaboratorInfo>>(emptyList()) }

    // Get collaboration data
    val pendingInvites by viewModel.pendingInvites.collectAsStateWithLifecycle()
    val collaborationError by viewModel.collaborationError.collectAsStateWithLifecycle()

    // FAB menu state
    var fabMenuExpanded by remember { mutableStateOf(false) }

    // Formatting states with proper active formatting tracking
    var isToolbarVisible by remember { mutableStateOf(true) } // ✅ FIXED: Default to true
    var editingLineId by remember { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf(TextRange.Zero) }
    var toggledStyles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var activeColor by remember { mutableStateOf<Color?>(null) }
    var activeFontSize by remember { mutableStateOf(16) }

    // Selection tracking
    var selectedLineId by remember { mutableStateOf<String?>(null) }
    var selectedTextRange by remember { mutableStateOf<TextRange?>(null) }

    // Track cursor position for active formatting
    var currentCursorPosition by remember { mutableStateOf(0) }

    // Title focus
    val titleFocusRequester = remember { FocusRequester() }
    var isTitleFocused by remember { mutableStateOf(false) }
    val lineFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }

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

    // Create computed activeFormatting that properly reflects current state
    val currentActiveFormatting = remember(toggledStyles, activeColor, activeFontSize, selectedLineId, selectedTextRange) {
        ActiveFormatting(
            isBold = toggledStyles.contains("BOLD"),
            isItalic = toggledStyles.contains("ITALIC"),
            isUnderline = toggledStyles.contains("UNDERLINE"),
            color = activeColor,
            fontSize = activeFontSize
        )
    }

    // Auto-save functions for both local and collaborative
    fun triggerImmediateAutoSave() {
        autoSaveJob?.cancel()
        coroutineScope.launch {
            if (isCollaborative && collaborativeNoteId != null) {
                // Update collaborative note
                viewModel.updateCollaborativeNote(collaborativeNoteId, title, lines.toList())
            } else if (shouldSaveOnExit) { // ✅ FIXED: Only save if we should
                // Use triggerAutoSave for local notes
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
            } else if (shouldSaveOnExit) { // ✅ FIXED: Only save if we should
                // Use triggerAutoSave for local notes
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
                    span.end <= insertStart -> span
                    span.start >= insertStart -> {
                        span.copy(
                            start = span.start + lengthDiff,
                            end = span.end + lengthDiff
                        )
                    }
                    span.start < insertStart && span.end > insertStart -> {
                        // Span contains insertion point - extend it
                        span.copy(end = span.end + lengthDiff)
                    }
                    else -> span
                }
            }

            existingSpans.clear()
            existingSpans.addAll(adjustedSpans)

            // Apply active formatting to newly typed characters
            if (toggledStyles.isNotEmpty() || activeColor != null || activeFontSize != 16) {
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
        }

        // Update the line
        lines[lineIndex] = line.copy(
            content = newText,
            spanStyles = cleanupOverlappingSpans(mergeAdjacentSpans(existingSpans))
        )

        triggerDebouncedAutoSave()
    }

    // Function to apply formatting to selected text with toggle
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

    // Delete with undo function with immediate save
    fun deleteLineWithUndo(line: NoteLine) {
        val index = lines.indexOf(line)
        if (index != -1) {
            deletedLine = Pair(index, line)
            lines.remove(line)

            // Remove focus requester
            lineFocusRequesters.remove(line.id)

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
                        val loadedLines = note.content.toNoteLines()

                        lines.clear() // Clear again to be safe
                        if (loadedLines.isNotEmpty()) {
                            lines.addAll(loadedLines)
                        } else {
                            // Only add empty line if there's truly no content
                            lines.add(NoteLine(type = LineType.CHECKLIST, content = ""))
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

                        // Start real-time sync after initial load
                        viewModel.startCollaborativeSync()
                    }
                }
                result.onFailure { error ->
                    // Handle error - maybe show a snackbar
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Failed to load collaborative note: ${error.message}")
                    }
                }
            }
        }
    }

    // Real-time updates - separate LaunchedEffect
    LaunchedEffect(isCollaborative, collaborativeNoteId) {
        if (isCollaborative && collaborativeNoteId != null) {
            // Small delay to ensure initial load completes first
            delay(500)

            // Listen for real-time updates
            viewModel.getCollaborativeNoteFlow(collaborativeNoteId).collect { note ->
                if (note != null && !isUserEditing) {
                    // Only update if content has actually changed
                    val newLines = note.content.toNoteLines()
                    val currentContent = lines.map { it.content }
                    val newContent = newLines.map { it.content }

                    if (currentContent != newContent || title != note.title) {
                        title = note.title
                        lines.clear()
                        lines.addAll(newLines)

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
                    }
                }
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
                                    triggerDebouncedAutoSave()
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
                            if (title.isEmpty()) {
                                Text(
                                    text = if (isCollaborative) "Enter collaborative title..." else "Enter title...",
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

                    // Delete note (only for non-collaborative or if owner)
                    if (noteId != 0 && !isCollaborative) {
                        IconButton(onClick = {
                            viewModel.deleteNoteById(noteId)
                            navController.navigateUp()
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                "Delete",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
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
        },
        floatingActionButton = {
            // FAB for non-collaborative or collaborative with menu
            if (!isCollaborative) {
                // Single FAB for starting collaboration
                FloatingActionButton(
                    onClick = { showCollaborationDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Group, contentDescription = "Start Collaboration")
                }
            } else if (collaborativeNoteId != null) {
                // Expandable FAB menu for collaborative features
                // Expandable FAB menu for collaborative features
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    // Mini FABs when expanded
                    AnimatedVisibility(
                        visible = fabMenuExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Save as local copy mini FAB with label
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    shadowElevation = 2.dp
                                ) {
                                    Text(
                                        text = "Save copy",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                SmallFloatingActionButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            val localTitle = "$title (Local Copy)"
                                            val contentJson = lines.toList().toJson()

                                            viewModel.createNoteWithRandomColor(
                                                title = localTitle,
                                                content = contentJson,
                                                type = NoteType.CHECKLIST
                                            )

                                            snackbarHostState.showSnackbar("Created local copy of the note")
                                        }
                                        fabMenuExpanded = false
                                    },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Save Local Copy"
                                    )
                                }
                            }

                            // Leave collaboration mini FAB with label
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    shadowElevation = 2.dp
                                ) {
                                    Text(
                                        text = "Leave",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                SmallFloatingActionButton(
                                    onClick = {
                                        viewModel.leaveCollaborativeNote(collaborativeNoteId)
                                        navController.navigateUp()
                                    },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ExitToApp,
                                        contentDescription = "Leave"
                                    )
                                }
                            }

                            // Invite members mini FAB with label
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    shadowElevation = 2.dp
                                ) {
                                    Text(
                                        text = "Invite",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                SmallFloatingActionButton(
                                    onClick = {
                                        showInviteDialog = true
                                        fabMenuExpanded = false
                                    },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Icon(
                                        Icons.Default.PersonAdd,
                                        contentDescription = "Invite"
                                    )
                                }
                            }

                            // View members mini FAB with label
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    shadowElevation = 2.dp
                                ) {
                                    Text(
                                        text = "Members",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                SmallFloatingActionButton(
                                    onClick = {
                                        showCollaboratorsDialog = true
                                        fabMenuExpanded = false
                                    },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Icon(
                                        Icons.Default.People,
                                        contentDescription = "Members"
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Main FAB
                    FloatingActionButton(
                        onClick = { fabMenuExpanded = !fabMenuExpanded },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        AnimatedContent(
                            targetState = fabMenuExpanded,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                            label = "fab_icon"
                        ) { expanded ->
                            if (expanded) {
                                Icon(Icons.Default.Close, contentDescription = "Close menu")
                            } else {
                                Icon(Icons.Default.Group, contentDescription = "Collaboration menu")
                            }
                        }
                    }
                }
            }
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
                // Formatting toolbar with proper handlers
                AnimatedVisibility(visible = isToolbarVisible) {
                    FormattingToolbar(
                        toggledStyles = toggledStyles,
                        activeColor = activeColor,
                        activeFontSize = activeFontSize,
                        onStyleToggle = { styleKey ->
                            if (selectedLineId != null && selectedTextRange != null && !selectedTextRange!!.collapsed) {
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
                            if (selectedLineId != null && selectedTextRange != null && !selectedTextRange!!.collapsed) {
                                applyFormattingToSelection(color = color)
                            } else {
                                activeColor = if (activeColor == color) null else color
                            }
                        },
                        onFontSizeChange = { size ->
                            if (selectedLineId != null && selectedTextRange != null && !selectedTextRange!!.collapsed) {
                                applyFormattingToSelection(fontSize = size)
                            } else {
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
                        bottom = 80.dp // Extra padding for FAB
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
                                            isDragging = isDragged,
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
                                            isDragging = isDragged,
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
                                                        val newFocusRequester = lineFocusRequesters.getOrPut(newLine.id) { FocusRequester() }
                                                        delay(50)
                                                        try {
                                                            newFocusRequester.requestFocus()
                                                        } catch (e: Exception) {
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

                        // Add Field Button - only under active items
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
                                    contentDescription = "Add field",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Add field",
                                    style = MaterialTheme.typography.bodyLarge
                                )
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
        }
    }
}

@Composable
fun PlainTextToDoItem(
    line: NoteLine,
    isEditing: Boolean,
    isDragging: Boolean,
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
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    modifier: Modifier = Modifier
) {
    // Get or create focus requester for this line
    val focusRequester = lineFocusRequesters.getOrPut(line.id) { FocusRequester() }

    // Text field value with proper formatting - FIXED initialization
    var textFieldValue by remember(line.id) {
        mutableStateOf(
            TextFieldValue(
                annotatedString = try {
                    line.toAnnotatedString()
                } catch (e: Exception) {
                    AnnotatedString(line.content)
                },
                selection = TextRange(0)
            )
        )
    }

    // Track if we're actively typing
    var isTyping by remember { mutableStateOf(false) }
    var lastCursorPosition by remember { mutableStateOf(0) }

    // Update text field value when line content changes
    LaunchedEffect(line.content, line.spanStyles, isEditing) {
        val annotatedString = if (isEditing && isTyping) {
            // While typing, show with active formatting preview
            buildAnnotatedStringWithActiveFormatting(
                line.content,
                line.spanStyles,
                activeFormatting,
                lastCursorPosition
            )
        } else {
            // Show normal formatting
            try {
                line.toAnnotatedString()
            } catch (e: Exception) {
                AnnotatedString(line.content)
            }
        }

        // Update text field value
        textFieldValue = TextFieldValue(
            annotatedString = annotatedString,
            selection = if (isEditing) {
                TextRange(
                    start = textFieldValue.selection.start.coerceAtMost(annotatedString.length),
                    end = textFieldValue.selection.end.coerceAtMost(annotatedString.length)
                )
            } else {
                TextRange(annotatedString.length)
            }
        )
    }

    // Reset typing state when focus changes
    LaunchedEffect(isEditing) {
        if (!isEditing) {
            isTyping = false
        }
    }

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

        // BasicTextField with formatting support
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newTfv ->
                // Handle enter key
                if (newTfv.text.contains("\n")) {
                    onEnterPressed()
                    return@BasicTextField
                }

                // Handle backspace on empty line
                if (newTfv.text.isEmpty() && textFieldValue.text.isEmpty()) {
                    onBackspaceOnEmptyLine()
                    return@BasicTextField
                }

                // Mark as typing
                isTyping = true
                lastCursorPosition = newTfv.selection.start

                // Build annotated string with active formatting preview
                val annotatedWithPreview = buildAnnotatedStringWithActiveFormatting(
                    newTfv.text,
                    line.spanStyles,
                    activeFormatting,
                    newTfv.selection.start
                )

                // Update text field value with formatting
                textFieldValue = newTfv.copy(
                    annotatedString = annotatedWithPreview
                )

                // Notify parent of changes
                onTextChange(newTfv.text, newTfv.selection.start)
                onSelectionChange(line.id, newTfv.selection)

                // Reset typing state after a delay
                coroutineScope.launch {
                    delay(500)
                    isTyping = false
                }
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    val hadFocus = isEditing
                    onFocusChange(focusState.isFocused)

                    if (hadFocus && !focusState.isFocused) {
                        isTyping = false
                        if (line.content.isNotEmpty()) {
                            onImmediateSave()
                        }
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
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
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

@Composable
fun CompletedToDoItem(
    line: NoteLine,
    isCollaborative: Boolean = false,
    onCheckChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
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

        // Show sync indicator for collaborative items
        if (isCollaborative) {
            Icon(
                Icons.Default.CloudSync,
                contentDescription = "Synced",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeparatorItem(
    text: String,
    isDragging: Boolean = false,
    onTextChange: (String) -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit = {},
    onDragOffset: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Use placeholder as default if text is empty
    val defaultText = text.ifEmpty { "Section divider" }
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = defaultText))
    }
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    // Only update from external text when not editing
    LaunchedEffect(text) {
        if (!isFocused) {
            textFieldValue = TextFieldValue(text = text.ifEmpty { "Section divider" })
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp) // Increased height to accommodate text descenders
            .padding(vertical = 4.dp, horizontal = 8.dp),
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
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
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

            // Delete button
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
