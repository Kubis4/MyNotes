package sk.kubdev.mynotes

import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.graphics.Typeface
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
enum class LineType {
    TEXT, CHECKLIST, SEPARATOR, BULLET, IMAGE, HEADING, QUOTE, NUMBERED,
    // A plain horizontal rule (Evernote-style "---"), distinct from SEPARATOR - which is
    // an editable, labeled section-divider card used by the to-do list. This one has no
    // text of its own, just a thin line.
    DIVIDER
}

@Immutable
@Serializable
data class NoteLine(
    val id: String = UUID.randomUUID().toString(),
    val content: String = "",
    val type: LineType = LineType.TEXT,
    val isChecked: Boolean = false,
    val spanStyles: List<SerializableSpanStyle> = emptyList(),
    // Deprecated: font size briefly lived here as a whole-line property, to work around a
    // Jetpack Compose BasicTextField bug where a single field mixing font sizes across lines
    // got its cursor position wrong. Text editing now uses a native EditText backed by a real
    // android.text.Spannable, which has never had that bug, so size is a per-character span
    // again (SerializableSpanStyle.fontSize) just like bold/italic/underline/color. This field
    // is only read (and migrated into a span) for notes saved during that window - never
    // written again afterwards.
    val fontSize: Float? = null,
    // Collaborative notes: uid of the user who last edited this line, so shared
    // checklists can show WHO wrote each item (their account photo). Null for
    // local notes and for lines from app versions that predate the field.
    val editorId: String? = null,
    val imageScale: Float = 1f,
    val imageOffsetX: Float = 0f,
    val imageOffsetY: Float = 0f,
    val imageHeight: Float = 200f,
    val imageWidth: Float = 300f,
) {
    companion object {
        fun create(
            content: String = "",
            type: LineType = LineType.TEXT,
            isChecked: Boolean = false,
            spanStyles: List<SerializableSpanStyle> = emptyList(),
            imageScale: Float = 1f,
            imageOffsetX: Float = 0f,
            imageOffsetY: Float = 0f,
            imageHeight: Float = 200f,
            imageWidth: Float = 300f,
        ): NoteLine {
            return NoteLine(
                id = UUID.randomUUID().toString(),
                content = content,
                type = type,
                isChecked = isChecked,
                spanStyles = spanStyles,
                imageScale = imageScale,
                imageOffsetX = imageOffsetX,
                imageOffsetY = imageOffsetY,
                imageHeight = imageHeight,
                imageWidth = imageWidth
            )
        }
    }
}

@Immutable
@Serializable
data class SerializableSpanStyle(
    val start: Int,
    val end: Int,
    val fontWeight: Int? = null,
    val fontStyle: String? = null,
    val textDecoration: String? = null,
    val color: ULong? = null,
    val fontSize: Float? = null
)

data class SelectionFormattingSnapshot(
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val color: ULong? = null,
    val fontSize: Int? = null
)

// Reads the combined formatting at a single offset (used to sync the toolbar's toggle/active
// state to whatever's actually under the cursor).
fun List<SerializableSpanStyle>.formattingAtOffset(offset: Int): SelectionFormattingSnapshot {
    val applicable = filter { offset >= it.start && offset < it.end }
    if (applicable.isEmpty()) return SelectionFormattingSnapshot()
    var bold = false
    var italic = false
    var underline = false
    var color: ULong? = null
    var fontSize: Int? = null
    applicable.forEach { span ->
        if (span.fontWeight != null && span.fontWeight >= FontWeightBold) bold = true
        if (span.fontStyle == "italic") italic = true
        if (span.textDecoration == "underline") underline = true
        span.color?.let { color = it }
        span.fontSize?.let { fontSize = it.toInt() }
    }
    return SelectionFormattingSnapshot(bold, italic, underline, color, fontSize)
}

private const val FontWeightBold = 700

// Converts our serializable model into a real android.text.Spannable, the same
// representation native EditText/TextView use - this is what lets one field mix font sizes
// (or any other formatting) across runs with correct, OS-native cursor/line-metric behavior.
//
// scaledDensity (Resources.getDisplayMetrics().scaledDensity) converts our stored sp-like
// values into raw pixels for AbsoluteSizeSpan, which - unlike TextView.setTextSize(SP, ...) -
// has no built-in sp unit, only px/dp. Using it (not plain density) means these spans still
// respect the user's system font-scale accessibility setting like every other sp value in
// the app, instead of silently ignoring it.
fun NoteLine.toSpannable(scaledDensity: Float): SpannableStringBuilder {
    val builder = SpannableStringBuilder(content)
    val flags = Spanned.SPAN_EXCLUSIVE_INCLUSIVE

    // Deprecated line-level size (see NoteLine.fontSize) - apply as a full-range span so old
    // notes saved during that window still render correctly, without needing a data migration.
    fontSize?.let { size ->
        if (content.isNotEmpty()) {
            builder.setSpan(AbsoluteSizeSpan((size * scaledDensity).toInt()), 0, content.length, flags)
        }
    }

    spanStyles.forEach { span ->
        if (span.start < 0 || span.end > content.length || span.start >= span.end) return@forEach
        if ((span.fontWeight ?: 0) >= FontWeightBold) {
            builder.setSpan(StyleSpan(Typeface.BOLD), span.start, span.end, flags)
        }
        if (span.fontStyle == "italic") {
            builder.setSpan(StyleSpan(Typeface.ITALIC), span.start, span.end, flags)
        }
        if (span.textDecoration == "underline") {
            builder.setSpan(UnderlineSpan(), span.start, span.end, flags)
        } else if (span.textDecoration == "linethrough") {
            builder.setSpan(StrikethroughSpan(), span.start, span.end, flags)
        }
        span.color?.let { color ->
            // Stored as Compose's Color.value ULong (legacy from the AnnotatedString era) -
            // round-trip through Color to get the plain ARGB int ForegroundColorSpan wants.
            builder.setSpan(ForegroundColorSpan(Color(color).toArgb()), span.start, span.end, flags)
        }
        span.fontSize?.let { size ->
            builder.setSpan(AbsoluteSizeSpan((size * scaledDensity).toInt()), span.start, span.end, flags)
        }
    }
    return builder
}

// The reverse of toSpannable(): reads whatever spans are currently on an Editable (after the
// user has typed/formatted through the native widget) back into our serializable model for
// storage/sync. Overlapping spans of the same kind are intentionally NOT merged here - the
// caller (handleTextChange/applyFormattingToSelection) already keeps the list well-formed.
fun Editable.toSerializableSpanStyles(): List<SerializableSpanStyle> {
    val result = mutableListOf<SerializableSpanStyle>()
    getSpans(0, length, Any::class.java).forEach { span ->
        val start = getSpanStart(span)
        val end = getSpanEnd(span)
        if (start < 0 || end <= start) return@forEach
        when (span) {
            is StyleSpan -> when (span.style) {
                Typeface.BOLD -> result.add(SerializableSpanStyle(start, end, fontWeight = FontWeightBold))
                Typeface.ITALIC -> result.add(SerializableSpanStyle(start, end, fontStyle = "italic"))
                Typeface.BOLD_ITALIC -> {
                    result.add(SerializableSpanStyle(start, end, fontWeight = FontWeightBold))
                    result.add(SerializableSpanStyle(start, end, fontStyle = "italic"))
                }
            }
            is UnderlineSpan -> result.add(SerializableSpanStyle(start, end, textDecoration = "underline"))
            is StrikethroughSpan -> result.add(SerializableSpanStyle(start, end, textDecoration = "linethrough"))
            is ForegroundColorSpan -> result.add(SerializableSpanStyle(start, end, color = Color(span.foregroundColor).value))
            is AbsoluteSizeSpan -> result.add(SerializableSpanStyle(start, end, fontSize = span.size.toFloat()))
        }
    }
    return result.sortedBy { it.start }
}

fun List<NoteLine>.toJson(): String {
    return Json.encodeToString(this)
}

// Read-only rendering (e.g. a completed checklist item's static Text()) has none of the
// cursor-placement concerns that pushed editable fields onto native Spannable - AnnotatedString
// is fine here, it's a plain draw-once conversion with no cursor involved.
fun NoteLine.toAnnotatedString(): androidx.compose.ui.text.AnnotatedString {
    return try {
        if (content.isEmpty()) {
            androidx.compose.ui.text.AnnotatedString("")
        } else if (spanStyles.isEmpty() && fontSize == null) {
            androidx.compose.ui.text.AnnotatedString(content)
        } else {
            androidx.compose.ui.text.buildAnnotatedString {
                append(content)
                fontSize?.let { size ->
                    addStyle(androidx.compose.ui.text.SpanStyle(fontSize = size.sp), 0, content.length)
                }
                spanStyles.filter { it.start >= 0 && it.end <= content.length && it.start < it.end }
                    .forEach { span ->
                        try {
                            addStyle(
                                androidx.compose.ui.text.SpanStyle(
                                    fontWeight = span.fontWeight?.let { androidx.compose.ui.text.font.FontWeight(it) },
                                    fontStyle = when (span.fontStyle) {
                                        "italic" -> androidx.compose.ui.text.font.FontStyle.Italic
                                        else -> null
                                    },
                                    textDecoration = when (span.textDecoration) {
                                        "underline" -> androidx.compose.ui.text.style.TextDecoration.Underline
                                        "linethrough" -> androidx.compose.ui.text.style.TextDecoration.LineThrough
                                        else -> null
                                    },
                                    color = span.color?.let { Color(it) } ?: Color.Unspecified,
                                    fontSize = span.fontSize?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified
                                ),
                                span.start, span.end
                            )
                        } catch (e: Exception) {
                            // Skip invalid spans
                        }
                    }
            }
        }
    } catch (e: Exception) {
        androidx.compose.ui.text.AnnotatedString(content)
    }
}

// Older versions of the app (and imports) stored every paragraph as its own TEXT NoteLine,
// which rendered as a wall of separate "Type something..." boxes. The editor treats
// consecutive plain paragraphs as one flowing text block, so we coalesce them back into a
// single NoteLine (adjusting span offsets for the joining "\n") wherever note content is read.
// Native Spannable handles mixed formatting - including mixed sizes - within that one block
// correctly, so unlike the Compose-BasicTextField era there's no size-based reason to keep
// any lines apart; they're free to always flow together.
fun List<NoteLine>.mergeConsecutiveTextLines(): List<NoteLine> {
    val result = mutableListOf<NoteLine>()
    var i = 0
    while (i < this.size) {
        val line = this[i]
        if (line.type == LineType.TEXT) {
            var mergedContent = line.content
            val mergedSpans = line.spanStyles.toMutableList()
            var j = i + 1
            while (j < this.size && this[j].type == LineType.TEXT) {
                val offset = mergedContent.length + 1
                mergedSpans.addAll(this[j].spanStyles.map { it.copy(start = it.start + offset, end = it.end + offset) })
                mergedContent += "\n" + this[j].content
                j++
            }
            result.add(line.copy(content = mergedContent, spanStyles = mergedSpans))
            i = j
        } else {
            result.add(line)
            i++
        }
    }
    return result
}

// Patches a Compose-observed list in place instead of clear()+addAll().
// clear()+addAll() briefly empties the list and then rebuilds it entirely,
// forcing every visible item to recompose/relayout even when only one line
// actually changed - very noticeable on collaborative to-do lists, where a
// real-time Firestore update (including the echo of the user's own edits)
// can fire on every keystroke/checkbox toggle. Setting only the indices that
// differ (NoteLine is a data class, so `!=` is a real content comparison)
// lets Compose skip recomposition for every unchanged item.
fun MutableList<NoteLine>.replaceAllSmart(newItems: List<NoteLine>) {
    for (index in newItems.indices) {
        if (index < size) {
            if (this[index] != newItems[index]) this[index] = newItems[index]
        } else {
            add(newItems[index])
        }
    }
    while (size > newItems.size) removeAt(size - 1)
}

// Lenient decoder: skips fields this app version doesn't know about, so notes
// written by a NEWER app version (with extra NoteLine fields) still open here
// instead of falling into the plain-text fallback below.
private val noteLineJson = Json { ignoreUnknownKeys = true }

fun String.toNoteLines(): List<NoteLine> {
    if (this.isBlank()) return emptyList()

    return try {
        noteLineJson.decodeFromString<List<NoteLine>>(this).mergeConsecutiveTextLines()
    } catch (e: Exception) {
        // Plain/legacy text (not our JSON format): keep it as a single flowing
        // block instead of splitting into one NoteLine per line.
        listOf(NoteLine(content = this, type = LineType.TEXT))
    }
}

fun List<NoteLine>.toPlainTextPreview(maxLength: Int = 100): String {
    val fullText = this.filter { it.type != LineType.SEPARATOR && it.type != LineType.IMAGE && it.type != LineType.DIVIDER }
        .joinToString(" ") { line ->
            when (line.type) {
                LineType.CHECKLIST -> if (line.isChecked) "☑ ${line.content}" else "☐ ${line.content}"
                LineType.BULLET -> "• ${line.content}"
                LineType.QUOTE -> "❝ ${line.content}"
                else -> line.content
            }
        }
    return if (fullText.length > maxLength) {
        fullText.take(maxLength) + "..."
    } else {
        fullText
    }
}

fun List<NoteLine>.getFirstLinePreview(maxLength: Int = 50): String {
    val firstContentLine = this.firstOrNull {
        it.type != LineType.SEPARATOR &&
                it.type != LineType.IMAGE &&
                it.type != LineType.DIVIDER &&
                it.content.isNotBlank()
    }
    val content = firstContentLine?.content ?: ""
    return if (content.length > maxLength) {
        content.take(maxLength) + "..."
    } else {
        content
    }
}

fun List<NoteLine>.getLineTypeCount(type: LineType): Int {
    return this.count { it.type == type }
}

fun List<NoteLine>.getCheckedItemsCount(): Int {
    return this.count { it.type == LineType.CHECKLIST && it.isChecked }
}

fun List<NoteLine>.getTotalChecklistItems(): Int {
    return this.count { it.type == LineType.CHECKLIST }
}

fun List<NoteLine>.getChecklistCompletionPercentage(): Float {
    val total = getTotalChecklistItems()
    return if (total > 0) {
        (getCheckedItemsCount().toFloat() / total.toFloat()) * 100f
    } else {
        0f
    }
}
