package sk.kubdev.mynotes

import androidx.room.TypeConverter
import sk.kubdev.mynotes.data.remote.local.entities.NoteType

class Converters {
    @TypeConverter
    fun fromNoteType(value: NoteType): String {
        return value.name
    }

    @TypeConverter
    fun toNoteType(value: String): NoteType {
        return NoteType.valueOf(value)
    }
}