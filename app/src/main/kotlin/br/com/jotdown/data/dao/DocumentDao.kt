package br.com.jotdown.data.dao
import androidx.room.*
import br.com.jotdown.data.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY dateAdded DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("UPDATE documents SET folderId = :folderId WHERE id = :docId")
    suspend fun setDocumentFolder(docId: String, folderId: Long?)

    @Query("UPDATE documents SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolder(folderId: Long)

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: String): DocumentEntity?

    @Upsert
    suspend fun upsertDocument(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: String)

    @Query("UPDATE documents SET title = :newTitle WHERE id = :id")
    suspend fun renameDocument(id: String, newTitle: String)

    @Query("SELECT id, fileName, title, dateAdded, docType, authorLastName, authorFirstName, (SELECT COUNT(*) FROM highlights h WHERE h.documentId = d.id) AS highlightCount, (SELECT COUNT(*) FROM annotations a WHERE a.documentId = d.id AND a.text != '') AS annotationCount FROM documents d WHERE (:folderId IS NULL AND d.folderId IS NULL) OR (d.folderId = :folderId) ORDER BY d.dateAdded DESC")
    fun getDocumentSummariesByFolder(folderId: Long?): Flow<List<DocumentSummary>>
}

data class DocumentSummary(
    val id: String, val fileName: String, val title: String, val dateAdded: Long,
    val docType: String, val authorLastName: String, val authorFirstName: String,
    val highlightCount: Int = 0, val annotationCount: Int = 0
)
