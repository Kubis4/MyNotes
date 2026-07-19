package sk.kubdev.mynotes.data.importer

import android.content.Context
import android.net.Uri
import sk.kubdev.mynotes.LineType
import sk.kubdev.mynotes.NoteLine
import sk.kubdev.mynotes.data.remote.local.entities.NoteType

// Parses plain .txt files and Markdown .md files (one file = one note).
// Recognizes Markdown checkboxes ("- [ ] " / "- [x] "), bullets and "---" separators.
object PlainTextImporter {

    private val checklistRegex = Regex("^\\s*[-*]\\s+\\[([ xX])]\\s*(.*)$")
    private val bulletRegex = Regex("^\\s*[-*]\\s+(.*)$")

    fun parse(context: Context, uri: Uri, fileName: String): ImportedNote {
        val text = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: throw NoteImportException("Cannot open file")

        val title = fileName.substringBeforeLast(".").ifBlank { "Imported note" }
        val lines = mutableListOf<NoteLine>()
        var hasChecklist = false

        text.lines().forEach { rawLine ->
            val checklistMatch = checklistRegex.find(rawLine)
            when {
                checklistMatch != null -> {
                    hasChecklist = true
                    lines.add(
                        NoteLine(
                            content = checklistMatch.groupValues[2],
                            type = LineType.CHECKLIST,
                            isChecked = checklistMatch.groupValues[1].lowercase() == "x"
                        )
                    )
                }
                rawLine.trim() == "---" || rawLine.trim() == "***" ->
                    lines.add(NoteLine(type = LineType.SEPARATOR))
                bulletRegex.matches(rawLine) -> {
                    val bulletContent = bulletRegex.find(rawLine)!!.groupValues[1]
                    lines.add(NoteLine(content = bulletContent, type = LineType.BULLET))
                }
                else -> lines.add(NoteLine(content = rawLine, type = LineType.TEXT))
            }
        }

        return ImportedNote(
            title = title,
            lines = lines.ifEmpty { listOf(NoteLine(content = "", type = LineType.TEXT)) },
            type = if (hasChecklist) NoteType.CHECKLIST else NoteType.TEXT
        )
    }
}
