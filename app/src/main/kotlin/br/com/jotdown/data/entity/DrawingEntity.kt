package br.com.jotdown.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "drawings",
    foreignKeys = [ForeignKey(
        entity = DocumentEntity::class,
        parentColumns = ["id"],
        childColumns = ["documentId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("documentId")]
)
data class DrawingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: String,
    val page: Int,
    val pathsJson: String  // serializado como JSON
)
