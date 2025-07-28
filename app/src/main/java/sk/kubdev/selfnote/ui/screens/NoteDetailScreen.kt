package sk.kubdev.selfnote.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import sk.kubdev.selfnote.LineType
import sk.kubdev.selfnote.NoteLine
import sk.kubdev.selfnote.NoteViewModel
import sk.kubdev.selfnote.R
import sk.kubdev.selfnote.SerializableSpanStyle
import sk.kubdev.selfnote.data.remote.local.entities.NoteType
import sk.kubdev.selfnote.toAnnotatedString
import sk.kubdev.selfnote.toJson
import sk.kubdev.selfnote.toNoteLines
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

// Drag and Drop State
data class DragDropState(
    val draggedIndex: Int? = null,
    val draggedOverIndex: Int? = null,
    val initialDragOffset: Offset = Offset.Zero,
    val currentDragOffset: Offset = Offset.Zero,
    val isDragging: Boolean = false
)

// Undo action data class
data class UndoAction(
    val type: UndoActionType,
    val index: Int,
    val line: NoteLine? = null,
    val lines: List<NoteLine>? = null
)

enum class UndoActionType {
    DELETE_LINE,
    DELETE_SEPARATOR,
    IMAGE_MOVE,
    DRAG_DROP
}

// Calculate the single target drop index based on drag position
private fun calculateTargetDropIndex(
    draggedIndex: Int,
    dragOffset: Float,
    totalItems: Int
): Int {
    if (draggedIndex == -1) return -1

    val itemHeight = 300f // Approximate item height
    val draggedItemCenter = draggedIndex * itemHeight + dragOffset

    // Calculate which position this corresponds to
    val targetIndex = (draggedItemCenter / itemHeight).roundToInt()

    return when {
        // Dragging up from current position
        dragOffset < -50f -> maxOf(0, targetIndex)
        // Dragging down from current position
        dragOffset > 50f -> minOf(totalItems, targetIndex + 1)
        // Not dragging enough to change position
        else -> -1
    }
}

// Drop indicator line composable
@Composable
private fun DropIndicatorLine() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 4.dp,
            color = MaterialTheme.colorScheme.primary
        )

        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    CircleShape
                )
                .padding(6.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Drop here",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
        }

        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 4.dp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    noteIdArg: Int,
    navController: NavController,
    viewModel: NoteViewModel
) {
    var noteId by remember { mutableStateOf(noteIdArg) }
    var title by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf(listOf(
        NoteLine.create(
            type = LineType.TEXT,
            content = ""
        )
    )) }
    var isToolbarVisible by remember { mutableStateOf(true) }
    var focusedLineIndex by remember { mutableStateOf<Int?>(null) }
    var selection by remember { mutableStateOf(TextRange.Zero) }
    var toggledStyles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var activeColor by remember { mutableStateOf<Color?>(null) }
    var activeFontSize by remember { mutableStateOf(16) }
    var isBulletModeActive by remember { mutableStateOf(false) }
    var isChecklistModeActive by remember { mutableStateOf(false) }
    var isTitleFocused by remember { mutableStateOf(false) }

    // Drag and drop state
    var dragDropState by remember { mutableStateOf(DragDropState()) }
    var itemPositions by remember { mutableStateOf<Map<Int, Float>>(emptyMap()) }

    // Undo stack
    var undoStack by remember { mutableStateOf<List<UndoAction>>(emptyList()) }
    val snackbarHostState = remember { SnackbarHostState() }

    val titleFocusRequester = remember { FocusRequester() }
    val lineFocusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val context = LocalContext.current
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Keep keyboard visible when switching between lines
    LaunchedEffect(focusedLineIndex) {
        if (focusedLineIndex != null) {
            delay(50) // Small delay to ensure focus is established
            keyboardController?.show()
        }
    }

    // Function to perform undo
    fun performUndo(action: UndoAction) {
        when (action.type) {
            UndoActionType.DELETE_LINE, UndoActionType.DELETE_SEPARATOR -> {
                action.line?.let { deletedLine ->
                    val newLines = lines.toMutableList()
                    newLines.add(action.index, deletedLine)
                    lines = newLines
                    coroutineScope.launch {
                        delay(100)
                        lazyListState.animateScrollToItem(action.index)
                    }
                }
            }
            UndoActionType.IMAGE_MOVE, UndoActionType.DRAG_DROP -> {
                action.lines?.let {
                    lines = it
                }
            }
        }
    }

    // Function to handle drag and drop
    fun handleDragDrop(fromIndex: Int, toIndex: Int) {
        if (fromIndex != toIndex && fromIndex >= 0 && toIndex >= 0 &&
            fromIndex < lines.size && toIndex < lines.size) {

            // Save current state for undo
            undoStack = undoStack + UndoAction(
                type = UndoActionType.DRAG_DROP,
                index = fromIndex,
                lines = lines
            )

            val newLines = lines.toMutableList()
            val item = newLines.removeAt(fromIndex)
            newLines.add(toIndex, item)
            lines = newLines

            // IMPORTANT: Reset drag state after successful move
            dragDropState = DragDropState()

            // Reset any focused line index if it was affected
            if (focusedLineIndex == fromIndex) {
                focusedLineIndex = toIndex
            } else if (focusedLineIndex != null) {
                // Adjust focused line index if it was affected by the move
                val focusedIndex = focusedLineIndex!!
                when {
                    focusedIndex > fromIndex && focusedIndex <= toIndex -> focusedLineIndex = focusedIndex - 1
                    focusedIndex < fromIndex && focusedIndex >= toIndex -> focusedLineIndex = focusedIndex + 1
                }
            }
        }

        // IMPORTANT: Always reset drag state, even if move failed
        dragDropState = DragDropState()
    }


    // Image picker launcher with permanent storage - modified to insert at cursor position
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // IMPORTANT: Reset drag state before modifying the list
            dragDropState = DragDropState()

            val permanentUri = saveImageToInternalStorage(context, uri)
            val imageLine = NoteLine.create(
                type = LineType.IMAGE,
                content = permanentUri.toString()
            )

            // Safe insertion with bounds checking
            val insertIndex = focusedLineIndex?.let { focusedIndex ->
                // Ensure the focused index is still valid
                if (focusedIndex >= 0 && focusedIndex < lines.size) {
                    (focusedIndex + 1).coerceIn(0, lines.size)
                } else {
                    lines.size
                }
            } ?: lines.size

            val newLines = lines.toMutableList()
            // Safe insertion
            if (insertIndex <= newLines.size) {
                newLines.add(insertIndex, imageLine)
            } else {
                newLines.add(imageLine)
            }
            lines = newLines

            // Update focused line index if necessary
            if (focusedLineIndex != null && focusedLineIndex!! >= insertIndex) {
                focusedLineIndex = focusedLineIndex!! + 1
            }

            coroutineScope.launch {
                delay(100)
                val scrollIndex = insertIndex.coerceIn(0, maxOf(0, lines.size - 1))
                lazyListState.animateScrollToItem(scrollIndex)
            }
        }
    }

    // ✅ FIXED: Auto-save effect
    LaunchedEffect(title, lines) {
        viewModel.triggerAutoSave(
            noteId = noteId,
            title = title,
            lines = lines,
            noteType = NoteType.TEXT,
            onIdReceived = { newId ->
                if (noteId == 0) {
                    noteId = newId
                }
            }
        )
    }

    // ✅ FIXED: Load existing note
    LaunchedEffect(key1 = noteIdArg) {
        if (noteIdArg != 0) {
            viewModel.getNoteById(noteIdArg).collectLatest { note ->
                if (note != null) {
                    title = note.title
                    val loadedLines = try {
                        note.content.toNoteLines()
                    } catch (e: Exception) {
                        println("Error parsing note content: ${e.message}")
                        listOf(NoteLine.create(type = LineType.TEXT, content = note.content))
                    }
                    lines = if (loadedLines.isNotEmpty()) {
                        loadedLines
                    } else {
                        listOf(NoteLine.create(type = LineType.TEXT, content = ""))
                    }
                }
            }
        } else {
            delay(100)
            titleFocusRequester.requestFocus()
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
                        Text(
                            text = if (noteIdArg == 0) stringResource(R.string.note_new_title) else stringResource(
                                R.string.note_edit_title
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        )

                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BasicTextField(
                                value = title,
                                onValueChange = { title = it },
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (undoStack.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                undoStack.lastOrNull()?.let { lastAction ->
                                    performUndo(lastAction)
                                    undoStack = undoStack.dropLast(1)
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Undo, stringResource(R.string.undo))
                        }
                    }

                    IconButton(onClick = { isToolbarVisible = !isToolbarVisible }) {
                        Icon(Icons.Default.TextFields, stringResource(R.string.note_toggle_toolbar))
                    }
                    if (noteId != 0) {
                        IconButton(onClick = {
                            viewModel.deleteNoteById(noteId)
                            navController.navigateUp()
                        }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.note_delete))
                        }
                    }
                    // ✅ FIXED: Save button
                    IconButton(onClick = {
                        coroutineScope.launch {
                            val contentJson = lines.toJson()
                            viewModel.finaliseAndSave(noteId, title, contentJson, NoteType.TEXT).join()
                            navController.navigateUp()
                        }
                    }) {
                        Icon(Icons.Default.Done, stringResource(R.string.note_save))
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
            FloatingActionButton(
                onClick = {
                    // Insert at cursor position instead of at the end
                    val insertIndex = focusedLineIndex?.let { it + 1 } ?: lines.size
                    val newLine = NoteLine.create(type = LineType.TEXT, content = "")
                    val newLines = lines.toMutableList()
                    newLines.add(insertIndex, newLine)
                    lines = newLines
                    coroutineScope.launch {
                        delay(100)
                        focusedLineIndex = insertIndex
                        selection = TextRange(0)
                        lazyListState.animateScrollToItem(insertIndex)
                        // Safely request focus
                        lineFocusRequesters[newLine.id]?.let { focusRequester ->
                            try {
                                focusRequester.requestFocus()
                            } catch (e: IllegalStateException) {
                                // Silently ignore if FocusRequester is not initialized
                            }
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.note_add_line))
            }
        }

    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            AnimatedVisibility(visible = isToolbarVisible) {
                NoteFormattingToolbar(
                    toggledStyles = toggledStyles,
                    activeColor = activeColor,
                    activeFontSize = activeFontSize,
                    isBulletModeActive = isBulletModeActive,
                    isChecklistModeActive = isChecklistModeActive,
                    onStyleToggle = { styleKey ->
                        focusedLineIndex?.let { index ->
                            if (index < lines.size && !selection.collapsed) {
                                applyFormattingToggleToSelection(index, lines, selection, styleKey) { newLines ->
                                    lines = newLines
                                }
                            } else {
                                toggledStyles = if (toggledStyles.contains(styleKey)) {
                                    toggledStyles - styleKey
                                } else {
                                    toggledStyles + styleKey
                                }
                            }
                        } ?: run {
                            toggledStyles = if (toggledStyles.contains(styleKey)) {
                                toggledStyles - styleKey
                            } else {
                                toggledStyles + styleKey
                            }
                        }
                    },
                    onColorChange = { color ->
                        activeColor = if (activeColor == color) null else color
                        focusedLineIndex?.let { index ->
                            if (index < lines.size && !selection.collapsed) {
                                applyFormattingToSelection(index, lines, selection, null, color, null) { newLines ->
                                    lines = newLines
                                }
                            }
                        }
                    },
                    onFontSizeChange = { fontSize ->
                        activeFontSize = fontSize
                        focusedLineIndex?.let { index ->
                            if (index < lines.size && !selection.collapsed) {
                                applyFormattingToSelection(index, lines, selection, null, null, fontSize) { newLines ->
                                    lines = newLines
                                }
                            }
                        }
                    },
                    onBulletModeToggle = {
                        isBulletModeActive = !isBulletModeActive
                        if (isBulletModeActive) {
                            isChecklistModeActive = false
                            // Insert at cursor position instead of at the end
                            val insertIndex = focusedLineIndex?.let { it + 1 } ?: lines.size
                            val newLine = NoteLine.create(type = LineType.BULLET, content = "")
                            val newLines = lines.toMutableList()
                            newLines.add(insertIndex, newLine)
                            lines = newLines
                            coroutineScope.launch {
                                delay(100)
                                focusedLineIndex = insertIndex
                                selection = TextRange(0)
                                lazyListState.animateScrollToItem(insertIndex)
                                // Safely request focus
                                lineFocusRequesters[newLine.id]?.let { focusRequester ->
                                    try {
                                        focusRequester.requestFocus()
                                    } catch (e: IllegalStateException) {
                                        // Silently ignore if FocusRequester is not initialized
                                    }
                                }
                            }
                        }
                    },
                    onChecklistModeToggle = {
                        isChecklistModeActive = !isChecklistModeActive
                        if (isChecklistModeActive) {
                            isBulletModeActive = false
                            // Insert at cursor position instead of at the end
                            val insertIndex = focusedLineIndex?.let { it + 1 } ?: lines.size
                            val newLine = NoteLine.create(type = LineType.CHECKLIST, content = "")
                            val newLines = lines.toMutableList()
                            newLines.add(insertIndex, newLine)
                            lines = newLines
                            coroutineScope.launch {
                                delay(100)
                                focusedLineIndex = insertIndex
                                selection = TextRange(0)
                                lazyListState.animateScrollToItem(insertIndex)
                                // Safely request focus
                                lineFocusRequesters[newLine.id]?.let { focusRequester ->
                                    try {
                                        focusRequester.requestFocus()
                                    } catch (e: IllegalStateException) {
                                        // Silently ignore if FocusRequester is not initialized
                                    }
                                }
                            }
                        }
                    },

                    onAddSeparator = {
                        // Insert at cursor position instead of at the end
                        val insertIndex = focusedLineIndex?.let { it + 1 } ?: lines.size
                        val separatorLine = NoteLine.create(
                            type = LineType.SEPARATOR,
                            content = ""
                        )
                        val newLines = lines.toMutableList()
                        newLines.add(insertIndex, separatorLine)
                        lines = newLines
                        coroutineScope.launch {
                            delay(100)
                            lazyListState.animateScrollToItem(insertIndex)
                        }
                    },
                    onAddImage = {
                        // This will trigger the image picker, which will insert at cursor position
                        imagePickerLauncher.launch("image/*")
                    }
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                state = lazyListState
            ) {
                itemsIndexed(
                    items = lines,
                    key = { index, line -> "${line.id}_$index" }
                ) { index, line ->
                    // Ensure focus requester exists for this line BEFORE using it
                    val focusRequester = lineFocusRequesters.getOrPut(line.id) { FocusRequester() }

                    // Calculate the target drop position
                    val targetDropIndex = calculateTargetDropIndex(
                        draggedIndex = dragDropState.draggedIndex ?: -1,
                        dragOffset = dragDropState.currentDragOffset.y,
                        totalItems = lines.size
                    )

                    // Show drop indicator ABOVE this item ONLY if this is the target position
                    if (dragDropState.isDragging &&
                        dragDropState.draggedIndex != index &&
                        targetDropIndex == index) {
                        DropIndicatorLine()
                    }

                    when (line.type) {
                        LineType.IMAGE -> {
                            val isDragged = dragDropState.draggedIndex == index
                            val isDraggedOver = dragDropState.draggedOverIndex == index

                            val dragOffset by animateDpAsState(
                                targetValue = if (isDragged) {
                                    with(density) { dragDropState.currentDragOffset.y.toDp() }
                                } else if (isDraggedOver && dragDropState.isDragging) {
                                    if (dragDropState.draggedIndex!! < index) (-20).dp else 20.dp
                                } else {
                                    0.dp
                                },
                                animationSpec = spring(stiffness = 500f),
                                label = "dragOffset"
                            )

                            val elevation by animateDpAsState(
                                targetValue = if (isDragged) 8.dp else 0.dp,
                                animationSpec = spring(stiffness = 500f),
                                label = "elevation"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coordinates ->
                                        itemPositions = itemPositions + (index to coordinates.positionInWindow().y)
                                    }
                                    .zIndex(if (isDragged) 1f else 0f)
                                    .graphicsLayer {
                                        alpha = if (isDragged) 0.6f else 1f
                                        shadowElevation = elevation.toPx()
                                        translationY = dragOffset.toPx()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                NoteImageItem(
                                    line = line,
                                    index = index,
                                    totalLines = lines.size,
                                    onDelete = {
                                        // Reset drag state before modifying list
                                        dragDropState = DragDropState()

                                        undoStack = undoStack + UndoAction(
                                            type = UndoActionType.DELETE_LINE,
                                            index = index,
                                            line = line
                                        )
                                        val newLines = lines.toMutableList().apply { removeAt(index) }
                                        lines = newLines

                                        coroutineScope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = context.getString(R.string.item_deleted),
                                                actionLabel = context.getString(R.string.undo),
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                undoStack.lastOrNull()?.let { lastAction ->
                                                    performUndo(lastAction)
                                                    undoStack = undoStack.dropLast(1)
                                                }
                                            }
                                        }
                                    },
                                    onImageUpdate = { updatedLine ->
                                        val newLines = lines.toMutableList().also { it[index] = updatedLine }
                                        lines = newLines
                                    },
                                    onMoveUp = {
                                        if (index > 0) {
                                            handleDragDrop(index, index - 1)
                                        }
                                    },
                                    onMoveDown = {
                                        if (index < lines.size - 1) {
                                            handleDragDrop(index, index + 1)
                                        }
                                    },
                                    onDragStart = {
                                        // Reset any previous drag state first
                                        dragDropState = DragDropState()

                                        dragDropState = dragDropState.copy(
                                            draggedIndex = index,
                                            isDragging = true,
                                            currentDragOffset = Offset.Zero
                                        )
                                    },
                                    onDragEnd = { targetIndex ->
                                        if (targetIndex != null && targetIndex != index) {
                                            handleDragDrop(index, targetIndex)
                                        } else {
                                            // Reset state even if no valid target
                                            dragDropState = DragDropState()
                                        }
                                    },
                                    onDrag = { offset ->
                                        // Only update if we're in a valid drag state
                                        if (dragDropState.isDragging && dragDropState.draggedIndex == index) {
                                            dragDropState = dragDropState.copy(
                                                currentDragOffset = offset
                                            )

                                            // Find which item we're hovering over
                                            val currentY = itemPositions[index]?.plus(offset.y) ?: 0f

                                            val hoveredIndex = itemPositions.entries.minByOrNull {
                                                abs(it.value - currentY)
                                            }?.key

                                            if (hoveredIndex != null && hoveredIndex != dragDropState.draggedOverIndex) {
                                                dragDropState = dragDropState.copy(
                                                    draggedOverIndex = hoveredIndex
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        LineType.SEPARATOR -> {
                            NoteSeparatorItem(
                                onDelete = {
                                    undoStack = undoStack + UndoAction(
                                        type = UndoActionType.DELETE_SEPARATOR,
                                        index = index,
                                        line = line
                                    )
                                    val newLines = lines.toMutableList().apply { removeAt(index) }
                                    lines = newLines

                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.item_deleted),
                                            actionLabel = context.getString(R.string.undo),
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            undoStack.lastOrNull()?.let { lastAction ->
                                                performUndo(lastAction)
                                                undoStack = undoStack.dropLast(1)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        LineType.CHECKLIST -> {
                            ChecklistLineItem(
                                line = line,
                                textFieldValue = remember(line.id, line.content, line.spanStyles.hashCode()) {
                                    try {
                                        TextFieldValue(
                                            annotatedString = line.toAnnotatedString(),
                                            selection = if (focusedLineIndex == index) selection else TextRange.Zero
                                        )
                                    } catch (e: Exception) {
                                        TextFieldValue(
                                            text = line.content,
                                            selection = if (focusedLineIndex == index) selection else TextRange.Zero
                                        )
                                    }
                                },
                                onValueChange = { newTfv ->
                                    if (newTfv.text == "---") {
                                        handleSeparatorCreation(index, lines, coroutineScope, lazyListState, lineFocusRequesters,
                                            { newLines -> lines = newLines },
                                            { newFocusIndex, newSelection ->
                                                focusedLineIndex = newFocusIndex
                                                selection = newSelection
                                            }
                                        )
                                        return@ChecklistLineItem
                                    }

                                    // Handle newlines properly
                                    if (newTfv.text.contains("\n")) {
                                        val beforeNewline = newTfv.text.substringBefore("\n")
                                        val afterNewline = newTfv.text.substringAfter("\n")

                                        // Update current line with text before newline
                                        val updatedCurrentLine = line.copy(content = beforeNewline)
                                        val newLines = lines.toMutableList()
                                        newLines[index] = updatedCurrentLine

                                        // Create new line with text after newline
                                        val newLineType = when {
                                            isChecklistModeActive -> LineType.CHECKLIST
                                            isBulletModeActive -> LineType.BULLET
                                            else -> LineType.TEXT
                                        }
                                        val newLine = NoteLine.create(type = newLineType, content = afterNewline)
                                        newLines.add(index + 1, newLine)

                                        lines = newLines

                                        coroutineScope.launch {
                                            delay(50)
                                            focusedLineIndex = index + 1
                                            selection = TextRange(afterNewline.length)
                                            lazyListState.animateScrollToItem(index + 1)
                                            // Ensure FocusRequester exists before requesting focus
                                            val newLineFocusRequester = lineFocusRequesters.getOrPut(newLine.id) { FocusRequester() }
                                            newLineFocusRequester.requestFocus()
                                        }
                                    } else {
                                        focusedLineIndex = index
                                        selection = newTfv.selection
                                        handleRichTextChange(newTfv, index, lines, toggledStyles, activeColor, activeFontSize) { newLines ->
                                            lines = newLines
                                        }
                                    }
                                },
                                onFocus = {
                                    focusedLineIndex = index
                                },
                                onCheckChange = { isChecked ->
                                    lines = lines.toMutableList().also { it[index] = line.copy(isChecked = isChecked) }
                                },
                                onDeleteLine = {
                                    undoStack = undoStack + UndoAction(
                                        type = UndoActionType.DELETE_LINE,
                                        index = index,
                                        line = line
                                    )

                                    handleDeleteLine(index, lines, coroutineScope, lineFocusRequesters, keyboardController) { newLines, newFocusIndex, newSelection ->
                                        lines = newLines
                                        focusedLineIndex = newFocusIndex
                                        selection = newSelection
                                    }

                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.item_deleted),
                                            actionLabel = context.getString(R.string.undo),
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            undoStack.lastOrNull()?.let { lastAction ->
                                                performUndo(lastAction)
                                                undoStack = undoStack.dropLast(1)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.focusRequester(focusRequester)
                            )
                        }
                        LineType.BULLET -> {
                            BulletLineItem(
                                line = line,
                                textFieldValue = remember(line.id, line.content, line.spanStyles.hashCode()) {
                                    try {
                                        TextFieldValue(
                                            annotatedString = line.toAnnotatedString(),
                                            selection = if (focusedLineIndex == index) selection else TextRange.Zero
                                        )
                                    } catch (e: Exception) {
                                        TextFieldValue(
                                            text = line.content,
                                            selection = if (focusedLineIndex == index) selection else TextRange.Zero
                                        )
                                    }
                                },
                                onValueChange = { newTfv ->
                                    if (newTfv.text == "---") {
                                        handleSeparatorCreation(index, lines, coroutineScope, lazyListState, lineFocusRequesters,
                                            { newLines -> lines = newLines },
                                            { newFocusIndex, newSelection ->
                                                focusedLineIndex = newFocusIndex
                                                selection = newSelection
                                            }
                                        )
                                        return@BulletLineItem
                                    }

                                    // Handle newlines properly
                                    if (newTfv.text.contains("\n")) {
                                        val beforeNewline = newTfv.text.substringBefore("\n")
                                        val afterNewline = newTfv.text.substringAfter("\n")

                                        // Update current line with text before newline
                                        val updatedCurrentLine = line.copy(content = beforeNewline)
                                        val newLines = lines.toMutableList()
                                        newLines[index] = updatedCurrentLine

                                        // Create new line with text after newline
                                        val newLineType = when {
                                            isBulletModeActive -> LineType.BULLET
                                            isChecklistModeActive -> LineType.CHECKLIST
                                            else -> LineType.TEXT
                                        }
                                        val newLine = NoteLine.create(type = newLineType, content = afterNewline)
                                        newLines.add(index + 1, newLine)

                                        lines = newLines

                                        coroutineScope.launch {
                                            delay(50)
                                            focusedLineIndex = index + 1
                                            selection = TextRange(afterNewline.length)
                                            lazyListState.animateScrollToItem(index + 1)
                                            // Ensure FocusRequester exists before requesting focus
                                            val newLineFocusRequester = lineFocusRequesters.getOrPut(newLine.id) { FocusRequester() }
                                            newLineFocusRequester.requestFocus()
                                        }
                                    } else {
                                        focusedLineIndex = index
                                        selection = newTfv.selection
                                        handleRichTextChange(newTfv, index, lines, toggledStyles, activeColor, activeFontSize) { newLines ->
                                            lines = newLines
                                        }
                                    }
                                },
                                onFocus = {
                                    focusedLineIndex = index
                                },
                                onDeleteLine = {
                                    handleDeleteLine(index, lines, coroutineScope, lineFocusRequesters, keyboardController) { newLines, newFocusIndex, newSelection ->
                                        lines = newLines
                                        focusedLineIndex = newFocusIndex
                                        selection = newSelection
                                    }
                                },
                                modifier = Modifier.focusRequester(focusRequester)
                            )
                        }
                        else -> {
                            NoteLineItem(
                                line = line,
                                textFieldValue = remember(line.id, line.content, line.spanStyles.hashCode()) {
                                    try {
                                        TextFieldValue(
                                            annotatedString = line.toAnnotatedString(),
                                            selection = if (focusedLineIndex == index) selection else TextRange.Zero
                                        )
                                    } catch (e: Exception) {
                                        TextFieldValue(
                                            text = line.content,
                                            selection = if (focusedLineIndex == index) selection else TextRange.Zero
                                        )
                                    }
                                },
                                onValueChange = { newTfv ->
                                    if (newTfv.text == "---") {
                                        handleSeparatorCreation(index, lines, coroutineScope, lazyListState, lineFocusRequesters,
                                            { newLines -> lines = newLines },
                                            { newFocusIndex, newSelection ->
                                                focusedLineIndex = newFocusIndex
                                                selection = newSelection
                                            }
                                        )
                                        return@NoteLineItem
                                    }

                                    // Handle newlines properly
                                    if (newTfv.text.contains("\n")) {
                                        val beforeNewline = newTfv.text.substringBefore("\n")
                                        val afterNewline = newTfv.text.substringAfter("\n")

                                        // Update current line with text before newline
                                        val updatedCurrentLine = line.copy(content = beforeNewline)
                                        val newLines = lines.toMutableList()
                                        newLines[index] = updatedCurrentLine

                                        // Create new line with text after newline
                                        val newLineType = when {
                                            isBulletModeActive -> LineType.BULLET
                                            isChecklistModeActive -> LineType.CHECKLIST
                                            else -> LineType.TEXT
                                        }
                                        val newLine = NoteLine.create(type = newLineType, content = afterNewline)
                                        newLines.add(index + 1, newLine)

                                        lines = newLines

                                        coroutineScope.launch {
                                            delay(50)
                                            focusedLineIndex = index + 1
                                            selection = TextRange(afterNewline.length)
                                            lazyListState.animateScrollToItem(index + 1)
                                            // Ensure FocusRequester exists before requesting focus
                                            val newLineFocusRequester = lineFocusRequesters.getOrPut(newLine.id) { FocusRequester() }
                                            newLineFocusRequester.requestFocus()
                                        }
                                    } else {
                                        focusedLineIndex = index
                                        selection = newTfv.selection
                                        handleRichTextChange(newTfv, index, lines, toggledStyles, activeColor, activeFontSize) { newLines ->
                                            lines = newLines
                                        }
                                    }
                                },
                                onFocus = {
                                    focusedLineIndex = index
                                },
                                onDeleteLine = {
                                    handleDeleteLine(index, lines, coroutineScope, lineFocusRequesters, keyboardController) { newLines, newFocusIndex, newSelection ->
                                        lines = newLines
                                        focusedLineIndex = newFocusIndex
                                        selection = newSelection
                                    }
                                },
                                modifier = Modifier.focusRequester(focusRequester)
                            )
                        }
                    }

                    // Show drop indicator BELOW this item ONLY if this is the target position (for end drops)
                    if (dragDropState.isDragging &&
                        dragDropState.draggedIndex != index &&
                        targetDropIndex == index + 1 &&
                        index == lines.size - 1) {
                        DropIndicatorLine()
                    }
                }
            }
        }
    }
}

// Helper Functions
private fun handleSeparatorCreation(
    index: Int,
    lines: List<NoteLine>,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    lineFocusRequesters: MutableMap<String, FocusRequester>,
    onLinesChange: (List<NoteLine>) -> Unit,
    onFocusChange: (Int, TextRange) -> Unit
) {
    val separatorLine = NoteLine.create(
        type = LineType.SEPARATOR,
        content = ""
    )

    val newTextLine = NoteLine.create(type = LineType.TEXT, content = "")

    val newLines = lines.toMutableList().apply {
        set(index, separatorLine)
        add(index + 1, newTextLine)
    }

    // Update state first
    onLinesChange(newLines)
    onFocusChange(index + 1, TextRange(0))

    coroutineScope.launch {
        delay(100)
        lazyListState.animateScrollToItem(index + 1)

        // Safely request focus
        lineFocusRequesters[newTextLine.id]?.let { focusRequester ->
            try {
                focusRequester.requestFocus()
            } catch (e: IllegalStateException) {
                // Silently ignore if FocusRequester is not initialized
            }
        }
    }
}

private fun handleDeleteLine(
    index: Int,
    lines: List<NoteLine>,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    lineFocusRequesters: MutableMap<String, FocusRequester>,
    keyboardController: SoftwareKeyboardController?,
    onUpdate: (List<NoteLine>, Int?, TextRange) -> Unit
) {
    if (lines.size > 1) {
        val newLines = lines.toMutableList().apply { removeAt(index) }

        val newFocusIndex = when {
            index > 0 -> {
                val previousLine = newLines[index - 1]
                val cursorPosition = previousLine.content.length
                index - 1 to TextRange(cursorPosition)
            }
            newLines.isNotEmpty() -> {
                0 to TextRange(0)
            }
            else -> null to TextRange.Zero
        }

        // Update state and focus immediately
        onUpdate(newLines, newFocusIndex.first, newFocusIndex.second)

        // Request focus immediately to maintain keyboard
        newFocusIndex.first?.let { focusIndex ->
            if (focusIndex < newLines.size) {
                val lineId = newLines[focusIndex].id
                lineFocusRequesters[lineId]?.let { focusRequester ->
                    try {
                        focusRequester.requestFocus()
                        // Force keyboard to stay visible
                        keyboardController?.show()
                    } catch (e: Exception) {
                        // Fallback: show keyboard even if focus fails
                        keyboardController?.show()
                    }
                }
            }
        }
    } else {
        val clearedLine = lines[index].copy(content = "", spanStyles = emptyList())
        val newLines = lines.toMutableList().also { it[index] = clearedLine }
        onUpdate(newLines, index, TextRange(0))

        // Keep focus on the same line
        coroutineScope.launch {
            val lineId = newLines[index].id
            lineFocusRequesters[lineId]?.let { focusRequester ->
                try {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                } catch (e: Exception) {
                    keyboardController?.show()
                }
            }
        }
    }
}

private fun handleRichTextChange(
    newTfv: TextFieldValue,
    index: Int,
    lines: List<NoteLine>,
    toggledStyles: Set<String>,
    activeColor: Color?,
    activeFontSize: Int,
    onLinesChange: (List<NoteLine>) -> Unit
) {
    val oldText = lines[index].content
    val newText = newTfv.text
    val currentLine = lines[index]

    when {
        newText.length > oldText.length -> {
            val insertionStart = newTfv.selection.start - (newText.length - oldText.length)
            val insertionEnd = newTfv.selection.start
            val insertionLength = newText.length - oldText.length

            val adjustedSpans = currentLine.spanStyles.map { span ->
                when {
                    span.start >= insertionStart -> span.copy(
                        start = span.start + insertionLength,
                        end = span.end + insertionLength
                    )
                    span.end > insertionStart -> span.copy(
                        end = span.end + insertionLength
                    )
                    else -> span
                }
            }.toMutableList()

            if (toggledStyles.isNotEmpty() || activeColor != null || activeFontSize != 16) {
                if (toggledStyles.contains("BOLD")) {
                    adjustedSpans.add(
                        SerializableSpanStyle(
                        start = insertionStart,
                        end = insertionEnd,
                        fontWeight = FontWeight.Bold.weight
                    )
                    )
                }
                if (toggledStyles.contains("ITALIC")) {
                    adjustedSpans.add(
                        SerializableSpanStyle(
                        start = insertionStart,
                        end = insertionEnd,
                        fontStyle = "italic"
                    )
                    )
                }
                if (toggledStyles.contains("UNDERLINE")) {
                    adjustedSpans.add(
                        SerializableSpanStyle(
                        start = insertionStart,
                        end = insertionEnd,
                        textDecoration = "underline"
                    )
                    )
                }
                if (activeColor != null) {
                    adjustedSpans.add(
                        SerializableSpanStyle(
                        start = insertionStart,
                        end = insertionEnd,
                        color = activeColor.value
                    )
                    )
                }
                if (activeFontSize != 16) {
                    adjustedSpans.add(
                        SerializableSpanStyle(
                        start = insertionStart,
                        end = insertionEnd,
                        fontSize = activeFontSize.toFloat()
                    )
                    )
                }
            }

            val updatedLine = currentLine.copy(
                content = newText,
                spanStyles = adjustedSpans
            )
            onLinesChange(lines.toMutableList().also { it[index] = updatedLine })
        }

        newText.length < oldText.length -> {
            val deletionStart = newTfv.selection.start
            val deletionLength = oldText.length - newText.length
            val deletionEnd = deletionStart + deletionLength

            val adjustedSpans = currentLine.spanStyles.mapNotNull { span ->
                when {
                    span.start >= deletionStart && span.end <= deletionEnd -> null
                    span.start < deletionStart && span.end > deletionEnd -> span.copy(
                        end = span.end - deletionLength
                    )
                    span.start < deletionStart && span.end > deletionStart && span.end <= deletionEnd -> span.copy(
                        end = deletionStart
                    )
                    span.start >= deletionStart && span.start < deletionEnd && span.end > deletionEnd -> span.copy(
                        start = deletionStart,
                        end = span.end - deletionLength
                    )
                    span.start >= deletionEnd -> span.copy(
                        start = span.start - deletionLength,
                        end = span.end - deletionLength
                    )
                    span.end <= deletionStart -> span
                    else -> span
                }
            }.filter {
                it.start < it.end && it.start >= 0 && it.end <= newText.length
            }

            val updatedLine = currentLine.copy(
                content = newText,
                spanStyles = adjustedSpans
            )
            onLinesChange(lines.toMutableList().also { it[index] = updatedLine })
        }

        else -> {
            val updatedLine = currentLine.copy(content = newText)
            onLinesChange(lines.toMutableList().also { it[index] = updatedLine })
        }
    }
}

private fun applyFormattingToggleToSelection(
    index: Int,
    lines: List<NoteLine>,
    selection: TextRange,
    styleKey: String,
    onLinesChange: (List<NoteLine>) -> Unit
) {
    val currentLine = lines[index]
    val newSpanStyles = currentLine.spanStyles.toMutableList()

    val hasStyle = currentLine.spanStyles.any { style ->
        style.start < selection.end && style.end > selection.start &&
                when (styleKey) {
                    "BOLD" -> style.fontWeight != null
                    "ITALIC" -> style.fontStyle != null
                    "UNDERLINE" -> style.textDecoration != null
                    else -> false
                }
    }

    if (hasStyle) {
        newSpanStyles.removeAll { style ->
            style.start < selection.end && style.end > selection.start &&
                    when (styleKey) {
                        "BOLD" -> style.fontWeight != null
                        "ITALIC" -> style.fontStyle != null
                        "UNDERLINE" -> style.textDecoration != null
                        else -> false
                    }
        }
    } else {
        val newStyle = SerializableSpanStyle(
            start = selection.start,
            end = selection.end,
            fontWeight = if (styleKey == "BOLD") FontWeight.Bold.weight else null,
            fontStyle = if (styleKey == "ITALIC") "italic" else null,
            textDecoration = if (styleKey == "UNDERLINE") "underline" else null
        )
        newSpanStyles.add(newStyle)
    }

    onLinesChange(lines.toMutableList().also {
        it[index] = currentLine.copy(spanStyles = newSpanStyles)
    })
}

private fun applyFormattingToSelection(
    index: Int,
    lines: List<NoteLine>,
    selection: TextRange,
    styleKey: String?,
    color: Color?,
    fontSize: Int?,
    onLinesChange: (List<NoteLine>) -> Unit
) {
    val currentLine = lines[index]
    val newSpanStyles = currentLine.spanStyles.toMutableList()

    when {
        styleKey != null -> {
            return
        }
        color != null -> {
            newSpanStyles.removeAll { style ->
                style.start < selection.end && style.end > selection.start && style.color != null
            }
        }
        fontSize != null -> {
            newSpanStyles.removeAll { style ->
                style.start < selection.end && style.end > selection.start && style.fontSize != null
            }
        }
    }

    when {
        color != null -> {
            val newStyle = SerializableSpanStyle(
                start = selection.start,
                end = selection.end,
                color = color.value
            )
            newSpanStyles.add(newStyle)
        }
        fontSize != null -> {
            val newStyle = SerializableSpanStyle(
                start = selection.start,
                end = selection.end,
                fontSize = fontSize.toFloat()
            )
            newSpanStyles.add(newStyle)
        }
    }

    onLinesChange(lines.toMutableList().also {
        it[index] = currentLine.copy(spanStyles = newSpanStyles)
    })
}

// Function to save image to internal storage
private fun saveImageToInternalStorage(context: Context, uri: Uri): Uri {
    val inputStream = context.contentResolver.openInputStream(uri)
    val fileName = "note_image_${System.currentTimeMillis()}.jpg"
    val file = File(context.filesDir, fileName)

    inputStream?.use { input ->
        FileOutputStream(file).use { output ->
            input.copyTo(output)
        }
    }

    return Uri.fromFile(file)
}

// Continue with the rest of the composables...
@Composable
fun NoteImageItem(
    line: NoteLine,
    index: Int,
    totalLines: Int,
    onDelete: () -> Unit,
    onImageUpdate: (NoteLine) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: (targetIndex: Int?) -> Unit = {},
    onDrag: (offset: Offset) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // State management
    var scale by rememberSaveable(line.id) { mutableFloatStateOf(line.imageScale) }
    var showControls by rememberSaveable(line.id) { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var showDropIndicator by remember { mutableStateOf(false) }
    var dropIndicatorPosition by remember { mutableStateOf("none") } // "top", "bottom", "none"

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Hide controls after 3 seconds of inactivity
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    // Update drop indicator based on drag offset
    LaunchedEffect(dragOffset, isDragging) {
        if (isDragging) {
            showDropIndicator = true
            dropIndicatorPosition = when {
                dragOffset.y < -50f -> "top"
                dragOffset.y > 50f -> "bottom"
                else -> "none"
            }
        } else {
            showDropIndicator = false
            dropIndicatorPosition = "none"
        }
    }

    // Create a stable image request
    val imageRequest = remember(line.content) {
        ImageRequest.Builder(context)
            .data(line.content)
            .memoryCacheKey(line.content)
            .diskCacheKey(line.content)
            .crossfade(false)
            .build()
    }

    // Main container with drop indicators
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = showDropIndicator && dropIndicatorPosition == "top",
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        thickness = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                CircleShape
                            )
                            .padding(8.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Drop above",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        thickness = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Main image card - NO SIZE CHANGES, only fade
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight()
                    .graphicsLayer {
                        alpha = if (isDragging) 0.6f else 1f
                    },
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 2.dp
                ),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column {
                    // Drag handle area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { _ ->
                                        isDragging = true
                                        dragOffset = Offset.Zero
                                        onDragStart()
                                    },
                                    onDragEnd = {
                                        // Calculate target index based on total drag offset
                                        val itemHeight = 300f
                                        val verticalMovement = dragOffset.y / itemHeight
                                        val targetIndexOffset = verticalMovement.toInt()
                                        val targetIndex = (index + targetIndexOffset).coerceIn(0, totalLines - 1)

                                        val finalTargetIndex = if (targetIndex != index) targetIndex else null
                                        onDragEnd(finalTargetIndex)

                                        // Reset state
                                        isDragging = false
                                        dragOffset = Offset.Zero
                                        showDropIndicator = false
                                        dropIndicatorPosition = "none"
                                    },
                                    onDrag = { _, dragAmount ->
                                        dragOffset += Offset(0f, dragAmount.y)
                                        onDrag(dragOffset)
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = "Drag to reorder",
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.image_drag_reorder),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // Image container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .clickable {
                                showControls = !showControls
                            }
                    ) {
                        // The image
                        SubcomposeAsyncImage(
                            model = imageRequest,
                            contentDescription = stringResource(R.string.note_image),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .pointerInput(scale, isDragging) {
                                    if (!isDragging) {
                                        detectTransformGestures { _, _, zoom, _ ->
                                            scale = (scale * zoom).coerceIn(1f, 5f)
                                            showControls = true

                                            onImageUpdate(
                                                line.copy(
                                                    imageScale = scale,
                                                    imageOffsetX = 0f,
                                                    imageOffsetY = 0f
                                                )
                                            )
                                        }
                                    }
                                },
                            contentScale = ContentScale.Fit,
                            loading = {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            error = {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.BrokenImage,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        stringResource(R.string.note_image),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        )

                        // Controls overlay
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showControls,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Semi-transparent background
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.2f))
                                )

                                // Top-right delete button
                                IconButton(
                                    onClick = onDelete,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(36.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.image_delete),
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }

                                // Bottom center controls
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(12.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                            RoundedCornerShape(20.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Zoom out control
                                    IconButton(
                                        onClick = {
                                            scale = (scale - 0.25f).coerceIn(1f, 5f)
                                            onImageUpdate(
                                                line.copy(
                                                    imageScale = scale,
                                                    imageOffsetX = 0f,
                                                    imageOffsetY = 0f
                                                )
                                            )
                                            showControls = true
                                        },
                                        modifier = Modifier.size(36.dp),
                                        enabled = scale > 1f
                                    ) {
                                        Icon(
                                            Icons.Default.Remove,
                                            contentDescription = stringResource(R.string.image_zoom_out),
                                            modifier = Modifier.size(20.dp),
                                            tint = if (scale > 1f)
                                                MaterialTheme.colorScheme.onSurface
                                            else
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                        )
                                    }

                                    // Zoom level display
                                    Text(
                                        text = "${(scale * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.width(50.dp),
                                        textAlign = TextAlign.Center
                                    )

                                    // Zoom in control
                                    IconButton(
                                        onClick = {
                                            scale = (scale + 0.25f).coerceIn(1f, 5f)
                                            onImageUpdate(
                                                line.copy(
                                                    imageScale = scale,
                                                    imageOffsetX = 0f,
                                                    imageOffsetY = 0f
                                                )
                                            )
                                            showControls = true
                                        },
                                        modifier = Modifier.size(36.dp),
                                        enabled = scale < 5f
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = stringResource(R.string.image_zoom_in),
                                            modifier = Modifier.size(20.dp),
                                            tint = if (scale < 5f)
                                                MaterialTheme.colorScheme.onSurface
                                            else
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                        )
                                    }

                                    VerticalDivider(
                                        modifier = Modifier.height(20.dp),
                                        thickness = 1.dp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                    )

                                    // Reset button
                                    IconButton(
                                        onClick = {
                                            scale = 1f
                                            onImageUpdate(
                                                line.copy(
                                                    imageScale = 1f,
                                                    imageOffsetX = 0f,
                                                    imageOffsetY = 0f
                                                )
                                            )
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = stringResource(R.string.image_reset),
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    // Position controls
                                    if (index > 0 || index < totalLines - 1) {
                                        VerticalDivider(
                                            modifier = Modifier.height(20.dp),
                                            thickness = 1.dp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                        )

                                        if (index > 0) {
                                            IconButton(
                                                onClick = onMoveUp,
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.KeyboardArrowUp,
                                                    contentDescription = stringResource(R.string.image_move_up),
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }

                                        if (index < totalLines - 1) {
                                            IconButton(
                                                onClick = onMoveDown,
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.KeyboardArrowDown,
                                                    contentDescription = stringResource(R.string.image_move_down),
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }

                                // Position indicator
                                if (totalLines > 1) {
                                    Text(
                                        text = "${index + 1}/$totalLines",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom drop indicator
            AnimatedVisibility(
                visible = showDropIndicator && dropIndicatorPosition == "bottom",
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        thickness = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                CircleShape
                            )
                            .padding(8.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Drop below",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        thickness = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun NoteSeparatorItem(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )

        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(32.dp)
                .padding(start = 8.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.note_delete_separator),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun ChecklistLineItem(
    line: NoteLine,
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onFocus: () -> Unit,
    onCheckChange: (Boolean) -> Unit,
    onDeleteLine: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = line.isChecked,
            onCheckedChange = onCheckChange,
            modifier = Modifier.padding(end = 8.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                checkmarkColor = MaterialTheme.colorScheme.onPrimary
            )
        )

        BasicTextField(
            value = textFieldValue,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        onFocus()
                    }
                }
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown &&
                        textFieldValue.text.isEmpty() &&
                        keyEvent.key == Key.Backspace) {
                        onDeleteLine()
                        true
                    } else false
                },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (line.isChecked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else LocalContentColor.current,
                textDecoration = if (line.isChecked) TextDecoration.LineThrough else null
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Default
            )
        )

        IconButton(
            onClick = onDeleteLine,
            modifier = Modifier
                .size(32.dp)
                .padding(start = 4.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.note_delete_checkbox),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun BulletLineItem(
    line: NoteLine,
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onFocus: () -> Unit,
    onDeleteLine: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.FiberManualRecord,
            contentDescription = stringResource(R.string.note_bullet),
            modifier = Modifier
                .padding(end = 8.dp)
                .size(8.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        BasicTextField(
            value = textFieldValue,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        onFocus()
                    }
                }
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown &&
                        textFieldValue.text.isEmpty() &&
                        keyEvent.key == Key.Backspace) {
                        onDeleteLine()
                        true
                    } else false
                },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = LocalContentColor.current
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Default
            )
        )
    }
}

@Composable
fun NoteLineItem(
    line: NoteLine,
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onFocus: () -> Unit,
    onDeleteLine: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        onFocus()
                    }
                }
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown &&
                        textFieldValue.text.isEmpty() &&
                        keyEvent.key == Key.Backspace) {
                        onDeleteLine()
                        true
                    } else false
                },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = LocalContentColor.current
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Default
            )
        )
    }
}

@Composable
private fun NoteFormattingToolbar(
    toggledStyles: Set<String>,
    activeColor: Color?,
    activeFontSize: Int,
    isBulletModeActive: Boolean,
    isChecklistModeActive: Boolean,
    onStyleToggle: (String) -> Unit,
    onColorChange: (Color) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onBulletModeToggle: () -> Unit,
    onChecklistModeToggle: () -> Unit,
    onAddSeparator: () -> Unit,
    onAddImage: () -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }
    var showFontSizePicker by remember { mutableStateOf(false) }

    val colors = listOf(
        Color.White, Color.Black, Color.Gray, Color(0xFF424242), Color(0xFF9E9E9E),
        Color.Red, Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7), Color.Blue,
        Color(0xFF2196F3), Color(0xFF00BCD4), Color(0xFF009688), Color.Green, Color(0xFF8BC34A),
        Color(0xFFCDDC39), Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFFF5722),
        Color(0xFF795548), Color(0xFF8D6E63), Color(0xFF6D4C41), Color(0xFF5D4037), Color(0xFF3E2723)
    )

    val fontSizes = listOf(10, 12, 14, 16, 18, 20, 24, 28, 32, 36, 48)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bold Button
            IconButton(
                onClick = { onStyleToggle("BOLD") },
                modifier = Modifier
                    .background(
                        if (toggledStyles.contains("BOLD"))
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                        else
                            Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    Icons.Default.FormatBold,
                    contentDescription = stringResource(R.string.formatting_bold),
                    tint = if (toggledStyles.contains("BOLD"))
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
            }

            // Italic Button
            IconButton(
                onClick = { onStyleToggle("ITALIC") },
                modifier = Modifier
                    .background(
                        if (toggledStyles.contains("ITALIC"))
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                        else
                            Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    Icons.Default.FormatItalic,
                    contentDescription = stringResource(R.string.formatting_italic),
                    tint = if (toggledStyles.contains("ITALIC"))
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
            }

            // Underline Button
            IconButton(
                onClick = { onStyleToggle("UNDERLINE") },
                modifier = Modifier
                    .background(
                        if (toggledStyles.contains("UNDERLINE"))
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                        else
                            Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    Icons.Default.FormatUnderlined,
                    contentDescription = stringResource(R.string.formatting_underline),
                    tint = if (toggledStyles.contains("UNDERLINE"))
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
            }

            // Font Size Picker
            Box {
                IconButton(onClick = { showFontSizePicker = !showFontSizePicker }) {
                    Icon(
                        Icons.Default.FormatSize,
                        contentDescription = stringResource(R.string.formatting_font_size),
                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                }
                DropdownMenu(
                    expanded = showFontSizePicker,
                    onDismissRequest = { showFontSizePicker = false }
                ) {
                    fontSizes.forEach { fontSize ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${fontSize}sp",
                                    color = if (activeFontSize == fontSize)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        LocalContentColor.current
                                )
                            },
                            onClick = {
                                onFontSizeChange(fontSize)
                                showFontSizePicker = false
                            }
                        )
                    }
                }
            }

            // Color Picker
            Box {
                IconButton(onClick = { showColorPicker = !showColorPicker }) {
                    Icon(
                        Icons.Default.FormatColorText,
                        contentDescription = stringResource(R.string.formatting_text_color),
                        tint = activeColor ?: MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                }
                DropdownMenu(
                    expanded = showColorPicker,
                    onDismissRequest = { showColorPicker = false }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        for (rowIndex in 0 until (colors.size + 4) / 5) {
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (colIndex in 0 until 5) {
                                    val colorIndex = rowIndex * 5 + colIndex
                                    if (colorIndex < colors.size) {
                                        val color = colors[colorIndex]
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .clickable {
                                                    onColorChange(color)
                                                    showColorPicker = false
                                                }
                                                .border(
                                                    width = 2.dp,
                                                    color = if (activeColor == color)
                                                        MaterialTheme.colorScheme.primary
                                                    else if (color == Color.White)
                                                        Color.Gray
                                                    else
                                                        Color.Transparent,
                                                    shape = CircleShape
                                                )
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.size(32.dp))
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        TextButton(
                            onClick = {
                                onColorChange(Color.Unspecified)
                                showColorPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.formatting_clear_color))
                        }
                    }
                }
            }

            // Bullet Mode Toggle
            IconButton(
                onClick = onBulletModeToggle,
                modifier = Modifier
                    .background(
                        if (isBulletModeActive)
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                        else
                            Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    Icons.Default.FiberManualRecord,
                    contentDescription = stringResource(R.string.formatting_bullet_mode),
                    tint = if (isBulletModeActive)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
            }

            // Checklist Mode Toggle
            IconButton(
                onClick = onChecklistModeToggle,
                modifier = Modifier
                    .background(
                        if (isChecklistModeActive)
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                        else
                            Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    Icons.Default.CheckBox,
                    contentDescription = stringResource(R.string.formatting_checklist_mode),
                    tint = if (isChecklistModeActive)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
            }

            // Add Separator Button
            IconButton(onClick = onAddSeparator) {
                Icon(
                    Icons.Default.HorizontalRule,
                    contentDescription = stringResource(R.string.formatting_add_separator),
                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
            }

            // Add Image Button
            IconButton(onClick = onAddImage) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = stringResource(R.string.formatting_add_image),
                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
            }
        }
    }
}
