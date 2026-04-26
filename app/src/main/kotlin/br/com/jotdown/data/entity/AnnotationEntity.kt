package br.com.jotdown.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "annotations",
    foreignKeys = [ForeignKey(
        entity = DocumentEntity::class,
        parentColumns = ["id"],
        childColumns = ["documentId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("documentId")]
)
data class AnnotationEntity(
    @PrimaryKey val id: Long,
    val documentId: String,
    val page: Int,
    val x: Float,
    val y: Float,
    val text: String,
    val color: String = "#FDE047"
)

