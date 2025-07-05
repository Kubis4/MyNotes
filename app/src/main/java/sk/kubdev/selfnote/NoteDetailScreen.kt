package sk.kubdev.selfnote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    noteIdArg: Int,
    navController: NavController,
    viewModel: NoteViewModel
) {
    var noteId by remember { mutableStateOf(noteIdArg) }
    var title by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf(listOf(NoteLine(content = ""))) }
    var isToolbarVisible by remember { mutableStateOf(false) }
    var focusedLineIndex by remember { mutableStateOf<Int?>(null) }
    var selection by remember { mutableStateOf(TextRange.Zero) }
    var toggledStyles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var activeColor by remember { mutableStateOf<Color?>(null) }

    val titleFocusRequester = remember { FocusRequester() }
    val lineFocusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

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

    LaunchedEffect(key1 = noteIdArg) {
        if (noteIdArg != 0) {
            viewModel.getNoteById(noteIdArg).collectLatest { note ->
                if (note != null) {
                    title = note.title
                    lines = if (note.content.isNotBlank() && note.content != "[]") note.content.toNoteLines() else listOf(NoteLine(content = ""))
                }
            }
        } else {
            isToolbarVisible = true
            delay(100)
            titleFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(focusedLineIndex, selection) {
        focusedLineIndex?.let { index ->
            if (index < lines.size && selection.collapsed) {
                val currentStyle = lines[index].toAnnotatedString().getSpanStyle(selection.start)
                val newToggled = mutableSetOf<String>()
                if (currentStyle.fontWeight == FontWeight.Bold) newToggled.add("BOLD")
                if (currentStyle.fontStyle == FontStyle.Italic) newToggled.add("ITALIC")
                if (currentStyle.textDecoration == TextDecoration.Underline) newToggled.add("UNDERLINE")
                toggledStyles = newToggled
                activeColor = if (currentStyle.color != Color.Unspecified) currentStyle.color else null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (noteIdArg == 0) "New Note" else "Edit Note") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isToolbarVisible = !isToolbarVisible }) {
                        Icon(Icons.Default.TextFields, "Toggle Toolbar")
                    }
                    if (noteId != 0) {
                        IconButton(onClick = {
                            viewModel.deleteNoteById(noteId)
                            navController.navigateUp()
                        }) {
                            Icon(Icons.Default.Delete, "Delete Note")
                        }
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            val contentJson = lines.toJson()
                            viewModel.finaliseAndSave(noteId, title, contentJson, NoteType.TEXT).join()
                            navController.navigateUp()
                        }
                    }) {
                        Icon(Icons.Default.Done, "Save Note")
                    }
                }
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            AnimatedVisibility(visible = isToolbarVisible) {
                FormattingToolbar(
                    toggledStyles = toggledStyles,
                    activeColor = activeColor,
                    onStyleToggle = { styleKey ->
                        toggledStyles = if (toggledStyles.contains(styleKey)) {
                            toggledStyles - styleKey
                        } else {
                            toggledStyles + styleKey
                        }
                    },
                    onColorChange = { color ->
                        activeColor = if (activeColor == color) null else color
                    },
                    onLineTypeChange = { newType ->
                        focusedLineIndex?.let { index ->
                            if (index < lines.size) {
                                val line = lines[index]
                                val finalType = if (line.type == newType) LineType.TEXT else newType
                                lines = lines.toMutableList().apply {
                                    this[index] = line.copy(type = finalType)
                                }
                            }
                        }
                    }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocusRequester),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = LocalContentColor.current
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = {
                        lineFocusRequesters[lines.firstOrNull()?.id]?.requestFocus()
                    }),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )
                if (title.isEmpty()) {
                    Text(
                        text = "Title",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            LazyColumn(modifier = Modifier.weight(1f), state = lazyListState) {
                itemsIndexed(lines, key = { _, line -> line.id }) { index, line ->
                    lineFocusRequesters.getOrPut(line.id) { FocusRequester() }

                    NoteLineItem(
                        line = line,
                        textFieldValue = TextFieldValue(
                            annotatedString = line.toAnnotatedString(),
                            selection = if (focusedLineIndex == index) selection else TextRange.Zero
                        ),
                        onValueChange = { newTfv ->
                            focusedLineIndex = index
                            selection = newTfv.selection
                            val oldText = lines[index].content
                            val newText = newTfv.text
                            val diff = newText.length - oldText.length

                            val annotatedString = if (diff > 0 && newTfv.selection.collapsed) {
                                val start = newTfv.selection.start - diff
                                val end = newTfv.selection.start
                                val builder = AnnotatedString.Builder(newTfv.annotatedString)
                                val combinedStyle = SpanStyle(
                                    fontWeight = if (toggledStyles.contains("BOLD")) FontWeight.Bold else null,
                                    fontStyle = if (toggledStyles.contains("ITALIC")) FontStyle.Italic else null,
                                    textDecoration = if (toggledStyles.contains("UNDERLINE")) TextDecoration.Underline else null,
                                    color = activeColor ?: Color.Unspecified
                                )
                                if (start < end) builder.addStyle(combinedStyle, start, end)
                                builder.toAnnotatedString()
                            } else {
                                newTfv.annotatedString
                            }

                            lines = lines.toMutableList().also {
                                it[index] = lines[index].copy(
                                    content = annotatedString.text,
                                    spanStyles = annotatedString.toSerializableSpanStyles()
                                )
                            }
                        },
                        onFocus = {
                            focusedLineIndex = index
                            if (focusedLineIndex != index || (selection.start == 0 && selection.end == 0)) {
                                selection = TextRange(lines[index].content.length)
                            }
                        },
                        onCheckChange = { isChecked ->
                            lines = lines.toMutableList().also { it[index] = line.copy(isChecked = isChecked) }
                        },
                        onBackspaceOnEmptyLine = {
                            if (index > 0) {
                                val previousLine = lines[index - 1]
                                val currentLine = lines[index]
                                val previousLineLength = previousLine.content.length

                                val mergedText = previousLine.content + currentLine.content
                                val shiftedStyles = currentLine.spanStyles.map {
                                    it.copy(start = it.start + previousLineLength, end = it.end + previousLineLength)
                                }
                                val mergedStyles = previousLine.spanStyles + shiftedStyles
                                val updatedPreviousLine = previousLine.copy(content = mergedText, spanStyles = mergedStyles)

                                lines = lines.toMutableList().apply {
                                    this[index - 1] = updatedPreviousLine
                                    removeAt(index)
                                }

                                coroutineScope.launch {
                                    delay(50)
                                    focusedLineIndex = index - 1
                                    selection = TextRange(previousLineLength)
                                    lineFocusRequesters[previousLine.id]?.requestFocus()
                                }
                            }
                        },
                        // <-- FIX: Added onEnterPressed handler
                        onEnterPressed = {
                            val currentLine = lines[index]
                            val cursorPosition = selection.start
                            val textBeforeCursor = currentLine.content.substring(0, cursorPosition)
                            val textAfterCursor = currentLine.content.substring(cursorPosition)

                            val updatedCurrentLine = currentLine.copy(content = textBeforeCursor)
                            val newLine = NoteLine(content = textAfterCursor, type = currentLine.type)

                            lines = lines.toMutableList().apply {
                                this[index] = updatedCurrentLine
                                add(index + 1, newLine)
                            }

                            coroutineScope.launch {
                                delay(50)
                                focusedLineIndex = index + 1
                                selection = TextRange(0)
                                lineFocusRequesters[newLine.id]?.requestFocus()
                            }
                        },
                        modifier = Modifier.focusRequester(lineFocusRequesters.getValue(line.id))
                    )
                }
            }
        }
    }
}

@Composable
fun NoteLineItem(
    line: NoteLine,
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onFocus: () -> Unit,
    onCheckChange: (Boolean) -> Unit,
    onBackspaceOnEmptyLine: () -> Unit,
    onEnterPressed: () -> Unit, // <-- FIX: Added parameter
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFocus
            )
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (line.type) {
            LineType.CHECKLIST -> Checkbox(checked = line.isChecked, onCheckedChange = onCheckChange)
            LineType.BULLET -> Text("•", modifier = Modifier.padding(end = 8.dp), style = MaterialTheme.typography.bodyLarge)
            else -> {}
        }

        Box(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) onFocus() }
                    .onKeyEvent { keyEvent -> // <-- FIX: Expanded key handling
                        if (keyEvent.type == KeyEventType.KeyUp) {
                            when (keyEvent.key) {
                                Key.Backspace -> if (textFieldValue.text.isEmpty()) {
                                    onBackspaceOnEmptyLine()
                                    return@onKeyEvent true
                                }
                                Key.Enter -> {
                                    onEnterPressed()
                                    return@onKeyEvent true
                                }
                            }
                        }
                        false
                    },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = LocalContentColor.current,
                    textDecoration = if (line.type == LineType.CHECKLIST && line.isChecked) TextDecoration.LineThrough else null
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
            )
            if (textFieldValue.text.isEmpty() && line.type == LineType.TEXT) {
                Text(
                    text = "Write a note...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}


@Composable
fun FormattingToolbar(
    toggledStyles: Set<String>,
    activeColor: Color?,
    onStyleToggle: (String) -> Unit,
    onColorChange: (Color) -> Unit,
    onLineTypeChange: (LineType) -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }
    val colors = listOf(Color.Black, Color.Gray, Color.Red, Color.Blue, Color.Green, Color.Magenta)

    val activeBackgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    val activeBorderColor = MaterialTheme.colorScheme.primary
    val iconTintColor = activeColor ?: LocalContentColor.current

    fun buttonModifier(isActive: Boolean): Modifier {
        return if (isActive) Modifier.background(activeBackgroundColor, RoundedCornerShape(4.dp))
        else Modifier
    }

    Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onStyleToggle("BOLD") }, modifier = buttonModifier(toggledStyles.contains("BOLD"))) {
                Icon(Icons.Default.FormatBold, "Bold")
            }
            IconButton(onClick = { onStyleToggle("ITALIC") }, modifier = buttonModifier(toggledStyles.contains("ITALIC"))) {
                Icon(Icons.Default.FormatItalic, "Italic")
            }
            IconButton(onClick = { onStyleToggle("UNDERLINE") }, modifier = buttonModifier(toggledStyles.contains("UNDERLINE"))) {
                Icon(Icons.Default.FormatUnderlined, "Underline")
            }
            Box {
                IconButton(onClick = { showColorPicker = !showColorPicker }) {
                    Icon(Icons.Default.FormatColorText, "Text Color", tint = iconTintColor)
                }
                DropdownMenu(expanded = showColorPicker, onDismissRequest = { showColorPicker = false }) {
                    Row(modifier = Modifier.padding(8.dp)) {
                        colors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable {
                                        onColorChange(color)
                                        showColorPicker = false
                                    }
                                    .border(
                                        width = 2.dp,
                                        color = if (activeColor == color) activeBorderColor else Color.Transparent,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }
            }
            IconButton(onClick = { onLineTypeChange(LineType.BULLET) }) {
                Icon(Icons.AutoMirrored.Filled.FormatListBulleted, "Bullet List")
            }
            IconButton(onClick = { onLineTypeChange(LineType.CHECKLIST) }) {
                Icon(Icons.Default.Checklist, "Checklist")
            }
        }
    }
}