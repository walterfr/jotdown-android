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

    @Query("SELECT id, fileName, title, dateAdded, docType, authorLastName, authorFirstName, (SELECT COUNT(*) FROM highlights h WHERE h.documentId = d.id) AS highlightCount, (SELECT COUNT(*) FROM annotations a WHERE a.documentId = d.id AND a.text != '') AS annotationCount FROM documents d WHERE (:folderId IS NULL AND d.folderId IS NULL) OR (d.folderId = :folderId) ORDER BY d.dateAdded DESC")
    abstract fun getDocumentSummariesByFolder(folderId: Long?): Flow<List<DocumentSummary>>
}

data class DocumentSummary(
    val id: String, val fileName: String, val title: String, val dateAdded: Long,
    val docType: String, val authorLastName: String, val authorFirstName: String,
    val highlightCount: Int = 0, val annotationCount: Int = 0
)
