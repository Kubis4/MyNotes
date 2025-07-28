package sk.kubdev.selfnote

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import androidx.compose.ui.text.buildAnnotatedString

@Serializable
enum class LineType {
    TEXT, CHECKLIST, SEPARATOR, BULLET, IMAGE
}

@Serializable
data class NoteLine(
    val id: String = UUID.randomUUID().toString(),
    val content: String = "",
    val type: LineType = LineType.TEXT,
    val isChecked: Boolean = false,
    val spanStyles: List<SerializableSpanStyle> = emptyList(),
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

fun AnnotatedString.getSpanStyleAtOffset(offset: Int): SpanStyle {
    val clampedOffset = offset.coerceIn(0, length)

    val applicableStyles = spanStyles.filter {
        clampedOffset >= it.start && clampedOffset < it.end
    }

    if (applicableStyles.isEmpty()) {
        return SpanStyle()
    }

    var combinedWeight: FontWeight? = null
    var combinedStyle: FontStyle? = null
    var combinedDecoration: TextDecoration? = null
    var combinedColor: Color = Color.Unspecified
    var combinedFontSize = androidx.compose.ui.unit.TextUnit.Unspecified

    applicableStyles.forEach { styleRange ->
        val style = styleRange.item
        if (style.fontWeight != null) combinedWeight = style.fontWeight
        if (style.fontStyle != null) combinedStyle = style.fontStyle
        if (style.textDecoration != null) combinedDecoration = style.textDecoration
        if (style.color != Color.Unspecified) combinedColor = style.color
        if (style.fontSize != androidx.compose.ui.unit.TextUnit.Unspecified) combinedFontSize = style.fontSize
    }

    return SpanStyle(
        fontWeight = combinedWeight,
        fontStyle = combinedStyle,
        textDecoration = combinedDecoration,
        color = combinedColor,
        fontSize = combinedFontSize
    )
}

fun AnnotatedString.toSerializableSpanStyles(): List<SerializableSpanStyle> {
    return spanStyles.map { spanStyleRange ->
        SerializableSpanStyle(
            start = spanStyleRange.start,
            end = spanStyleRange.end,
            fontWeight = spanStyleRange.item.fontWeight?.weight,
            fontStyle = when (spanStyleRange.item.fontStyle) {
                FontStyle.Italic -> "italic"
                FontStyle.Normal -> "normal"
                else -> null
            },
            textDecoration = when (spanStyleRange.item.textDecoration) {
                TextDecoration.Underline -> "underline"
                TextDecoration.LineThrough -> "linethrough"
                else -> null
            },
            color = spanStyleRange.item.color.value.takeIf { it != Color.Unspecified.value },
            fontSize = if (spanStyleRange.item.fontSize != androidx.compose.ui.unit.TextUnit.Unspecified) {
                spanStyleRange.item.fontSize.value
            } else null
        )
    }
}

fun NoteLine.toAnnotatedString(): AnnotatedString {
    return try {
        if (content.isEmpty()) {
            AnnotatedString("")
        } else if (spanStyles.isEmpty()) {
            AnnotatedString(content)
        } else {
            buildAnnotatedString {
                append(content)

                // Only process valid spans
                val validSpans = spanStyles.filter { span ->
                    span.start >= 0 &&
                            span.end <= content.length &&
                            span.start < span.end
                }

                validSpans.forEach { serializableStyle ->
                    try {
                        val spanStyle = SpanStyle(
                            fontWeight = serializableStyle.fontWeight?.let { FontWeight(it) },
                            fontStyle = when (serializableStyle.fontStyle) {
                                "italic" -> FontStyle.Italic
                                "normal" -> FontStyle.Normal
                                else -> null
                            },
                            textDecoration = when (serializableStyle.textDecoration) {
                                "underline" -> TextDecoration.Underline
                                "linethrough" -> TextDecoration.LineThrough
                                else -> null
                            },
                            color = serializableStyle.color?.let { Color(it) } ?: Color.Unspecified,
                            fontSize = serializableStyle.fontSize?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified
                        )

                        addStyle(spanStyle, serializableStyle.start, serializableStyle.end)
                    } catch (e: Exception) {
                        // Skip invalid spans
                    }
                }
            }
        }
    } catch (e: Exception) {
        // Fallback to plain text
        AnnotatedString(content)
    }
}

fun List<NoteLine>.toJson(): String {
    return Json.encodeToString(this)
}

fun String.toNoteLines(): List<NoteLine> {
    // Quick fix for corrupted data
    if (this.startsWith("[{\"content\":") && this.contains("spanStyles")) {
        return listOf(NoteLine(content = "Error: Corrupted note data", type = LineType.TEXT))
    }

    return try {
        Json.decodeFromString<List<NoteLine>>(this)
    } catch (e: Exception) {
        if (this.startsWith("[") && this.endsWith("]")) {
            try {
                Json.decodeFromString<List<NoteLine>>(this)
            } catch (jsonException: Exception) {
                listOf(NoteLine(content = this, type = LineType.TEXT))
            }
        } else {
            this.lines().mapIndexed { _, line ->
                NoteLine(
                    content = line,
                    type = LineType.TEXT
                )
            }.takeIf { it.isNotEmpty() } ?: listOf(NoteLine(content = "", type = LineType.TEXT))
        }
    }
}

fun List<NoteLine>.toPlainTextPreview(maxLength: Int = 100): String {
    val fullText = this.filter { it.type != LineType.SEPARATOR && it.type != LineType.IMAGE }
        .joinToString(" ") { line ->
            when (line.type) {
                LineType.CHECKLIST -> if (line.isChecked) "☑ ${line.content}" else "☐ ${line.content}"
                LineType.BULLET -> "• ${line.content}"
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
