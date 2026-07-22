package sk.kubisdev.mynotes.data.importer

import sk.kubisdev.mynotes.NoteLine
import sk.kubisdev.mynotes.data.remote.local.entities.NoteType

data class ImportedNote(
    val title: String,
    val lines: List<NoteLine>,
    val type: NoteType
)

class NoteImportException(message: String, cause: Throwable? = null) : Exception(message, cause)
