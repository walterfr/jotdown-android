package br.com.jotdown.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "highlights",
    foreignKeys = [ForeignKey(
        entity = DocumentEntity::class,
        parentColumns = ["id"],
        childColumns = ["documentId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("documentId")]
)
data class HighlightEntity(
    @PrimaryKey val id: Long,
    val documentId: String,
    val page: Int,
    val text: String
)
