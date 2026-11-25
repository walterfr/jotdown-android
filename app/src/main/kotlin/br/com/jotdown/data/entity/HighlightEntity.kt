package br.com.jotdown.data.entity

import androidx.room.ColumnInfo
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
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: String,
    val page: Int,
    val text: String,
    /** Fichamento da citação: o comentário do leitor sobre este trecho.
     *  Sai no export logo abaixo da citação que o originou. */
    val note: String = "",
    @ColumnInfo(defaultValue = "")
    val rectsJson: String = "",
    @ColumnInfo(defaultValue = "HIGHLIGHT")
    val style: String = "HIGHLIGHT",
    @ColumnInfo(defaultValue = "#FDE047")
    val color: String = "#FDE047"
)

