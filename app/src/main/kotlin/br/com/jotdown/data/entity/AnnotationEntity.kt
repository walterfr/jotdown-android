package br.com.jotdown.data.entity

import androidx.room.ColumnInfo
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
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: String,
    val page: Int,
    val x: Float,
    val y: Float,
    val text: String,
    val color: String = "#FDE047",
    val audioRecordingId: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val audioTimestampMs: Long = 0L
)

