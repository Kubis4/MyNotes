package sk.kubisdev.mynotes.data.remote.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val order: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
