package br.com.jotdown.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val labels: String = "",
    val sourceDocId: String? = null,
    val sourcePage: Int? = null
)
