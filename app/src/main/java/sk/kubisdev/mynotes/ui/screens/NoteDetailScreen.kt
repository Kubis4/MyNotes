package sk.kubisdev.mynotes.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.input.pointer.positionChange
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sk.kubisdev.mynotes.CollaborationDialog
import sk.kubisdev.mynotes.CollaboratorsDialog
import sk.kubisdev.mynotes.FormattingToolbar
import sk.kubisdev.mynotes.InviteManagementDialog
import sk.kubisdev.mynotes.data.remote.models.CollaboratorInfo
import sk.kubisdev.mynotes.data.remote.models.CollaboratorRole
import sk.kubisdev.mynotes.data.remote.models.InviteStatus
import sk.kubisdev.mynotes.LineType
import sk.kubisdev.mynotes.NoteLine
import sk.kubisdev.mynotes.NoteViewModel
import sk.kubisdev.mynotes.R
import sk.kubisdev.mynotes.SelectionFormattingSnapshot
import sk.kubisdev.mynotes.SerializableSpanStyle
import sk.kubisdev.mynotes.data.remote.local.entities.NoteType
import sk.kubisdev.mynotes.formattingAtOffset
import sk.kubisdev.mynotes.mergeConsecutiveTextLines
import sk.kubisdev.mynotes.replaceAllSmart
import sk.kubisdev.mynotes.RichTextField
import sk.kubisdev.mynotes.RichTextFieldController
import sk.kubisdev.mynotes.toJson
import sk.kubisdev.mynotes.toNoteLines
import sk.kubisdev.mynotes.ui.components.ColorPickerDialog
import sk.kubisdev.mynotes.ui.components.ReminderDialog
import sk.kubisdev.mynotes.ui.components.rememberNotificationPermissionRequester
import sk.kubisdev.mynotes.ui.components.GradientTopAppBar
import sk.kubisdev.mynotes.ui.theme.NoteColorPalette
import sk.kubisdev.mynotes.ui.theme.notePattern
import androidx.compose.ui.graphics.luminance
import java.io.File
import java.io.FileOutputStream

// Note: ActiveFormatting, mergeAdjacentSpans, cleanupOverlappingSpans and
// buildAnnotatedStringWithActiveFormatting are defined top-level in ToDoListScreen.kt
// (same package) and reused here rather than duplicated.

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NoteDetailScreen(
    noteIdArg: Int,
    navController: NavController,
    viewModel: NoteViewModel,
    // Collaborative mode: same editor, but content loads from / saves to the shared
    // Firestore note and live-syncs with other collaborators (mirrors the pattern
    // ToDoListScreen uses for shared checklists).
    isCollaborative: Boolean = false,
    collaborativeNoteId: String? = null
) {
    var noteId by remember { mutableIntStateOf(noteIdArg) }
    var title by remember { mutableStateOf("") }
    val lines = remember { mutableStateListOf<NoteLine>() }
    var isOwner by remember { mutableStateOf(false) }

    // Collaboration: invite/members live in the overflow menu (the FAB stays the
    // content-insertion menu), mirroring the shared checklist editor's dialogs.
    var showInviteDialog by remember { mutableStateOf(false) }
    var showCollaboratorsDialog by remember { mutableStateOf(false) }
    var showCollaborationDialog by remember { mutableStateOf(false) }
    var collaborators by remember { mutableStateOf<List<CollaboratorInfo>>(emptyList()) }
    val pendingInvites by viewModel.pendingInvites.collectAsStateWithLifecycle()

    // The note's color tints the editor background too (same palette entry as its
    // card in the list), and can be changed from the overflow menu right here.
    var noteColorIndex by remember { mutableIntStateOf(-1) }
    var notePatternIndex by remember { mutableIntStateOf(0) }
    var loadedNote by remember { mutableStateOf<sk.kubisdev.mynotes.data.remote.local.entities.Note?>(null) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    val requestNotificationPermission = rememberNotificationPermissionRequester()

    var isToolbarVisible by remember { mutableStateOf(true) }
    var editingLineId by remember { mutableStateOf<String?>(null) }
    // editingLineId is nulled the instant a text field blurs, but tapping the FAB or
    // the formatting toolbar to insert something causes exactly that blur right
    // before the click handler runs - so by then it's always null and every insert
    // silently fell back to "append at the end". This tracks the same thing but is
    // only ever updated on focus *gain*, so it still reflects "where the cursor last
    // was" even after the field has lost focus to some other control.
    var lastActiveLineId by remember { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf(TextRange.Zero) }
    var toggledStyles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var activeColor by remember { mutableStateOf<Color?>(null) }
    var activeFontSize by remember { mutableStateOf(16) }
    var isTitleFocused by remember { mutableStateOf(false) }
    var isFabExpanded by remember { mutableStateOf(false) }

    var selectedLineId by remember { mutableStateOf<String?>(null) }
    var selectedTextRange by remember { mutableStateOf<TextRange?>(null) }

    // Which tap-to-reveal object (divider/image - things without a focusable text
    // field) currently shows its delete X. Held at screen level so revealing one
    // automatically hides the previously revealed one, matching how focus-based
    // rows (checklist/bullet) naturally behave.
    var revealedDeleteId by remember { mutableStateOf<String?>(null) }

    // Drag-to-reorder state, same scheme as ToDoListScreen's.
    var draggedItemId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var dropTargetId by remember { mutableStateOf<String?>(null) }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

    val titleFocusRequester = remember { FocusRequester() }
    val lineFocusRequesters = remember { mutableMapOf<String, RichTextFieldController>() }
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val itemDeletedMessage = stringResource(R.string.item_deleted)
    val undoLabel = stringResource(R.string.undo)

    var deletedLine by remember { mutableStateOf<Pair<Int, NoteLine>?>(null) }

    var autoSaveJob by remember { mutableStateOf<Job?>(null) }
    var isUserEditing by remember { mutableStateOf(false) }

    fun triggerImmediateAutoSave() {
        autoSaveJob?.cancel()
        coroutineScope.launch {
            if (isCollaborative && collaborativeNoteId != null) {
                // Awaited (not fire-and-forget updateCollaborativeNote): isUserEditing must
                // stay true until the write actually lands, or the live-sync listener below
                // can race a stale snapshot into `lines` mid-edit and yank focus/cursor to a
                // different line.
                viewModel.updateCollaborativeNoteAndAwait(collaborativeNoteId, title, lines.toList())
            } else {
                viewModel.triggerAutoSave(
                    noteId = noteId,
                    title = title,
                    lines = lines.toList(),
                    noteType = NoteType.TEXT,
                    // Persist a color the user picked before the note was ever saved.
                    colorIndex = noteColorIndex.takeIf { it >= 0 },
                    onIdReceived = { newId -> if (noteId == 0) noteId = newId }
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
                isUserEditing = false
                return@launch
            }
            viewModel.triggerAutoSave(
                noteId = noteId,
                title = title,
                lines = lines.toList(),
                noteType = NoteType.TEXT,
                colorIndex = noteColorIndex.takeIf { it >= 0 },
                onIdReceived = { newId -> if (noteId == 0) noteId = newId }
            )
            isUserEditing = false
        }
    }

    fun handleTextChange(lineId: String, newText: String, cursorPos: Int) {
        val lineIndex = lines.indexOfFirst { it.id == lineId }
        if (lineIndex == -1) return
        val line = lines[lineIndex]
        if (line.content == newText) return

        // Markdown-style shortcuts, so lists and dividers can be started straight
        // from the keyboard without reaching for the FAB or toolbar:
        //   "__"        -> divider (same idea as "---" in most markdown editors)
        //   "- " / "* " -> bullet item
        //   "[]"        -> checklist item
        //   "# "        -> heading
        //   "> "        -> quote
        //   "1. "       -> numbered list item
        // Consecutive plain paragraphs live in ONE NoteLine, joined by "\n"
        // (mergeConsecutiveTextLines), so this fires whenever the paragraph the
        // cursor is currently in - wherever it sits inside that block, not just at
        // the very start/end - is blank apart from the just-typed trigger. That's
        // what makes the shortcut work in the middle of an existing note too, not
        // only on a brand-new blank line.
        if (line.type == LineType.TEXT) {
            val triggers = listOf(
                "__" to LineType.DIVIDER,
                "- " to LineType.BULLET,
                "* " to LineType.BULLET,
                "[]" to LineType.CHECKLIST,
                "[ ]" to LineType.CHECKLIST,
                "# " to LineType.HEADING,
                "> " to LineType.QUOTE,
                "1. " to LineType.NUMBERED
            )

            val paragraphStart = newText.lastIndexOf('\n', (cursorPos - 1).coerceAtLeast(0))
                .let { if (it == -1) 0 else it + 1 }
            val nextNewline = newText.indexOf('\n', cursorPos)
            val paragraphEnd = if (nextNewline == -1) newText.length else nextNewline

            val matchedTrigger = if (cursorPos == paragraphEnd) {
                val paragraph = newText.substring(paragraphStart, paragraphEnd)
                triggers.firstOrNull { (pattern, _) -> paragraph == pattern }
            } else null

            if (matchedTrigger != null) {
                val (_, targetType) = matchedTrigger
                val beforeText = if (paragraphStart > 0) newText.substring(0, paragraphStart - 1) else ""
                val afterText = if (paragraphEnd < newText.length) newText.substring(paragraphEnd + 1) else ""
                // beforeText is an untouched prefix of the old content, so its spans carry
                // over unchanged; afterText is an untouched suffix of it, just shifted by
                // however much longer/shorter the old content was.
                val oldAfterStart = line.content.length - afterText.length
                val afterSpans = line.spanStyles.mapNotNull { span ->
                    if (span.start >= oldAfterStart) {
                        span.copy(start = span.start - oldAfterStart, end = span.end - oldAfterStart)
                    } else null
                }

                val newLines = mutableListOf<NoteLine>()
                if (beforeText.isNotEmpty()) {
                    newLines.add(
                        line.copy(
                            content = beforeText,
                            spanStyles = line.spanStyles.filter { it.end <= beforeText.length }
                        )
                    )
                }
                val convertedLine = NoteLine(type = targetType, content = "")
                newLines.add(convertedLine)

                val focusLine: NoteLine
                if (targetType == LineType.DIVIDER) {
                    // A divider isn't editable prose, so make sure there's a text line
                    // right after it to land the cursor on and keep typing.
                    focusLine = if (afterText.isNotEmpty()) {
                        NoteLine(type = LineType.TEXT, content = afterText, spanStyles = afterSpans)
                    } else {
                        NoteLine(type = LineType.TEXT, content = "")
                    }
                    newLines.add(focusLine)
                } else {
                    focusLine = convertedLine
                    if (afterText.isNotEmpty()) {
                        newLines.add(NoteLine(type = LineType.TEXT, content = afterText, spanStyles = afterSpans))
                    }
                }

                lines.removeAt(lineIndex)
                lines.addAll(lineIndex, newLines)
                triggerImmediateAutoSave()
                coroutineScope.launch {
                    delay(100)
                    editingLineId = focusLine.id
                    try {
                        lineFocusRequesters.getOrPut(focusLine.id) { RichTextFieldController() }.requestFocus()
                    } catch (e: Exception) {
                        // Row not composed yet; user can tap it
                    }
                }
                return
            }
        }

        isUserEditing = true

        val existingSpans = line.spanStyles.toMutableList()
        val lengthDiff = newText.length - line.content.length

        if (lengthDiff < 0) {
            val deleteStart = cursorPos
            val deleteEnd = cursorPos - lengthDiff
            val adjustedSpans = existingSpans.mapNotNull { span ->
                when {
                    span.end <= deleteStart -> span
                    span.start >= deleteEnd -> span.copy(
                        start = (span.start + lengthDiff).coerceAtLeast(0),
                        end = (span.end + lengthDiff).coerceAtLeast(0)
                    )
                    span.start < deleteStart && span.end > deleteEnd -> span.copy(end = (span.end + lengthDiff).coerceAtLeast(span.start))
                    span.start >= deleteStart && span.start < deleteEnd && span.end > deleteEnd -> span.copy(
                        start = deleteStart,
                        end = (span.end + lengthDiff).coerceAtLeast(deleteStart)
                    )
                    span.end > deleteStart && span.end <= deleteEnd && span.start < deleteStart -> span.copy(end = deleteStart)
                    span.start >= deleteStart && span.end <= deleteEnd -> null
                    else -> null
                }
            }.filter { it.start < newText.length && it.end <= newText.length && it.start < it.end }
            existingSpans.clear()
            existingSpans.addAll(adjustedSpans)
        } else if (lengthDiff > 0) {
            val insertStart = (cursorPos - lengthDiff).coerceAtLeast(0)
            val insertEnd = cursorPos
            val adjustedSpans = existingSpans.map { span ->
                when {
                    span.end < insertStart -> span
                    span.start >= insertStart -> span.copy(start = span.start + lengthDiff, end = span.end + lengthDiff)
                    // span.end == insertStart falls in here too: typing right at the end of a
                    // formatted run (e.g. continuing bold text) should keep extending that
                    // run's formatting instead of the new characters silently falling outside it.
                    span.start < insertStart && span.end >= insertStart -> span.copy(end = span.end + lengthDiff)
                    else -> span
                }
            }
            existingSpans.clear()
            existingSpans.addAll(adjustedSpans)

            if (toggledStyles.isNotEmpty() || activeColor != null || activeFontSize != 16) {
                if (insertStart < insertEnd && insertStart >= 0 && insertEnd <= newText.length) {
                    if (toggledStyles.contains("BOLD")) {
                        existingSpans.add(sk.kubisdev.mynotes.SerializableSpanStyle(insertStart, insertEnd, fontWeight = FontWeight.Bold.weight))
                    }
                    if (toggledStyles.contains("ITALIC")) {
                        existingSpans.add(sk.kubisdev.mynotes.SerializableSpanStyle(insertStart, insertEnd, fontStyle = "italic"))
                    }
                    if (toggledStyles.contains("UNDERLINE")) {
                        existingSpans.add(sk.kubisdev.mynotes.SerializableSpanStyle(insertStart, insertEnd, textDecoration = "underline"))
                    }
                    activeColor?.let { color ->
                        existingSpans.add(sk.kubisdev.mynotes.SerializableSpanStyle(insertStart, insertEnd, color = color.value))
                    }
                    if (activeFontSize != 16) {
                        existingSpans.add(sk.kubisdev.mynotes.SerializableSpanStyle(insertStart, insertEnd, fontSize = activeFontSize.toFloat()))
                    }
                }
            }
        }

        lines[lineIndex] = line.copy(
            content = newText,
            spanStyles = cleanupOverlappingSpans(mergeAdjacentSpans(existingSpans))
        )
        triggerDebouncedAutoSave()
    }

    fun applyFormattingToSelection(
        toggleBold: Boolean = false,
        toggleItalic: Boolean = false,
        toggleUnderline: Boolean = false,
        color: Color? = null,
        fontSize: Int? = null
    ) {
        // lastActiveLineId/selection (not selectedLineId/selectedTextRange) deliberately:
        // the color and font-size pickers are DropdownMenus, which - unlike a single-tap
        // toggle button - are focusable popups that steal Android view focus the instant
        // they open, blurring the text field and nulling selectedLineId/selectedTextRange
        // before the user ever picks an item from the menu. lastActiveLineId/selection are
        // only ever updated on selection change, never cleared on blur, so they still hold
        // the selection the user actually made.
        val lineId = lastActiveLineId ?: return
        val range = selection
        val lineIndex = lines.indexOfFirst { it.id == lineId }
        if (lineIndex == -1) return
        val line = lines[lineIndex]
        val start = range.min
        val end = range.max
        if (start >= end || start < 0 || end > line.content.length) return

        // Same race as the image-insert path below: the collaborative live-sync
        // listener is only guarded by !isUserEditing, so a Firestore snapshot from
        // just before this formatting change can land while triggerImmediateAutoSave()
        // is still in flight and revert `lines` right back to the pre-change state.
        isUserEditing = true

        val existingSpans = line.spanStyles.toMutableList()

        fun hasFormattingInEntireSelection(checkSpan: (sk.kubisdev.mynotes.SerializableSpanStyle) -> Boolean): Boolean {
            val positions = BooleanArray(end - start) { false }
            existingSpans.forEach { span ->
                if (checkSpan(span)) {
                    val overlapStart = maxOf(span.start, start)
                    val overlapEnd = minOf(span.end, end)
                    if (overlapStart < overlapEnd) {
                        for (i in overlapStart until overlapEnd) if (i >= start && i < end) positions[i - start] = true
                    }
                }
            }
            return positions.all { it }
        }

        fun toggleSpanProperty(has: Boolean, matches: (sk.kubisdev.mynotes.SerializableSpanStyle) -> Boolean, add: () -> sk.kubisdev.mynotes.SerializableSpanStyle) {
            if (has) {
                val newSpans = mutableListOf<sk.kubisdev.mynotes.SerializableSpanStyle>()
                existingSpans.forEach { span ->
                    if (!matches(span)) {
                        newSpans.add(span)
                    } else if (span.end <= start || span.start >= end) {
                        newSpans.add(span)
                    } else if (span.start < start && span.end > end) {
                        newSpans.add(span.copy(end = start)); newSpans.add(span.copy(start = end))
                    } else if (span.start < start && span.end > start) {
                        newSpans.add(span.copy(end = start))
                    } else if (span.start < end && span.end > end) {
                        newSpans.add(span.copy(start = end))
                    }
                }
                existingSpans.clear(); existingSpans.addAll(newSpans)
            } else {
                existingSpans.add(add())
            }
        }

        if (toggleBold) {
            toggleSpanProperty(hasFormattingInEntireSelection { it.fontWeight != null }, { it.fontWeight != null }) {
                sk.kubisdev.mynotes.SerializableSpanStyle(start, end, fontWeight = FontWeight.Bold.weight)
            }
        }
        if (toggleItalic) {
            toggleSpanProperty(hasFormattingInEntireSelection { it.fontStyle != null }, { it.fontStyle != null }) {
                sk.kubisdev.mynotes.SerializableSpanStyle(start, end, fontStyle = "italic")
            }
        }
        if (toggleUnderline) {
            toggleSpanProperty(hasFormattingInEntireSelection { it.textDecoration != null }, { it.textDecoration != null }) {
                sk.kubisdev.mynotes.SerializableSpanStyle(start, end, textDecoration = "underline")
            }
        }
        if (color != null) {
            val newSpans = existingSpans.filter { it.color == null || it.end <= start || it.start >= end }.toMutableList()
            newSpans.add(sk.kubisdev.mynotes.SerializableSpanStyle(start, end, color = color.value))
            existingSpans.clear(); existingSpans.addAll(newSpans)
        }
        if (fontSize != null) {
            val newSpans = existingSpans.filter { it.fontSize == null || it.end <= start || it.start >= end }.toMutableList()
            newSpans.add(sk.kubisdev.mynotes.SerializableSpanStyle(start, end, fontSize = fontSize.toFloat()))
            existingSpans.clear(); existingSpans.addAll(newSpans)
        }

        lines[lineIndex] = line.copy(spanStyles = cleanupOverlappingSpans(mergeAdjacentSpans(existingSpans)))
        triggerImmediateAutoSave()
    }

    // Enter within a TEXT line just inserts "\n" in the same field (see RichTextField's
    // afterTextChanged), so multiple paragraphs typed in one sitting stay one NoteLine. But a
    // line added via the "+" menu, or one that arrived as a separate block from a collaborator,
    // is a genuinely separate NoteLine/field - and Backspace at position 0 there can't flow
    // into the previous field on its own. Without this, backspacing right at the start of such
    // a line does nothing, which reads as the note being stuck.
    fun mergeLineWithPrevious(lineId: String): Boolean {
        val index = lines.indexOfFirst { it.id == lineId }
        if (index <= 0) return false
        val line = lines[index]
        if (line.type != LineType.TEXT) return false
        val prevIndex = index - 1
        val prevLine = lines[prevIndex]
        if (prevLine.type != LineType.TEXT) return false

        val offset = prevLine.content.length
        val mergedSpans = prevLine.spanStyles.toMutableList()
        mergedSpans.addAll(line.spanStyles.map { it.copy(start = it.start + offset, end = it.end + offset) })
        val mergedLine = prevLine.copy(content = prevLine.content + line.content, spanStyles = mergedSpans)

        lines[prevIndex] = mergedLine
        lines.removeAt(index)
        lineFocusRequesters.remove(line.id)
        editingLineId = mergedLine.id
        lastActiveLineId = mergedLine.id
        triggerImmediateAutoSave()

        coroutineScope.launch {
            delay(100)
            try {
                lineFocusRequesters.getOrPut(mergedLine.id) { RichTextFieldController() }.requestFocus()
            } catch (e: Exception) {
                // Row not composed yet; user can tap it
            }
        }
        return true
    }

    fun deleteLineWithUndo(line: NoteLine) {
        val index = lines.indexOf(line)
        if (index == -1) return
        lines.remove(line)
        lineFocusRequesters.remove(line.id)
        triggerImmediateAutoSave()

        // No undo toast for lines with nothing in them - deleting empty lines happens
        // constantly while typing (backspace on an empty paragraph/checklist), and a
        // 4-second snackbar popping up over the keyboard on every one of those was
        // pure noise. Only content that could actually be lost earns the Undo offer.
        // (Images always do: their "content" is a file URI, never blank.)
        if (line.content.isBlank() && line.type != LineType.IMAGE) return
        deletedLine = Pair(index, line)

        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = itemDeletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                deletedLine?.let { (oldIndex, oldLine) ->
                    lines.add(oldIndex.coerceIn(0, lines.size), oldLine)
                    triggerImmediateAutoSave()
                }
            } else {
                deletedLine = null
            }
        }
    }

    val draggedItem by remember {
        derivedStateOf { draggedItemId?.let { id -> lines.find { it.id == id } } }
    }

    fun startAutoScroll(direction: Int) {
        autoScrollJob?.cancel()
        autoScrollJob = coroutineScope.launch {
            while (isActive) {
                val currentIndex = lazyListState.firstVisibleItemIndex
                val targetIndex = (currentIndex + direction).coerceIn(0, lazyListState.layoutInfo.totalItemsCount - 1)
                if (targetIndex != currentIndex) {
                    try {
                        lazyListState.animateScrollToItem(targetIndex)
                    } catch (e: Exception) {
                        // Handle edge cases
                    }
                }
                delay(300)
            }
        }
    }

    fun stopAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = null
    }

    fun calculateDropTarget(currentDragOffset: Float): String? {
        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty() || draggedItem == null) return null

        val draggedItemInfo = visibleItems.find { it.key == draggedItemId } ?: return null
        val draggedItemCenter = draggedItemInfo.offset + currentDragOffset + draggedItemInfo.size / 2

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

    fun performDrop() {
        if (draggedItem != null && dropTargetId != null && draggedItemId != dropTargetId) {
            val fromIndex = lines.indexOfFirst { it.id == draggedItemId }
            val toIndex = lines.indexOfFirst { it.id == dropTargetId }
            if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                val item = lines.removeAt(fromIndex)
                lines.add(toIndex.coerceIn(0, lines.size), item)
                triggerImmediateAutoSave()
            }
        }
        draggedItemId = null
        dropTargetId = null
        dragOffset = 0f
        stopAutoScroll()
    }

    fun addLine(afterId: String? = null, type: LineType = LineType.TEXT) {
        // Inserting onto an empty paragraph converts that line in place - otherwise
        // the new item lands one row below and leaves a stray blank line above it
        // (most visible on a brand-new note whose only line is the empty seed).
        val currentIndex = afterId?.let { lines.indexOfFirst { l -> l.id == it } } ?: -1
        val current = lines.getOrNull(currentIndex)
        if (current != null && type != LineType.TEXT &&
            current.type == LineType.TEXT && current.content.isEmpty()
        ) {
            lines[currentIndex] = current.copy(type = type)
            triggerImmediateAutoSave()
            coroutineScope.launch {
                delay(100)
                editingLineId = current.id
                lazyListState.animateScrollToItem(currentIndex)
                lineFocusRequesters.getOrPut(current.id) { RichTextFieldController() }.requestFocus()
            }
            return
        }

        val newLine = NoteLine(type = type, content = "")
        val insertIndex = afterId?.let { lines.indexOfFirst { l -> l.id == it } + 1 } ?: lines.size
        lines.add(insertIndex.coerceIn(0, lines.size), newLine)
        triggerImmediateAutoSave()

        coroutineScope.launch {
            delay(100)
            editingLineId = newLine.id
            lazyListState.animateScrollToItem(insertIndex.coerceIn(0, lines.size - 1))
            lineFocusRequesters.getOrPut(newLine.id) { RichTextFieldController() }.requestFocus()
        }
    }

    @Composable
    fun DropIndicator() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .padding(horizontal = 16.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Without this, the collaborative live-sync listener (guarded by
            // !isUserEditing) can race this add: a Firestore snapshot from just
            // before the image was inserted arrives while triggerImmediateAutoSave()
            // is still in flight, overwrites `lines` back to the image-less version,
            // and that gets pushed to Firestore right after - permanently dropping the
            // image server-side even though it still looked fine in this session.
            isUserEditing = true
            val permanentUri = saveImageToInternalStorage(context, it)
            val newLine = NoteLine(type = LineType.IMAGE, content = permanentUri.toString())
            val insertIndex = lastActiveLineId?.let { id -> lines.indexOfFirst { l -> l.id == id } + 1 } ?: lines.size
            lines.add(insertIndex.coerceIn(0, lines.size), newLine)
            triggerImmediateAutoSave()
        }
    }

    LaunchedEffect(noteIdArg) {
        if (noteIdArg != 0 && !isCollaborative) {
            viewModel.getNoteById(noteIdArg).collectLatest { note ->
                if (note != null && !isUserEditing) {
                    loadedNote = note
                    noteColorIndex = note.colorIndex
                    notePatternIndex = note.patternIndex
                    if (title != note.title) title = note.title
                    val loadedLines = try {
                        note.content.toNoteLines()
                    } catch (e: Exception) {
                        listOf(NoteLine(content = note.content, type = LineType.TEXT))
                    }.mergeConsecutiveTextLines()
                    lines.clear()
                    if (loadedLines.isNotEmpty()) lines.addAll(loadedLines) else lines.add(NoteLine(type = LineType.TEXT, content = ""))
                }
            }
        }
    }

    // Same placeholder identity scheme as ToDoListScreen: the backend only stores
    // collaborator userIds, so everyone but the signed-in user gets a generic label.
    fun mapCollaborators(note: sk.kubisdev.mynotes.data.remote.models.CollaborativeNote): List<CollaboratorInfo> {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        return note.collaborators.mapIndexed { index, userId ->
            CollaboratorInfo(
                userId = userId,
                email = if (userId == currentUser?.uid) {
                    currentUser?.email ?: "user@email.com"
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

    // Collaborative: initial load, then live sync - the same replaceAllSmart
    // technique as the shared checklist editor, so a Firestore echo of your own
    // keystroke only recomposes the line that actually changed.
    LaunchedEffect(collaborativeNoteId) {
        if (isCollaborative && collaborativeNoteId != null) {
            noteColorIndex = sk.kubisdev.mynotes.CollabLocalPrefs.getColorIndex(context, collaborativeNoteId)
            notePatternIndex = sk.kubisdev.mynotes.CollabLocalPrefs.getPatternIndex(context, collaborativeNoteId)
            viewModel.getCollaborativeNoteById(collaborativeNoteId) { result ->
                result.onSuccess { note ->
                    if (note != null) {
                        title = note.title
                        isOwner = note.ownerId == com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                        collaborators = mapCollaborators(note)
                        val loadedLines = note.content.toNoteLines().mergeConsecutiveTextLines()
                        lines.clear()
                        if (loadedLines.isNotEmpty()) {
                            lines.addAll(loadedLines)
                        } else {
                            lines.add(NoteLine(type = LineType.TEXT, content = ""))
                            // Push the seeded placeholder to Firestore immediately so the
                            // live-sync listener's first snapshot (500ms later) already
                            // matches local state - otherwise it sees the still-blank
                            // remote doc and wipes this placeholder back to zero lines
                            // before the user gets a chance to type.
                            triggerImmediateAutoSave()
                        }
                        viewModel.startCollaborativeSync()
                    }
                }
            }
        }
    }

    LaunchedEffect(isCollaborative, collaborativeNoteId) {
        if (isCollaborative && collaborativeNoteId != null) {
            delay(500)
            // try/catch is defense-in-depth: the flow itself no longer rethrows
            // Firestore permission errors (e.g. right after leaving/being removed
            // from this note), but a collect{} that crashes the whole app on any
            // future stream error is one bad state away regardless.
            try {
                viewModel.getCollaborativeNoteFlow(collaborativeNoteId).collect { note ->
                    if (note != null && !isUserEditing) {
                        val newLines = note.content.toNoteLines().mergeConsecutiveTextLines()
                        if (lines.map { it.content } != newLines.map { it.content } || title != note.title) {
                            title = note.title
                            isOwner = note.ownerId == com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                            lines.replaceAllSmart(newLines)
                        }
                        collaborators = mapCollaborators(note)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("NoteDetailScreen", "Real-time collaborative note listener failed", e)
            }
        }
    }

    LaunchedEffect(noteIdArg) {
        if (noteIdArg == 0 && !isCollaborative && lines.isEmpty()) {
            lines.add(NoteLine(type = LineType.TEXT, content = ""))
            delay(300)
            titleFocusRequester.requestFocus()
        }
    }

    Scaffold(
        // The note tint must be the CONTAINER color, not just the content Column's
        // background: content is inset above the gesture-navigation area, so a
        // background applied there leaves a default-colored (white in light theme)
        // strip at the very bottom edge of the screen.
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
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            // Long titles shrink and may wrap to a second line instead of
                            // being clipped at the bar's edge.
                            val titleFontSize = when {
                                title.length > 45 -> 14.sp
                                title.length > 28 -> 17.sp
                                else -> MaterialTheme.typography.titleLarge.fontSize
                            }
                            BasicTextField(
                                value = title,
                                onValueChange = { title = it; triggerDebouncedAutoSave() },
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
                            if (title.isEmpty() && !isTitleFocused) {
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
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { isToolbarVisible = !isToolbarVisible }) {
                        Icon(Icons.Default.Title, null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            if (isCollaborative && collaborativeNoteId != null) {
                                viewModel.updateCollaborativeNote(collaborativeNoteId, title, lines.toList())
                            } else {
                                viewModel.finaliseAndSave(noteId, title, lines.toList().toJson(), NoteType.TEXT, noteColorIndex.takeIf { it >= 0 }).join()
                            }
                            navController.navigateUp()
                        }
                    }) {
                        Icon(Icons.Default.Done, stringResource(R.string.action_save), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    // Share/Delete live in an overflow menu: four inline icons were
                    // eating so much bar width the note title got truncated.
                    var showOverflowMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.more_options), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_share)) },
                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    val shareText = buildString {
                                        if (title.isNotBlank()) {
                                            appendLine(title)
                                            appendLine()
                                        }
                                        var numberedCounter = 0
                                        lines.forEach { l ->
                                            if (l.type != LineType.NUMBERED) numberedCounter = 0
                                            when (l.type) {
                                                LineType.TEXT -> appendLine(l.content)
                                                LineType.BULLET -> appendLine("• ${l.content}")
                                                LineType.CHECKLIST -> appendLine(if (l.isChecked) "☑ ${l.content}" else "☐ ${l.content}")
                                                LineType.SEPARATOR -> appendLine("――――――")
                                                LineType.DIVIDER -> appendLine("――――――")
                                                LineType.IMAGE -> Unit
                                                LineType.HEADING -> appendLine(l.content)
                                                LineType.QUOTE -> appendLine("❝ ${l.content}")
                                                LineType.NUMBERED -> {
                                                    numberedCounter++
                                                    appendLine("$numberedCounter. ${l.content}")
                                                }
                                            }
                                        }
                                    }.trimEnd()
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        if (title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
                                    }
                                    context.startActivity(Intent.createChooser(intent, null))
                                }
                            )
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
                            // Color is available even before the first save: the pick is kept
                            // in noteColorIndex and written out with the note on autosave/Done.
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.note_color)) },
                                leadingIcon = { Icon(Icons.Default.Palette, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showColorPicker = true
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
                                        viewModel.saveCollaborativeNoteLocally(
                                            title = context.getString(R.string.collab_local_copy_title, title),
                                            lines = lines.toList(),
                                            noteType = NoteType.TEXT
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
                                // Reminder stays visible even before the note is saved, but
                                // greyed: a reminder needs a saved note id to schedule the
                                // alarm, so tapping it while unsaved just explains why.
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
                                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
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
                    if (isToolbarVisible) {
                        FormattingToolbar(
                            toggledStyles = toggledStyles,
                            activeColor = activeColor,
                            activeFontSize = activeFontSize,
                            onStyleToggle = { styleKey ->
                                if (lastActiveLineId != null && !selection.collapsed) {
                                    applyFormattingToSelection(
                                        toggleBold = styleKey == "BOLD",
                                        toggleItalic = styleKey == "ITALIC",
                                        toggleUnderline = styleKey == "UNDERLINE"
                                    )
                                } else {
                                    toggledStyles = if (toggledStyles.contains(styleKey)) toggledStyles - styleKey else toggledStyles + styleKey
                                }
                            },
                            onColorChange = { color ->
                                if (lastActiveLineId != null && !selection.collapsed) {
                                    applyFormattingToSelection(color = color)
                                } else {
                                    activeColor = if (color == Color.Unspecified) null else color
                                }
                            },
                            onFontSizeChange = { size ->
                                if (lastActiveLineId != null && !selection.collapsed) {
                                    applyFormattingToSelection(fontSize = size ?: 16)
                                } else {
                                    activeFontSize = size ?: 16
                                }
                            },
                            onAddSeparator = {
                                addLine(lastActiveLineId, LineType.DIVIDER)
                            },
                            showSeparatorButton = false,
                            drawOwnBackground = false
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            MultiFloatingActionButton(
                isExpanded = isFabExpanded,
                onFabClick = { isFabExpanded = !isFabExpanded },
                // Rides above the keyboard, so inserting a checklist/image mid-typing
                // doesn't require closing the IME first.
                modifier = Modifier.imePadding(),
                items = buildList {
                    add(FabButtonItem(Icons.Default.Subject, "Paragraph") {
                        addLine(lastActiveLineId, LineType.TEXT)
                        isFabExpanded = false
                    })
                    add(FabButtonItem(Icons.Default.List, "Bullet list") {
                        addLine(lastActiveLineId, LineType.BULLET)
                        isFabExpanded = false
                    })
                    add(FabButtonItem(Icons.Default.CheckBox, "Checklist") {
                        addLine(lastActiveLineId, LineType.CHECKLIST)
                        isFabExpanded = false
                    })
                    add(FabButtonItem(Icons.Default.HorizontalRule, "Divider") {
                        addLine(lastActiveLineId, LineType.DIVIDER)
                        isFabExpanded = false
                    })
                    // Images are stored as device-local files (only their file:// URI
                    // syncs through Firestore), so other collaborators could never see
                    // them - hide the option in shared notes until images get real
                    // cloud storage.
                    if (!isCollaborative) {
                        add(FabButtonItem(Icons.Default.Image, "Image") {
                            imagePickerLauncher.launch("image/*")
                            isFabExpanded = false
                        })
                    }
                }
            )
        }
    ) { paddingValues ->
        if (showCollaborationDialog) {
            CollaborationDialog(
                onDismiss = { showCollaborationDialog = false },
                onCreateCollaborative = {
                    coroutineScope.launch {
                        val currentTitle = title.ifBlank { "Collaborative Note" }
                        val currentLines = lines.toList()
                        // Cancel any in-flight autosave so the local copy can't be
                        // recreated after we delete it below.
                        autoSaveJob?.cancel()
                        if (noteId != 0) {
                            viewModel.deleteNoteById(noteId)
                        }
                        viewModel.createCollaborativeNote(
                            title = currentTitle,
                            lines = currentLines,
                            noteType = NoteType.TEXT
                        ) { newCollaborativeId ->
                            navController.navigate("collaborative_todo/$newCollaborativeId") {
                                popUpTo(navController.graph.id) { inclusive = false }
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
                            // Result surfaces through the invite's own state
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
                        // Nothing to update locally
                    }
                }
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

        if (showColorPicker) {
            ColorPickerDialog(
                currentColorIndex = noteColorIndex.coerceAtLeast(0),
                onColorSelected = { picked ->
                    noteColorIndex = picked
                    if (isCollaborative && collaborativeNoteId != null) {
                        // Personal, device-local color - each collaborator picks their own
                        sk.kubisdev.mynotes.CollabLocalPrefs.setColorIndex(context, collaborativeNoteId, picked)
                    } else {
                        // Saved note: write immediately. New (unsaved) note: noteColorIndex
                        // already holds the pick, so just flush it out via a save.
                        loadedNote?.let { viewModel.updateNoteColor(it, picked) } ?: triggerImmediateAutoSave()
                    }
                    showColorPicker = false
                },
                onDismiss = { showColorPicker = false },
                currentPatternIndex = notePatternIndex,
                onPatternSelected = { picked ->
                    notePatternIndex = picked
                    if (isCollaborative && collaborativeNoteId != null) {
                        // Personal, device-local pattern - each collaborator picks their
                        // own, same as the color above (patterns aren't a shared field).
                        sk.kubisdev.mynotes.CollabLocalPrefs.setPatternIndex(context, collaborativeNoteId, picked)
                    } else {
                        loadedNote?.let { viewModel.updateNotePattern(it, picked) }
                    }
                }
            )
        }

        if (showReminderDialog && noteId != 0) {
            ReminderDialog(
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

        Column(
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
                .imePadding()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    // Tap-below-to-continue-writing WITHOUT phantom trailing space: no
                    // spacer item is appended after the content (the note visibly ends
                    // exactly where its text ends). Taps on rows are consumed by the
                    // fields themselves and never reach this handler; only taps on the
                    // truly empty area below the last item land here, where we either
                    // refocus the trailing paragraph or start a new one.
                    .pointerInput(Unit) {
                        detectTapGestures { tap ->
                            val lastItem = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()
                            val contentBottom = lastItem?.let { it.offset + it.size } ?: 0
                            if (tap.y > contentBottom) {
                                val last = lines.lastOrNull()
                                if (last != null && last.type == LineType.TEXT) {
                                    editingLineId = last.id
                                    try {
                                        lineFocusRequesters.getOrPut(last.id) { RichTextFieldController() }.requestFocus()
                                    } catch (e: Exception) {
                                        // Row not composed yet; user can tap the line itself
                                    }
                                } else {
                                    addLine(last?.id, LineType.TEXT)
                                }
                            }
                        }
                    },
                state = lazyListState,
                // 96.dp is FAB clearance, and doubles as the scroll runway below the last
                // line: bringIntoView() targets the cursor's line plus a 64.dp margin, so
                // this is all the extra scroll room a field at the very end ever needs.
                // (A larger IME-only padding used to live here as a workaround for
                // whole-field bringIntoView; it just read as a big empty gap.)
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
            ) {
                itemsIndexed(items = lines, key = { _, line -> line.id }) { itemIndex, line ->
                    val isDragged = draggedItemId == line.id
                    val isDropTarget = dropTargetId == line.id
                    val numberInList = if (line.type == LineType.NUMBERED) {
                        var n = 1
                        var i = itemIndex - 1
                        while (i >= 0 && lines[i].type == LineType.NUMBERED) {
                            n++
                            i--
                        }
                        n
                    } else 1

                    if (isDropTarget && !isDragged && draggedItem != null) {
                        DropIndicator()
                    }

                    fun onLineDragStart() {
                        draggedItemId = line.id
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }

                    fun onLineDragOffset(offset: Float) {
                        if (draggedItemId == line.id) {
                            dragOffset += offset
                            dropTargetId = calculateDropTarget(dragOffset)

                            val layoutInfo = lazyListState.layoutInfo
                            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                            when {
                                dragOffset < -viewportHeight * 0.2f -> startAutoScroll(-1)
                                dragOffset > viewportHeight * 0.2f -> startAutoScroll(1)
                                else -> stopAutoScroll()
                            }
                        }
                    }

                    fun onLineDragEnd() {
                        if (draggedItemId == line.id) performDrop()
                    }

                    Box(
                        modifier = Modifier.then(
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
                            LineType.SEPARATOR -> SeparatorItem(
                                text = line.content,
                                onTextChange = { newText ->
                                    val index = lines.indexOf(line)
                                    if (index != -1) {
                                        lines[index] = line.copy(content = newText)
                                        triggerDebouncedAutoSave()
                                    }
                                },
                                onDelete = { deleteLineWithUndo(line) },
                                onDragStart = ::onLineDragStart,
                                onDragOffset = ::onLineDragOffset,
                                onDragEnd = ::onLineDragEnd,
                                showDragHandle = false
                            )
                            LineType.DIVIDER -> DividerLineItem(
                                showDelete = revealedDeleteId == line.id,
                                onToggleDelete = {
                                    revealedDeleteId = if (revealedDeleteId == line.id) null else line.id
                                },
                                onDelete = { deleteLineWithUndo(line) },
                                onDragStart = ::onLineDragStart,
                                onDragOffset = ::onLineDragOffset,
                                onDragEnd = ::onLineDragEnd
                            )
                            LineType.IMAGE -> NoteImageRow(
                                line = line,
                                showDelete = revealedDeleteId == line.id,
                                onToggleDelete = {
                                    revealedDeleteId = if (revealedDeleteId == line.id) null else line.id
                                },
                                onScaleChange = { zoomDelta ->
                                    val index = lines.indexOf(line)
                                    if (index != -1) {
                                        val newScale = (line.imageScale * zoomDelta).coerceIn(0.5f, 4f)
                                        lines[index] = line.copy(imageScale = newScale)
                                        triggerDebouncedAutoSave()
                                    }
                                },
                                onScaleReset = {
                                    val index = lines.indexOf(line)
                                    if (index != -1) {
                                        lines[index] = line.copy(imageScale = 1f)
                                        triggerImmediateAutoSave()
                                    }
                                },
                                onDelete = { deleteLineWithUndo(line) },
                                onDragStart = ::onLineDragStart,
                                onDragOffset = ::onLineDragOffset,
                                onDragEnd = ::onLineDragEnd
                            )
                            else -> NoteLineEditRow(
                                line = line,
                                // Placeholder on a brand-new, still-empty note so the first
                                // line isn't a blank void - shows "Type something…" until the
                                // user types or adds a second line.
                                hint = if (itemIndex == 0 && line.type == LineType.TEXT && lines.size == 1)
                                    stringResource(R.string.type_something_hint) else null,
                                numberInList = numberInList,
                                isEditing = editingLineId == line.id,
                                lineFocusRequesters = lineFocusRequesters,
                                onTextChange = { newText, cursorPos -> handleTextChange(line.id, newText, cursorPos) },
                                onCheckChange = { checked ->
                                    val index = lines.indexOf(line)
                                    if (index != -1) {
                                        lines[index] = line.copy(isChecked = checked)
                                        triggerImmediateAutoSave()
                                    }
                                },
                                onDelete = { deleteLineWithUndo(line) },
                                onFocusChange = { hasFocus ->
                                    editingLineId = if (hasFocus) line.id else null
                                    if (hasFocus) {
                                        lastActiveLineId = line.id
                                        // Focusing a text row dismisses any tap-revealed
                                        // delete X on dividers/images.
                                        revealedDeleteId = null
                                    } else {
                                        selectedLineId = null
                                        selectedTextRange = null
                                        toggledStyles = emptySet()
                                        activeColor = null
                                        activeFontSize = 16
                                    }
                                },
                                onBackspaceOnEmptyLine = { deleteLineWithUndo(line) },
                                onMergeWithPrevious = { mergeLineWithPrevious(line.id) },
                                onEnterPressed = {
                                    // Enter on an EMPTY bullet/checklist item exits the list
                                    // back to plain text (standard editor behavior) instead
                                    // of piling up empty items.
                                    if (line.type != LineType.TEXT && line.content.isBlank()) {
                                        val index = lines.indexOf(line)
                                        if (index != -1) {
                                            lines[index] = line.copy(type = LineType.TEXT)
                                            triggerImmediateAutoSave()
                                        }
                                    } else {
                                        addLine(line.id, line.type)
                                    }
                                },
                                onSelectionChange = { lineId, range ->
                                    selectedLineId = lineId
                                    selectedTextRange = range
                                    selection = range

                                    if (lineId == line.id) {
                                        val start = range.min
                                        val end = range.max
                                        val snapshot = if (range.collapsed) {
                                            // Cursor with no selection: look at the character just
                                            // typed/left of it, so formatting reads as "active" right
                                            // after a bold/italic/underline run - matching how typing
                                            // continues that style instead of dropping it.
                                            line.spanStyles.formattingAtOffset((start - 1).coerceAtLeast(0))
                                        } else {
                                            fun allCovered(matches: (SerializableSpanStyle) -> Boolean): Boolean {
                                                val covered = BooleanArray(end - start)
                                                line.spanStyles.filter(matches).forEach { span ->
                                                    val overlapStart = maxOf(span.start, start)
                                                    val overlapEnd = minOf(span.end, end)
                                                    for (i in overlapStart until overlapEnd) covered[i - start] = true
                                                }
                                                return covered.all { it }
                                            }
                                            SelectionFormattingSnapshot(
                                                isBold = allCovered { (it.fontWeight ?: 0) >= 700 },
                                                isItalic = allCovered { it.fontStyle == "italic" },
                                                isUnderline = allCovered { it.textDecoration == "underline" },
                                                color = line.spanStyles.firstOrNull {
                                                    it.color != null && it.start < end && it.end > start
                                                }?.color,
                                                fontSize = line.spanStyles.firstOrNull {
                                                    it.fontSize != null && it.start < end && it.end > start
                                                }?.fontSize?.toInt()
                                            )
                                        }
                                        toggledStyles = buildSet {
                                            if (snapshot.isBold) add("BOLD")
                                            if (snapshot.isItalic) add("ITALIC")
                                            if (snapshot.isUnderline) add("UNDERLINE")
                                        }
                                        activeColor = snapshot.color?.let { Color(it) }
                                        activeFontSize = snapshot.fontSize ?: 16
                                    }
                                },
                                onImmediateSave = { triggerImmediateAutoSave() },
                                coroutineScope = coroutineScope,
                                onDragStart = ::onLineDragStart,
                                onDragOffset = ::onLineDragOffset,
                                onDragEnd = ::onLineDragEnd
                            )
                        }
                    }
                }

            }
        }
    }
}

@Composable
private fun NoteLineEditRow(
    line: NoteLine,
    numberInList: Int = 1,
    isEditing: Boolean,
    lineFocusRequesters: MutableMap<String, RichTextFieldController>,
    onTextChange: (String, Int) -> Unit,
    onCheckChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onBackspaceOnEmptyLine: () -> Unit,
    onEnterPressed: () -> Unit,
    onSelectionChange: (String, TextRange) -> Unit,
    onImmediateSave: () -> Unit,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onMergeWithPrevious: () -> Boolean = { false },
    onDragStart: () -> Unit = {},
    onDragOffset: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    hint: String? = null,
    modifier: Modifier = Modifier
) {
    val controller = remember(line.id) { lineFocusRequesters.getOrPut(line.id) { RichTextFieldController() } }
    val cursorColor = MaterialTheme.colorScheme.primary
    val displayMetrics = LocalContext.current.resources.displayMetrics
    val baseFontSizeSp = 16f
    val checkedState = rememberUpdatedState(line.isChecked)
    // The delete X stays hidden until the line is actually being edited - keeps the
    // note visually clean and prevents accidental deletes while scrolling.
    var isRowFocused by remember(line.id) { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // Checklist/bullet items are genuinely discrete list entries and keep the
            // larger spacing; plain paragraphs read as continuous prose.
            .padding(vertical = if (line.type == LineType.TEXT) 0.dp else 4.dp, horizontal = 8.dp)
            // Min-intrinsic height lets the QUOTE side bar below stretch to match
            // whatever height the (possibly multi-line) text field ends up with.
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // No permanent drag handle - plain paragraphs stay visually clean like a real
        // notepad. Discrete items (checklist/bullet) are reordered by long-pressing
        // their leading element (checkbox / bullet dot) and dragging, Apple Notes
        // style: tap still toggles the checkbox, only press-and-hold starts a drag.
        when (line.type) {
            // Checkbox's own built-in clickable/ripple handling would consume the down
            // event before our long-press timer here ever fires, so long-pressing the
            // checkbox never started a drag. onCheckedChange = null turns it into a
            // pure visual (no touch handling of its own); the single gesture detector
            // below owns both the tap-to-toggle and long-press-to-drag behavior.
            LineType.CHECKLIST -> Box(
                modifier = Modifier.pointerInput(line.id) {
                    // pointerInput(line.id) only restarts this coroutine when the line's
                    // ID changes, so a plain `line` reference captured here would keep
                    // pointing at the very first NoteLine this gesture loop ever saw -
                    // every tap would then toggle relative to that stale isChecked value
                    // instead of the current one. checkedState always reads the latest.
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        // withTimeoutOrNull, not a try/catch around withTimeout: the
                        // timeout fires as a CancellationException, and kotlinx's
                        // TimeoutCancellationException isn't the same type as Compose's
                        // own PointerEventTimeoutCancellationException - catching the
                        // wrong one let it propagate up and permanently cancel this
                        // gesture-detecting coroutine after the very first long press,
                        // leaving the checkbox unresponsive to every tap after that.
                        val upEvent = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            waitForUpOrCancellation()
                        }

                        if (upEvent == null) {
                            down.consume()
                            onDragStart()
                            drag(down.id) { change ->
                                onDragOffset(change.positionChange().y)
                                change.consume()
                            }
                            onDragEnd()
                        } else {
                            onCheckChange(!checkedState.value)
                        }
                    }
                }
            ) {
                Checkbox(
                    checked = line.isChecked,
                    onCheckedChange = null,
                    modifier = Modifier.padding(end = 4.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
            LineType.BULLET -> Box(
                modifier = Modifier
                    .size(32.dp)
                    .pointerInput(line.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { _, dragAmount -> onDragOffset(dragAmount.y) }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    modifier = Modifier.size(8.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
            LineType.NUMBERED -> Box(
                modifier = Modifier
                    .widthIn(min = 24.dp)
                    .padding(end = 4.dp)
                    .pointerInput(line.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { _, dragAmount -> onDragOffset(dragAmount.y) }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$numberInList.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            LineType.QUOTE -> Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .padding(end = 8.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                    .pointerInput(line.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { _, dragAmount -> onDragOffset(dragAmount.y) }
                        )
                    }
            )
            else -> Unit
        }

        RichTextField(
            line = line,
            textColor = when {
                line.type == LineType.CHECKLIST && line.isChecked -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                line.type == LineType.QUOTE -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                else -> MaterialTheme.colorScheme.onSurface
            },
            strikeThrough = line.type == LineType.CHECKLIST && line.isChecked,
            cursorColor = cursorColor,
            baseFontSizeSp = if (line.type == LineType.HEADING) baseFontSizeSp + 6f else baseFontSizeSp,
            bold = line.type == LineType.HEADING,
            italic = line.type == LineType.QUOTE,
            scaledDensity = displayMetrics.scaledDensity,
            hint = hint,
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
            onBackspaceAtStart = {
                if (line.type == LineType.TEXT && line.content.isNotEmpty()) onMergeWithPrevious() else false
            },
            modifier = Modifier.weight(1f)
        )

        // A plain paragraph doesn't need its own delete button - clearing the text and
        // backspacing removes it, same as any normal notepad. Checklist/bullet items keep
        // one since they're discrete list entries, not free-flowing prose - but only
        // while the line is focused (see isRowFocused above).
        if (line.type != LineType.TEXT && isRowFocused) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp).padding(start = 4.dp)) {
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

// A plain horizontal rule (Evernote-style "---") - unlike SeparatorItem this has no
// label/text field, just a thin line, so it stays visually distinct from the labeled
// section-divider card the to-do list uses.
@Composable
private fun DividerLineItem(
    // No text field to focus here, so the delete X is revealed by tapping the
    // divider itself. The reveal state is hoisted so only one object on the
    // screen shows its X at a time.
    showDelete: Boolean,
    onToggleDelete: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit = {},
    onDragOffset: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 8.dp)
            .pointerInput("divider_reorder") {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { _, dragAmount -> onDragOffset(dragAmount.y) }
                )
            }
            .pointerInput("divider_tap") {
                detectTapGestures { onToggleDelete() }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
        if (showDelete) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp).padding(start = 4.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Delete divider",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun NoteImageRow(
    line: NoteLine,
    // The delete X only appears after tapping the image once (tap again to hide),
    // so it doesn't permanently cover the photo's corner. Hoisted so only one
    // object on the screen shows its X at a time.
    showDelete: Boolean,
    onToggleDelete: () -> Unit,
    onScaleChange: (Float) -> Unit,
    onScaleReset: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit = {},
    onDragOffset: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(modifier = Modifier.weight(1f)) {
            AsyncImage(
                model = line.content,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .graphicsLayer(scaleX = line.imageScale, scaleY = line.imageScale)
                    // No drag handle: press-and-hold the image itself, then drag to
                    // reorder it between lines (single tap/scroll unaffected).
                    .pointerInput("reorder_${line.id}") {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { _, dragAmount -> onDragOffset(dragAmount.y) }
                        )
                    }
                    // Single tap toggles the delete X overlay (see showDelete above).
                    .pointerInput("tap_${line.id}") {
                        detectTapGestures { onToggleDelete() }
                    }
                    // Only reacts to genuine two-finger pinches (checks changes.size >= 2
                    // before consuming anything) so a normal single-finger drag over the
                    // image still scrolls the note as usual instead of getting swallowed here.
                    .pointerInput(line.id) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.size >= 2) {
                                    val zoomChange = event.calculateZoom()
                                    if (zoomChange != 1f) {
                                        event.changes.forEach { it.consume() }
                                        onScaleChange(zoomChange)
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
                contentScale = ContentScale.Fit
            )
            if (line.imageScale != 1f) {
                IconButton(
                    onClick = onScaleReset,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "Reset zoom", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            if (showDelete) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Delete image", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private fun saveImageToInternalStorage(context: Context, uri: Uri): Uri {
    val inputStream = context.contentResolver.openInputStream(uri)
    val file = File(context.filesDir, "img_${System.currentTimeMillis()}.jpg")
    val outputStream = FileOutputStream(file)
    inputStream?.copyTo(outputStream)
    inputStream?.close()
    outputStream.close()
    return Uri.fromFile(file)
}
