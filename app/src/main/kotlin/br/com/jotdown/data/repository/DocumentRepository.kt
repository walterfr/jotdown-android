package br.com.jotdown.data.repository

import br.com.jotdown.data.dao.*
import br.com.jotdown.data.entity.*
import br.com.jotdown.data.service.MetadataService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class DocumentRepository(
    private val folderDao: FolderDao,
    private val documentDao: DocumentDao,
    private val annotationDao: AnnotationDao,
    private val drawingDao: DrawingDao,
    private val highlightDao: HighlightDao,
    private val noteDao: br.com.jotdown.data.dao.NoteDao? = null,
    private val syncManager: br.com.jotdown.data.sync.SyncManager? = null,
    private val metadataService: MetadataService = MetadataService()
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
    suspend fun linkHighlightToDocument(highlightId: Long, linkedDocId: String?) = withContext(Dispatchers.IO) { highlightDao.updateLinkedDoc(highlightId, linkedDocId); triggerSync() }

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

    fun getFolderProgress() = folderDao.getFolderProgress()

    fun getAllNotes() = noteDao?.getAllNotes() ?: kotlinx.coroutines.flow.emptyFlow()
    suspend fun getNoteById(id: String) = noteDao?.getNoteById(id)
    fun getNotesForDocument(docId: String) = noteDao?.getNotesForDocument(docId) ?: kotlinx.coroutines.flow.emptyFlow()
    suspend fun upsertNote(note: br.com.jotdown.data.entity.NoteEntity) { noteDao?.upsertNote(note); triggerSync() }
    suspend fun deleteNote(id: String) { noteDao?.deleteNote(id); triggerSync() }
    suspend fun updateNote(id: String, title: String, content: String) { noteDao?.updateNote(id, title, content, System.currentTimeMillis()); triggerSync() }

    suspend fun createNoteForDocument(
        docId: String, page: Int?, title: String = "", content: String = "",
        sourceHighlightId: Long? = null
    ): String {
        val noteId = java.util.UUID.randomUUID().toString()
        upsertNote(NoteEntity(
            id = noteId,
            title = title,
            content = content,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            labels = "",
            sourceDocId = docId,
            sourcePage = page,
            sourceHighlightId = sourceHighlightId
        ))
        return noteId
    }

    /**
     * Move o post-it para ficha: cria a ficha com o texto do post-it e remove o
     * post-it. Ordem importa — só apaga depois que a ficha existe, senão uma
     * falha no meio perde o texto do usuário.
     */
    suspend fun promoteAnnotationToNote(annotation: AnnotationEntity): String {
        val noteId = createNoteForDocument(
            docId = annotation.documentId,
            page = annotation.page,
            title = "",
            content = annotation.text
        )
        deleteAnnotation(annotation.id)
        return noteId
    }

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
    suspend fun updateReadingStatus(id: String, status: String) { documentDao.updateReadingStatus(id, status); triggerSync() }
    suspend fun saveDocument(doc: DocumentEntity) = upsertDocument(doc)
    suspend fun getDocumentByDriveFileId(driveFileId: String) = documentDao.getDocumentByDriveFileId(driveFileId)
    suspend fun getDocumentsByDriveFileIds(driveFileIds: List<String>) = documentDao.getDocumentsByDriveFileIds(driveFileIds)

    suspend fun importDOI(docId: String, doi: String) = withContext(Dispatchers.IO) {
        val metadata = metadataService.searchDOI(doi)
        if (metadata != null) {
            getDocumentById(docId)?.let {
                upsertDocument(it.copy(
                    doi = doi,
                    title = metadata.title.takeIf { t -> t.isNotBlank() } ?: it.title,
                    authorFirstName = metadata.authorFirstName.takeIf { a -> a.isNotBlank() } ?: it.authorFirstName,
                    authorLastName = metadata.authorLastName.takeIf { a -> a.isNotBlank() } ?: it.authorLastName,
                    publisher = metadata.publisher.takeIf { p -> p.isNotBlank() } ?: it.publisher,
                    year = metadata.year.takeIf { y -> y.isNotBlank() } ?: it.year,
                    journal = metadata.journal.takeIf { j -> j.isNotBlank() } ?: it.journal,
                    volume = metadata.volume.takeIf { v -> v.isNotBlank() } ?: it.volume,
                    pages = metadata.pages.takeIf { p -> p.isNotBlank() } ?: it.pages
                ))
            }
        }
        metadata
    }
}
