package br.com.jotdown.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val title: String,
    val dateAdded: Long,
    val folderId: Long? = null, // nullable foreign key to FolderEntity – caminho do arquivo no disco
    val pdfFilePath: String, // Path to stored PDF file
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
    val accessDate: String = ""
)
