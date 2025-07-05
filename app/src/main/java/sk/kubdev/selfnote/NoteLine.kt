package sk.kubdev.selfnote

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

enum class LineType {
    TEXT, CHECKLIST, BULLET
}

data class SerializableColor(val argb: Int) {
    // FIX: Added the missing space between 'fun' and 'toComposeColor'
    fun toComposeColor(): Color = Color(argb)
}

fun Color.toSerializableColor() = SerializableColor(this.toArgb())


data class SerializableSpanStyle(
    val start: Int,
    val end: Int,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val color: SerializableColor? = null
)

data class NoteLine(
    val id: String = UUID.randomUUID().toString(),
    val type: LineType = LineType.TEXT,
    val content: String = "",
    val isChecked: Boolean = false,
    val spanStyles: List<SerializableSpanStyle> = emptyList()
)

fun NoteLine.toAnnotatedString(): AnnotatedString {
    return buildAnnotatedString {
        append(content)
        spanStyles.forEach { style ->
            val composeStyle = SpanStyle(
                fontWeight = if (style.isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (style.isItalic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (style.isUnderline) TextDecoration.Underline else null,
                color = style.color?.toComposeColor() ?: Color.Unspecified
            )
            // Ensure the range is valid before applying the style
            if (style.start < style.end && style.end <= content.length) {
                addStyle(composeStyle, style.start, style.end)
            }
        }
    }
}

fun AnnotatedString.toSerializableSpanStyles(): List<SerializableSpanStyle> {
    return this.spanStyles.map {
        SerializableSpanStyle(
            start = it.start,
            end = it.end,
            isBold = it.item.fontWeight == FontWeight.Bold,
            isItalic = it.item.fontStyle == FontStyle.Italic,
            isUnderline = it.item.textDecoration == TextDecoration.Underline,
            color = if (it.item.color != Color.Unspecified) it.item.color.toSerializableColor() else null
        )
    }
}

fun List<NoteLine>.toJson(): String {
    return Gson().toJson(this)
}

fun String.toNoteLines(): List<NoteLine> {
    val type = object : TypeToken<List<NoteLine>>() {}.type
    return try {
        Gson().fromJson(this, type)
    } catch (e: Exception) {
        listOf(NoteLine(content = this)) // Fallback for old plain text notes
    }
}

