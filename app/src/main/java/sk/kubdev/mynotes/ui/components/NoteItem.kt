package sk.kubdev.mynotes.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sk.kubdev.mynotes.LineType
import sk.kubdev.mynotes.NoteLine
import sk.kubdev.mynotes.R
import sk.kubdev.mynotes.data.remote.local.entities.Note
import sk.kubdev.mynotes.data.remote.local.entities.NoteType
import sk.kubdev.mynotes.toNoteLines
import sk.kubdev.mynotes.ui.theme.NoteColorPalette
import sk.kubdev.mynotes.ui.theme.notePattern
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteItem(
    note: Note,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onPinToggle: (() -> Unit)? = null,
    isExpanded: Boolean = false,
    // Collaborative parameters
    isCollaborative: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val dateFormatter = remember {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    }

    val timeFormatter = remember {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    }

    // Whole card carries the note color (Keep-style); see NoteColorPalette.getCardBackground.
    val cardColor = NoteColorPalette.getCardBackground(
        note.colorIndex,
        isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    )
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    val prefs = remember { context.getSharedPreferences("note_item_prefs", android.content.Context.MODE_PRIVATE) }
    var isNoteExpanded by remember(note.id) {
        mutableStateOf(prefs.getBoolean("note_expanded_${note.id}", false))
    }

    val noteLines = remember(note.content) { note.content.toNoteLines() }

    val previewContent = remember(noteLines, isExpanded, isNoteExpanded, note.type) {
        when (note.type) {
            NoteType.TEXT -> {
                if (isExpanded || isNoteExpanded) {
                    val fullText = noteLines.joinToString("\n") { it.content }.trim()
                    val preview = if (fullText.length > 300) {
                        fullText.take(300) + "..."
                    } else {
                        fullText
                    }
                    TextPreviewContent(preview)
                } else {
                    val firstLine = noteLines.firstOrNull { it.content.isNotBlank() }?.content ?: ""
                    TextPreviewContent(if (firstLine.length > 60) firstLine.take(60) + "..." else firstLine)
                }
            }
            NoteType.CHECKLIST -> {
                val actualItems = noteLines.filter { it.type == LineType.CHECKLIST }
                val totalItems = actualItems.size
                val checkedItems = actualItems.count { it.isChecked }

                if (totalItems > 0) {
                    val uncheckedItems = actualItems.filter { !it.isChecked }
                    val checkedItemsList = actualItems.filter { it.isChecked }

                    val baseItemsToShow = if (isExpanded) 2 else 1
                    val expandedItemsToShow = 8
                    val itemsToShow = if (isNoteExpanded) expandedItemsToShow else baseItemsToShow

                    val previewItems = buildList {
                        addAll(uncheckedItems.take(itemsToShow))
                        val remainingSlots = itemsToShow - size
                        if (remainingSlots > 0) {
                            addAll(checkedItemsList.take(remainingSlots))
                        }
                    }

                    ChecklistPreviewContent(
                        completedCount = checkedItems,
                        totalCount = totalItems,
                        previewItems = previewItems,
                        hasMore = totalItems > previewItems.size,
                        canToggleExpand = totalItems > baseItemsToShow,
                        isNoteExpanded = isNoteExpanded
                    )
                } else {
                    TextPreviewContent("Empty checklist")
                }
            }
        }
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = cardColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .notePattern(note.patternIndex, MaterialTheme.colorScheme.onSurface)
                .padding(12.dp)
        ) {
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
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (isExpanded || isNoteExpanded) 3 else 1,
                        overflow = TextOverflow.Ellipsis,
                        color = textColor
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCollaborative) {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = "Shared",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }

                    Icon(
                        imageVector = when (note.type) {
                            NoteType.TEXT -> Icons.Default.EditNote
                            NoteType.CHECKLIST -> Icons.Default.CheckBox
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )

                    if (!isCollaborative && (note.isPinned || onPinToggle != null)) {
                        IconButton(
                            onClick = { onPinToggle?.invoke() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = "Pin",
                                tint = if (note.isPinned) MaterialTheme.colorScheme.primary else secondaryTextColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (previewContent) {
                is TextPreviewContent -> {
                    if (previewContent.text.isNotEmpty()) {
                        Text(
                            text = previewContent.text,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (isNoteExpanded) 8 else if (isExpanded) 3 else 1,
                            overflow = TextOverflow.Ellipsis,
                            color = textColor.copy(alpha = 0.8f)
                        )
                    }
                }
                is ChecklistPreviewContent -> {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${previewContent.completedCount}/${previewContent.totalCount}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = secondaryTextColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            LinearProgressIndicator(
                                progress = { if (previewContent.totalCount > 0) previewContent.completedCount.toFloat() / previewContent.totalCount else 0f },
                                modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        previewContent.previewItems.forEach { item ->
                            Row(
                                modifier = Modifier.padding(top = 2.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    if (item.isChecked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp).padding(top = 2.dp),
                                    tint = if (item.isChecked) MaterialTheme.colorScheme.primary else secondaryTextColor
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = item.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (item.isChecked) secondaryTextColor.copy(alpha = 0.6f) else textColor,
                                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val date = java.util.Date(note.lastModifiedAt)
                Text(
                    text = "${dateFormatter.format(date)} • ${timeFormatter.format(date)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor
                )
                
                val canExpand = if (previewContent is ChecklistPreviewContent) previewContent.canToggleExpand else noteLines.joinToString(" ") { it.content }.length > 60
                
                if (canExpand) {
                    IconButton(
                        onClick = {
                            isNoteExpanded = !isNoteExpanded
                            prefs.edit().putBoolean("note_expanded_${note.id}", isNoteExpanded).apply()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            if (isNoteExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

sealed class PreviewContent
data class TextPreviewContent(val text: String) : PreviewContent()
data class ChecklistPreviewContent(
    val completedCount: Int,
    val totalCount: Int,
    val previewItems: List<NoteLine>,
    val hasMore: Boolean,
    val canToggleExpand: Boolean,
    val isNoteExpanded: Boolean
) : PreviewContent()
