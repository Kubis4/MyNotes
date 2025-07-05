package sk.kubdev.selfnote

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- NEW: A utility to format timestamps nicely ---
fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}