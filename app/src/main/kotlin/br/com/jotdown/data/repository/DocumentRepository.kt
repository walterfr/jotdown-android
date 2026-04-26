package br.com.jotdown.data.repository
import br.com.jotdown.data.dao.*
import br.com.jotdown.data.entity.*
import kotlinx.coroutines.flow.Flow

class DocumentRepository(
    private val documentDao: DocumentDao,
    private val annotationDao: AnnotationDao,
    private val highlightDao: HighlightDao,
    private val drawingDao: DrawingDao,
    private val folderDao: FolderDao
) {
    fun getAllDocuments(): Flow<List<DocumentEntity>> = documentDao.getAllDocuments()
    fun getDocumentSummariesByFolder(folderId: Long?): Flow<List<DocumentSummary>> = documentDao.getDocumentSummariesByFolder(folderId)

    fun getAllFolders(): Flow<List<FolderEntity>> = folderDao.getAllFolders()
    suspend fun insertFolder(folder: FolderEntity): Long = folderDao.insert(folder)
    suspend fun deleteFolder(id: Long) = folderDao.deleteFolder(id)
    suspend fun clearFolder(id: Long) = documentDao.clearFolder(id)
    suspend fun renameFolder(id: Long, newName: String) = folderDao.renameFolder(id, newName)
    suspend fun setDocumentFolder(docId: String, folderId: Long?) = documentDao.setDocumentFolder(docId, folderId)

    suspend fun getDocumentById(id: String): DocumentEntity? = documentDao.getDocumentById(id)
    suspend fun saveDocument(document: DocumentEntity) = documentDao.upsertDocument(document)
    suspend fun deleteDocument(id: String) { documentDao.deleteDocument(id) }
    suspend fun renameDocument(id: String, newTitle: String) = documentDao.renameDocument(id, newTitle)

    fun getAnnotations(documentId: String): Flow<List<AnnotationEntity>> = annotationDao.getAnnotationsForDocument(documentId)
    suspend fun saveAnnotation(annotation: AnnotationEntity) = annotationDao.upsertAnnotation(annotation)
    suspend fun deleteAnnotation(id: Long) = annotationDao.deleteAnnotation(id)

    fun getHighlights(documentId: String): Flow<List<HighlightEntity>> = highlightDao.getHighlightsForDocument(documentId)
    suspend fun saveHighlight(highlight: HighlightEntity) = highlightDao.insertHighlight(highlight)
    suspend fun deleteHighlight(id: Long) = highlightDao.deleteHighlight(id)

    fun getDrawings(documentId: String): Flow<List<DrawingEntity>> = drawingDao.getDrawingsForDocument(documentId)
    suspend fun saveDrawing(drawing: DrawingEntity) = drawingDao.upsertDrawing(drawing)
    suspend fun deleteDrawingForPage(documentId: String, page: Int) = drawingDao.deleteDrawingForPage(documentId, page)

    suspend fun updateFavoriteStatus(id: String, isFav: Boolean) = documentDao.updateFavoriteStatus(id, isFav)
    suspend fun updateTrashStatus(id: String, isTrashed: Boolean) = documentDao.updateTrashStatus(id, isTrashed)
    fun getAllDocumentSummaries() = documentDao.getAllDocumentSummaries()
    fun getFavoriteDocumentSummaries() = documentDao.getFavoriteDocumentSummaries()
    fun getTrashedDocumentSummaries() = documentDao.getTrashedDocumentSummaries()

    suspend fun updateDocumentLabels(id: String, labels: String) = documentDao.updateDocumentLabels(id, labels)
}

