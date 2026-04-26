package br.com.jotdown.data.dao

import androidx.room.*
import br.com.jotdown.data.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
@JvmSuppressWildcards
abstract class DocumentDao {
    @Query("SELECT * FROM documents ORDER BY dateAdded DESC")
    abstract fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("UPDATE documents SET folderId = :folderId WHERE id = :docId")
    abstract suspend fun setDocumentFolder(docId: String, folderId: Long?): Int

    @Query("UPDATE documents SET folderId = NULL WHERE folderId = :folderId")
    abstract suspend fun clearFolder(folderId: Long): Int

    @Query("SELECT * FROM documents WHERE id = :id")
    abstract suspend fun getDocumentById(id: String): DocumentEntity?

    @Upsert
    abstract suspend fun upsertDocument(document: DocumentEntity): Long

    @Query("DELETE FROM documents WHERE id = :id")
    abstract suspend fun deleteDocument(id: String): Int

    @Query("UPDATE documents SET title = :newTitle WHERE id = :id")
    abstract suspend fun renameDocument(id: String, newTitle: String): Int

    @Query("UPDATE documents SET isFavorite = :isFavorite WHERE id = :id")
    abstract suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean): Int

    @Query("UPDATE documents SET isTrashed = :isTrashed WHERE id = :id")
    abstract suspend fun updateTrashStatus(id: String, isTrashed: Boolean): Int

    @Query("SELECT id, fileName, title, dateAdded, docType, authorLastName, authorFirstName, isFavorite, isTrashed, labels, (SELECT COUNT(*) FROM highlights h WHERE h.documentId = d.id) AS highlightCount, (SELECT COUNT(*) FROM annotations a WHERE a.documentId = d.id AND a.text != '') AS annotationCount FROM documents d WHERE isTrashed = 0 ORDER BY d.dateAdded DESC")
    abstract fun getAllDocumentSummaries(): Flow<List<DocumentSummary>>

    @Query("SELECT id, fileName, title, dateAdded, docType, authorLastName, authorFirstName, isFavorite, isTrashed, labels, (SELECT COUNT(*) FROM highlights h WHERE h.documentId = d.id) AS highlightCount, (SELECT COUNT(*) FROM annotations a WHERE a.documentId = d.id AND a.text != '') AS annotationCount FROM documents d WHERE isTrashed = 0 AND ((:folderId IS NULL AND d.folderId IS NULL) OR (d.folderId = :folderId)) ORDER BY d.dateAdded DESC")
    abstract fun getDocumentSummariesByFolder(folderId: Long?): Flow<List<DocumentSummary>>

    @Query("SELECT id, fileName, title, dateAdded, docType, authorLastName, authorFirstName, isFavorite, isTrashed, labels, (SELECT COUNT(*) FROM highlights h WHERE h.documentId = d.id) AS highlightCount, (SELECT COUNT(*) FROM annotations a WHERE a.documentId = d.id AND a.text != '') AS annotationCount FROM documents d WHERE isTrashed = 0 AND isFavorite = 1 ORDER BY d.dateAdded DESC")
    abstract fun getFavoriteDocumentSummaries(): Flow<List<DocumentSummary>>

    @Query("SELECT id, fileName, title, dateAdded, docType, authorLastName, authorFirstName, isFavorite, isTrashed, labels, (SELECT COUNT(*) FROM highlights h WHERE h.documentId = d.id) AS highlightCount, (SELECT COUNT(*) FROM annotations a WHERE a.documentId = d.id AND a.text != '') AS annotationCount FROM documents d WHERE isTrashed = 1 ORDER BY d.dateAdded DESC")
    abstract fun getTrashedDocumentSummaries(): Flow<List<DocumentSummary>>

    @Query("UPDATE documents SET labels = :labels WHERE id = :id")
    abstract suspend fun updateDocumentLabels(id: String, labels: String): Int

}

data class DocumentSummary(
    val id: String, val fileName: String, val title: String, val dateAdded: Long,
    val docType: String, val authorLastName: String, val authorFirstName: String,
    val isFavorite: Boolean = false, val isTrashed: Boolean = false, val labels: String = "",
    val highlightCount: Int = 0, val annotationCount: Int = 0
)

