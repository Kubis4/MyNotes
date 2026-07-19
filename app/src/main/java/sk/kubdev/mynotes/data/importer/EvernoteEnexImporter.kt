package sk.kubdev.mynotes.data.importer

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import sk.kubdev.mynotes.LineType
import sk.kubdev.mynotes.NoteLine
import sk.kubdev.mynotes.data.remote.local.entities.NoteType
import java.io.StringReader

// Parses Evernote .enex exports (XML with one <note> per note, content as ENML in a CDATA block).
object EvernoteEnexImporter {

    fun parse(context: Context, uri: Uri): List<ImportedNote> {
        val notes = mutableListOf<ImportedNote>()
        val input = context.contentResolver.openInputStream(uri)
            ?: throw NoteImportException("Cannot open file")

        input.use { stream ->
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, "UTF-8")

            var inNote = false
            var title = ""
            var content = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "note" -> {
                            inNote = true
                            title = ""
                            content = ""
                        }
                        "title" -> if (inNote) title = parser.nextText()
                        "content" -> if (inNote) content = parser.nextText()
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "note" && inNote) {
                            inNote = false
                            val lines = parseEnmlContent(content).ifEmpty {
                                listOf(NoteLine(content = "", type = LineType.TEXT))
                            }
                            val type = if (lines.any { it.type == LineType.CHECKLIST }) {
                                NoteType.CHECKLIST
                            } else {
                                NoteType.TEXT
                            }
                            notes.add(
                                ImportedNote(
                                    title = title.ifBlank { "Imported note" },
                                    lines = lines,
                                    type = type
                                )
                            )
                        }
                    }
                }
                eventType = parser.next()
            }
        }

        if (notes.isEmpty()) {
            throw NoteImportException("No notes found in this .enex file")
        }
        return notes
    }

    // ENML is XHTML-ish. Turn <en-todo>/<div>/<br>/<p> boundaries into separate NoteLines.
    private fun parseEnmlContent(enml: String): List<NoteLine> {
        val lines = mutableListOf<NoteLine>()
        val currentLine = StringBuilder()
        var pendingChecklist = false
        var pendingChecked = false

        fun flushLine() {
            val text = currentLine.toString().trim()
            if (text.isNotEmpty() || pendingChecklist) {
                lines.add(
                    NoteLine(
                        content = text,
                        type = if (pendingChecklist) LineType.CHECKLIST else LineType.TEXT,
                        isChecked = pendingChecked
                    )
                )
            }
            currentLine.clear()
            pendingChecklist = false
            pendingChecked = false
        }

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(enml))

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "div", "br", "p" -> flushLine()
                        "en-todo" -> {
                            flushLine()
                            pendingChecklist = true
                            pendingChecked = parser.getAttributeValue(null, "checked") == "true"
                        }
                    }
                    XmlPullParser.TEXT -> currentLine.append(parser.text)
                }
                eventType = parser.next()
            }
            flushLine()
        } catch (e: Exception) {
            // Fall back to stripping tags if the ENML fragment is malformed
            return enml.replace(Regex("<[^>]*>"), " ")
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { NoteLine(content = it, type = LineType.TEXT) }
        }

        return lines
    }
}
