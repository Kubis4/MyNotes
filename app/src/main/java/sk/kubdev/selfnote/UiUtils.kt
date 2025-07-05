package sk.kubdev.selfnote

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

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
