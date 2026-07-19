package sk.kubdev.mynotes.data.importer

import sk.kubdev.mynotes.NoteLine
import sk.kubdev.mynotes.data.remote.local.entities.NoteType

data class ImportedNote(
    val title: String,
    val lines: List<NoteLine>,
    val type: NoteType
)

class NoteImportException(message: String, cause: Throwable? = null) : Exception(message, cause)
