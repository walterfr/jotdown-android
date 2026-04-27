package br.com.jotdown.data.repository

import br.com.jotdown.data.dao.*
import br.com.jotdown.data.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class DocumentRepository(
    private val folderDao: FolderDao,
    private val documentDao: DocumentDao,
    private val annotationDao: AnnotationDao,
    private val drawingDao: DrawingDao,
    private val highlightDao: HighlightDao
) {
    fun getAllDocuments(): Flow<List<DocumentEntity>> = documentDao.getAllDocuments()
    suspend fun getDocumentById(id: String): DocumentEntity? = documentDao.getDocumentById(id)
    suspend fun upsertDocument(doc: DocumentEntity) = documentDao.upsertDocument(doc)
    suspend fun deleteDocument(id: String) = documentDao.deleteDocument(id)

    fun getDrawingsForDocument(documentId: String): Flow<List<DrawingEntity>> = drawingDao.getDrawingsForDocument(documentId)
    suspend fun upsertDrawing(drawing: DrawingEntity) = drawingDao.upsertDrawing(drawing)

    fun getAnnotationsForDocument(docId: String) = annotationDao.getAnnotationsForDocument(docId)
    suspend fun upsertAnnotation(annot: AnnotationEntity) = annotationDao.upsertAnnotation(annot)
    suspend fun deleteAnnotation(id: Long) = annotationDao.deleteAnnotation(id)

    fun getHighlightsForDocument(docId: String) = highlightDao.getHighlightsForDocument(docId)
    suspend fun insertHighlight(highlight: HighlightEntity) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { highlightDao.insertHighlight(highlight) }
    suspend fun deleteHighlight(id: Long) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { highlightDao.deleteHighlightById(id) }

    fun getAllFolders(): Flow<List<FolderEntity>> = folderDao.getAllFolders()
    
    suspend fun insertFolder(folder: FolderEntity): Long = withContext(Dispatchers.IO) { folderDao.upsertFolder(folder) }
    
    suspend fun renameFolder(id: Long, newName: String) = withContext(Dispatchers.IO) {
        folderDao.getFolderById(id)?.let { folderDao.upsertFolder(it.copy(name = newName)) }
    }
    
    suspend fun deleteFolder(id: Long) = withContext(Dispatchers.IO) {
        folderDao.getFolderById(id)?.let { folderDao.deleteFolder(it) }
    }

    fun getAllDocumentSummaries() = documentDao.getAllDocumentSummaries()
    fun getFavoriteDocumentSummaries() = documentDao.getFavoriteDocumentSummaries()
    fun getTrashedDocumentSummaries() = documentDao.getTrashedDocumentSummaries()
    fun getDocumentSummariesByFolder(folderId: Long?) = if (folderId == null) documentDao.getAllDocumentSummaries() else documentDao.getDocumentSummariesByFolder(folderId)

    // ÃƒÂ°Ã…Â¸Ã¢â‚¬ÂºÃ‚Â¡ÃƒÂ¯Ã‚Â¸Ã‚Â O funil 100% alargado para receber todos os campos da ABNT!
    suspend fun updateMetadata(id: String, type: String, last: String, first: String, title: String, subtitle: String, edition: String, city: String, publisher: String, year: String, journal: String, volume: String, pages: String, url: String, accessDate: String) { 
        getDocumentById(id)?.let { 
            upsertDocument(it.copy(docType = type, authorLastName = last, authorFirstName = first, title = title, subtitle = subtitle, edition = edition, city = city, publisher = publisher, year = year, journal = journal, volume = volume, pages = pages, url = url, accessDate = accessDate)) 
        } 
    }

    suspend fun renameDocument(id: String, newTitle: String) { getDocumentById(id)?.let { upsertDocument(it.copy(title = newTitle)) } }
    suspend fun updateDocumentLabels(id: String, labels: String) { getDocumentById(id)?.let { upsertDocument(it.copy(labels = labels)) } }
    suspend fun setDocumentFolder(docId: String, folderId: Long?) { getDocumentById(docId)?.let { upsertDocument(it.copy(folderId = folderId)) } }
    suspend fun clearFolder(folderId: Long) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { documentDao.clearFolder(folderId) } 
    suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean) { getDocumentById(id)?.let { upsertDocument(it.copy(isFavorite = isFavorite)) } }
    suspend fun updateTrashStatus(id: String, isTrashed: Boolean) { getDocumentById(id)?.let { upsertDocument(it.copy(isTrashed = isTrashed)) } }
    suspend fun saveDocument(doc: DocumentEntity) = upsertDocument(doc)
}