package sk.kubdev.selfnote

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sk.kubdev.selfnote.data.remote.local.entities.Note
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NoteContent(
    note: Note,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(modifier = modifier) {
        Text(
            text = note.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (note.content.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = formatDate(note.lastModifiedAt, context),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

private fun formatDate(timestamp: Long, context: android.content.Context): String {
    val date = Date(timestamp)
    val now = Date()
    val diff = now.time - date.time

    return when {
        diff < 60000 -> context.getString(R.string.time_just_now_capitalized)
        diff < 3600000 -> context.getString(R.string.time_minutes_ago, diff / 60000)
        diff < 86400000 -> context.getString(R.string.time_hours_ago, diff / 3600000)
        else -> SimpleDateFormat(context.getString(R.string.date_format_full), Locale.getDefault()).format(date)
    }
}
