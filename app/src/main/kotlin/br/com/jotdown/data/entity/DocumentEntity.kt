package br.com.jotdown.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val title: String,
    val dateAdded: Long,
    val folderId: Long? = null,
    val pdfFilePath: String,
    val docType: String = "livro",
    val authorLastName: String = "",
    val authorFirstName: String = "",
    val subtitle: String = "",
    val edition: String = "",
    val city: String = "",
    val publisher: String = "",
    val year: String = "",
    val journal: String = "",
    val volume: String = "",
    val pages: String = "",
    val url: String = "",
    val accessDate: String = "",
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val labels: String = "",
    /** Google Drive file ID — null for locally-imported documents. */
    val driveFileId: String? = null,
    @ColumnInfo(defaultValue = "TO_READ")
    val readingStatus: String = ReadingStatus.TO_READ.name,
    @ColumnInfo(defaultValue = "")
    val doi: String = "",
    @ColumnInfo(defaultValue = "")
    val isbn: String = "",
    @ColumnInfo(defaultValue = "NONE")
    val pagePaddingMode: String = "NONE",
    @ColumnInfo(defaultValue = "#FFFFFF")
    val pagePaddingColor: String = "#FFFFFF",
    @ColumnInfo(defaultValue = "LIGHT")
    val readingTheme: String = "LIGHT",
    @ColumnInfo(defaultValue = "VERTICAL")
    val readingMode: String = "VERTICAL",
    @ColumnInfo(defaultValue = "0")
    val isLocked: Boolean = false
)
