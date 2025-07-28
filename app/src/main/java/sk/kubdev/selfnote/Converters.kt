package sk.kubdev.selfnote

import androidx.room.TypeConverter
import sk.kubdev.selfnote.data.remote.local.entities.NoteType

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