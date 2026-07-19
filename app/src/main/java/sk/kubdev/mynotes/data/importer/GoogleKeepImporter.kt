package sk.kubdev.mynotes.data.importer

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import sk.kubdev.mynotes.LineType
import sk.kubdev.mynotes.NoteLine
import sk.kubdev.mynotes.data.remote.local.entities.NoteType
import java.util.zip.ZipInputStream

// Parses Google Keep notes exported via Google Takeout: either a .zip archive
// containing Takeout/Keep/*.json entries, or a single Keep note .json file.
object GoogleKeepImporter {

    fun parseZip(context: Context, uri: Uri): List<ImportedNote> {
        val notes = mutableListOf<ImportedNote>()
        val input = context.contentResolver.openInputStream(uri)
            ?: throw NoteImportException("Cannot open file")

        input.use { stream ->
            ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory &&
                        entry.name.contains("Keep", ignoreCase = true) &&
                        entry.name.endsWith(".json", ignoreCase = true)
                    ) {
                        try {
                            val text = zip.readBytes().toString(Charsets.UTF_8)
                            parseNoteJson(JSONObject(text))?.let { notes.add(it) }
                        } catch (e: Exception) {
                            // skip malformed/unrelated entry
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        if (notes.isEmpty()) {
            throw NoteImportException("No Google Keep notes found in this archive")
        }
        return notes
    }

    fun parseSingleJson(context: Context, uri: Uri): List<ImportedNote> {
        val text = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: throw NoteImportException("Cannot open file")

        val note = parseNoteJson(JSONObject(text))
            ?: throw NoteImportException("Not a valid Google Keep note")
        return listOf(note)
    }

    private fun parseNoteJson(json: JSONObject): ImportedNote? {
        if (json.optBoolean("isTrashed", false)) return null

        val title = json.optString("title").ifBlank { "Untitled" }
        val listContent = json.optJSONArray("listContent")

        val lines: List<NoteLine>
        val type: NoteType

        if (listContent != null && listContent.length() > 0) {
            lines = (0 until listContent.length()).map { i ->
                val item = listContent.getJSONObject(i)
                NoteLine(
                    content = item.optString("text"),
                    type = LineType.CHECKLIST,
                    isChecked = item.optBoolean("isChecked", false)
                )
            }
            type = NoteType.CHECKLIST
        } else {
            val text = json.optString("textContent")
            lines = text.split("\n").map { NoteLine(content = it, type = LineType.TEXT) }
            type = NoteType.TEXT
        }

        return ImportedNote(title = title, lines = lines, type = type)
    }
}
