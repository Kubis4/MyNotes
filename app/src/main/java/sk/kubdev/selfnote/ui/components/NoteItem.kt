package sk.kubdev.selfnote.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sk.kubdev.selfnote.LineType
import sk.kubdev.selfnote.NoteLine
import sk.kubdev.selfnote.R
import sk.kubdev.selfnote.data.remote.local.entities.Note
import sk.kubdev.selfnote.data.remote.local.entities.NoteType
import sk.kubdev.selfnote.toNoteLines
import sk.kubdev.selfnote.ui.theme.NoteColorPalette
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteItem(
    note: Note,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onPinToggle: (() -> Unit)? = null,
    showClickableArea: Boolean = true,
    isExpanded: Boolean = false,
    // Collaborative parameters
    isCollaborative: Boolean = false,
    collaboratorCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // ✅ FIXED: Create safe date formatters without try-catch in composable
    val dateFormatter = remember {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    }

    val timeFormatter = remember {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    }

    // ✅ FIXED: Use the same background color logic for both types
    val backgroundColor = NoteColorPalette.getColorByIndex(note.colorIndex)

    // ✅ FIXED: Use the same text color logic for both types
    val textColor = if (backgroundColor == Color.White) {
        MaterialTheme.colorScheme.onSurface
    } else {
        Color.Black.copy(alpha = 0.87f)
    }

    val secondaryTextColor = if (backgroundColor == Color.White) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    } else {
        Color.Black.copy(alpha = 0.6f)
    }

    // Save expanded state per note
    val prefs = remember { context.getSharedPreferences("note_item_prefs", android.content.Context.MODE_PRIVATE) }
    var isNoteExpanded by remember(note.id) {
        mutableStateOf(prefs.getBoolean("note_expanded_${note.id}", false))
    }

    // Parse note content
    val noteLines = note.content.toNoteLines()

    // ✅ FIXED: Safe string resource access without try-catch in composable
    val emptyChecklistText = remember { "Empty checklist" }

    // Generate preview content based on note type
    val previewContent = when (note.type) {
        NoteType.TEXT -> {
            if (isExpanded || isNoteExpanded) {
                val fullText = noteLines.getTextContent()
                val preview = if (fullText.length > 300) {
                    fullText.take(300) + "..."
                } else {
                    fullText
                }
                TextPreviewContent(preview)
            } else {
                // ✅ FIXED: Use safe getFirstLinePreview
                TextPreviewContent(noteLines.getFirstLinePreview(60))
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

                val hasMoreToShow = totalItems > previewItems.size

                ChecklistPreviewContent(
                    completedCount = checkedItems,
                    totalCount = totalItems,
                    previewItems = previewItems,
                    hasMore = hasMoreToShow,
                    canToggleExpand = totalItems > baseItemsToShow,
                    isNoteExpanded = isNoteExpanded
                )
            } else {
                TextPreviewContent(emptyChecklistText)
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        // ✅ FIXED: Same elevation for both types
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        // ✅ FIXED: Subtle border only for collaborative notes
        border = if (isCollaborative) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row with title and icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Title column
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    if (note.title.isNotEmpty()) {
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = if (isExpanded || isNoteExpanded) 3 else 1,
                            overflow = TextOverflow.Ellipsis,
                            color = textColor
                        )
                    }
                }

                // Icons row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ✅ UPDATED: Subtle collaborative indicator using same theme colors
                    if (isCollaborative) {
                        Icon(
                            Icons.Default.CloudSync,
                            contentDescription = "Shared",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    // Note type icon (same styling for both)
                    Icon(
                        imageVector = when (note.type) {
                            NoteType.TEXT -> Icons.Default.EditNote
                            NoteType.CHECKLIST -> Icons.Default.CheckBox
                        },
                        contentDescription = when (note.type) {
                            NoteType.TEXT -> "Text Note"
                            NoteType.CHECKLIST -> "Checklist Note"
                        },
                        tint = if (backgroundColor == Color.White) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        },
                        modifier = Modifier.size(20.dp)
                    )

                    // Pin icon - ONLY FOR REGULAR NOTES
                    if (!isCollaborative && (note.isPinned || onPinToggle != null)) {
                        IconButton(
                            onClick = { onPinToggle?.invoke() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = if (note.isPinned) "Unpin" else "Pin",
                                tint = if (note.isPinned) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    secondaryTextColor
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Content preview
            if (note.title.isNotEmpty() || previewContent !is EmptyContent) {
                Spacer(modifier = Modifier.height(4.dp))
            }

            when (previewContent) {
                is TextPreviewContent -> {
                    if (previewContent.text.isNotEmpty()) {
                        Text(
                            text = previewContent.text,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (isNoteExpanded) 8 else if (isExpanded) 3 else 1,
                            overflow = TextOverflow.Ellipsis,
                            color = textColor.copy(alpha = 0.87f)
                        )

                        val longText = noteLines.getTextContent().length > 60
                        if (longText) {
                            TextButton(
                                onClick = {
                                    isNoteExpanded = !isNoteExpanded
                                    prefs.edit().putBoolean("note_expanded_${note.id}", isNoteExpanded).apply()
                                },
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isNoteExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isNoteExpanded) "Show Less" else "Show More",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
                is ChecklistPreviewContent -> {
                    Column {
                        // Completed count with progress indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${previewContent.completedCount}/${previewContent.totalCount} completed",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = textColor
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // ✅ FIXED: Same progress bar styling for both types
                            LinearProgressIndicator(
                                progress = if (previewContent.totalCount > 0) {
                                    previewContent.completedCount.toFloat() / previewContent.totalCount
                                } else 0f,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Preview items
                        previewContent.previewItems.forEach { item ->
                            Row(
                                modifier = Modifier.padding(top = 2.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = if (item.isChecked) "✓" else "○",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (item.isChecked) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        secondaryTextColor
                                    },
                                    modifier = Modifier.width(16.dp)
                                )
                                Text(
                                    text = item.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = if (isNoteExpanded) 2 else 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (item.isChecked) {
                                        secondaryTextColor.copy(alpha = 0.6f)
                                    } else {
                                        secondaryTextColor
                                    },
                                    textDecoration = if (item.isChecked) {
                                        TextDecoration.LineThrough
                                    } else {
                                        TextDecoration.None
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Expand/collapse button
                        if (previewContent.canToggleExpand) {
                            TextButton(
                                onClick = {
                                    isNoteExpanded = !isNoteExpanded
                                    prefs.edit().putBoolean("note_expanded_${note.id}", isNoteExpanded).apply()
                                },
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isNoteExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isNoteExpanded) "Show Less" else "Show More",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
                is EmptyContent -> {
                    // Don't show anything
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer with date/time and collaborator info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // ✅ FIXED: Safe date access - use only existing properties
                Column {
                    // Use the timestamp that exists in your Note entity
                    val timestamp = note.lastModifiedAt ?: note.createdAt
                    val date = java.util.Date(timestamp) // ✅ FIXED: Explicit constructor

                    Text(
                        text = dateFormatter.format(date),
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor
                    )
                    Text(
                        text = timeFormatter.format(date),
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor
                    )
                }

                // ✅ UPDATED: Subtle collaborator info with consistent styling
                if (isCollaborative && collaboratorCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (collaboratorCount) {
                                1 -> "Shared"
                                else -> "Shared with ${collaboratorCount - 1} others"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ✅ Keep your existing preview content classes
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
object EmptyContent : PreviewContent()

// ✅ FIXED: Add the missing extension functions
fun List<NoteLine>.getTextContent(): String {
    return filter { it.type == LineType.TEXT }
        .joinToString("\n") { it.content }
        .trim()
}

// ✅ ADDED: Missing getFirstLinePreview extension function
fun List<NoteLine>.getFirstLinePreview(maxLength: Int = 60): String {
    val textContent = getTextContent()
    return if (textContent.length > maxLength) {
        textContent.take(maxLength) + "..."
    } else {
        textContent
    }
}
