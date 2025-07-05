package sk.kubdev.selfnote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NoteItem(
    note: Note,
    onClick: () -> Unit
) {
    // Decode the note content from JSON into a list of lines
    val noteLines = remember(note.content) { note.content.toNoteLines() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Show an icon indicating the note type for better clarity
                Icon(
                    imageVector = if (note.type == NoteType.CHECKLIST) Icons.Default.Checklist else Icons.Default.Notes,
                    contentDescription = "Note Type",
                    modifier = Modifier.size(20.dp).padding(end = 6.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = note.title.ifEmpty { if (note.type == NoteType.CHECKLIST) "Untitled To-Do" else "Untitled Note" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Display a preview of the first 3 lines
            noteLines.take(3).forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (line.type == LineType.CHECKLIST) {
                        Icon(
                            imageVector = if (line.isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = "Checkbox",
                            modifier = Modifier.size(20.dp).padding(end = 6.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Use plain content for preview; rich text is not needed here
                    Text(
                        text = line.content,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (line.isChecked) TextDecoration.LineThrough else null,
                        color = if (line.isChecked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else LocalContentColor.current
                    )
                }
            }

            if (noteLines.size > 3) {
                Text(
                    text = "...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                // Use a standard and clean date format
                text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(note.lastModifiedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
