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
    private val highlightDao: HighlightDao,
    private val syncManager: br.com.jotdown.data.sync.SyncManager? = null
) {
    private fun triggerSync() {
        syncManager?.triggerImmediateSync()
    }

    fun getAllDocuments(): Flow<List<DocumentEntity>> = documentDao.getAllDocuments()
    suspend fun getDocumentById(id: String): DocumentEntity? = documentDao.getDocumentById(id)
    suspend fun upsertDocument(doc: DocumentEntity) { documentDao.upsertDocument(doc); triggerSync() }
    suspend fun deleteDocument(id: String) { documentDao.deleteDocument(id); triggerSync() }

    fun getDrawingsForDocument(documentId: String): Flow<List<DrawingEntity>> = drawingDao.getDrawingsForDocument(documentId)
    suspend fun upsertDrawing(drawing: DrawingEntity) { drawingDao.upsertDrawing(drawing); triggerSync() }

    fun getAnnotationsForDocument(docId: String) = annotationDao.getAnnotationsForDocument(docId)
    suspend fun upsertAnnotation(annot: AnnotationEntity) { annotationDao.upsertAnnotation(annot); triggerSync() }
    suspend fun deleteAnnotation(id: Long) { annotationDao.deleteAnnotation(id); triggerSync() }

    fun getHighlightsForDocument(docId: String) = highlightDao.getHighlightsForDocument(docId)
    suspend fun insertHighlight(highlight: HighlightEntity) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { highlightDao.insertHighlight(highlight); triggerSync() }
    suspend fun deleteHighlight(id: Long) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { highlightDao.deleteHighlightById(id); triggerSync() }

    fun getAllFolders(): Flow<List<FolderEntity>> = folderDao.getAllFolders()
    
    suspend fun insertFolder(folder: FolderEntity): Long = withContext(Dispatchers.IO) { val res = folderDao.upsertFolder(folder); triggerSync(); res }
    
    suspend fun renameFolder(id: Long, newName: String) = withContext(Dispatchers.IO) {
        folderDao.getFolderById(id)?.let { folderDao.upsertFolder(it.copy(name = newName)); triggerSync() }
    }
    
    suspend fun deleteFolder(id: Long) = withContext(Dispatchers.IO) {
        folderDao.getFolderById(id)?.let { folderDao.deleteFolder(it); triggerSync() }
    }

    fun getAllDocumentSummaries() = documentDao.getAllDocumentSummaries()
    fun getFavoriteDocumentSummaries() = documentDao.getFavoriteDocumentSummaries()
    fun getTrashedDocumentSummaries() = documentDao.getTrashedDocumentSummaries()
    fun getDocumentSummariesByFolder(folderId: Long?) = documentDao.getDocumentSummariesByFolder(folderId)

    suspend fun updateMetadata(id: String, type: String, last: String, first: String, title: String, subtitle: String, edition: String, city: String, publisher: String, year: String, journal: String, volume: String, pages: String, url: String, accessDate: String) { 
        getDocumentById(id)?.let { 
            upsertDocument(it.copy(docType = type, authorLastName = last, authorFirstName = first, title = title, subtitle = subtitle, edition = edition, city = city, publisher = publisher, year = year, journal = journal, volume = volume, pages = pages, url = url, accessDate = accessDate)) 
        } 
    }

    suspend fun renameDocument(id: String, newTitle: String) { getDocumentById(id)?.let { upsertDocument(it.copy(title = newTitle)) } }
    suspend fun updateDocumentLabels(id: String, labels: String) { documentDao.updateDocumentLabels(id, labels); triggerSync() }
    suspend fun setDocumentFolder(docId: String, folderId: Long?) { getDocumentById(docId)?.let { upsertDocument(it.copy(folderId = folderId)) } }
    suspend fun clearFolder(folderId: Long) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { documentDao.clearFolder(folderId); triggerSync() } 
    suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean) { documentDao.updateFavoriteStatus(id, isFavorite); triggerSync() }
    suspend fun updateTrashStatus(id: String, isTrashed: Boolean) { documentDao.updateTrashStatus(id, isTrashed); triggerSync() }
    suspend fun updateAccessDate(id: String, accessDate: String) { documentDao.updateAccessDate(id, accessDate); triggerSync() }
    suspend fun saveDocument(doc: DocumentEntity) = upsertDocument(doc)
}