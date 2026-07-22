package sk.kubisdev.mynotes.data.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

// Detects the source format from the picked file's name and delegates to the matching importer.
object NoteImportManager {

    fun importFromUri(context: Context, uri: Uri): List<ImportedNote> {
        val fileName = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: ""

        return when {
            fileName.endsWith(".enex", ignoreCase = true) ->
                EvernoteEnexImporter.parse(context, uri)

            fileName.endsWith(".zip", ignoreCase = true) ->
                GoogleKeepImporter.parseZip(context, uri)

            fileName.endsWith(".json", ignoreCase = true) ->
                GoogleKeepImporter.parseSingleJson(context, uri)

            fileName.endsWith(".md", ignoreCase = true) || fileName.endsWith(".txt", ignoreCase = true) ->
                listOf(PlainTextImporter.parse(context, uri, fileName))

            else -> throw NoteImportException("Unsupported file type: $fileName")
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme != "content") return uri.lastPathSegment
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index) else null
                    } else null
                }
        } catch (e: Exception) {
            null
        }
    }
}
