package sk.kubdev.mynotes

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import java.text.Normalizer

/**
 * Lowercases and strips diacritics (e.g. "Nákupný" -> "nakupny") so search
 * matching works regardless of accents - useful for Slovak/Czech text where
 * users often type without diacritics.
 */
fun String.normalizeForSearch(): String {
    val decomposed = Normalizer.normalize(this, Normalizer.Form.NFD)
    return decomposed.replace(Regex("\\p{Mn}+"), "").lowercase()
}

/**
 * Returns up to the first two initials of a name, uppercased.
 * Shared helper used by collaboration UI to render avatar placeholders.
 */
fun getInitials(name: String): String {
    return name.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .uppercase()
}

/**
 * A shared utility function to get the combined SpanStyle at a specific
 * character index within an AnnotatedString.
 * It's marked as `internal` to be accessible throughout the :app module.
 */
internal fun AnnotatedString.getSpanStyle(index: Int): SpanStyle {
    if (this.text.isEmpty()) return SpanStyle()
    // Check the character before the cursor for styling
    val effectiveIndex = if (index > 0) index - 1 else 0

    // Find all styles that apply at that index
    val styles = this.spanStyles.filter {
        effectiveIndex in it.start until it.end
    }

    // Merge all found styles into one
    var mergedStyle = SpanStyle()
    styles.forEach {
        mergedStyle = mergedStyle.merge(it.item)
    }
    return mergedStyle
}
