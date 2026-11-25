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
    private val bookmarkDao: BookmarkDao,
    private val audioRecordingDao: AudioRecordingDao? = null,
    private val syncManager: br.com.jotdown.data.sync.SyncManager? = null,
    private val metadataService: MetadataService = MetadataService(),
    private val runInTransaction: suspend (suspend () -> Unit) -> Unit = { it() }
) {
    private fun triggerSync() {
        syncManager?.triggerImmediateSync()
    }

    fun getAllDocuments(): Flow<List<DocumentEntity>> = documentDao.getAllDocuments()
    suspend fun getDocumentById(id: String): DocumentEntity? = documentDao.getDocumentById(id)
    suspend fun upsertDocument(doc: DocumentEntity) { documentDao.upsertDocument(doc); triggerSync() }
    suspend fun deleteDocument(id: String) { documentDao.deleteDocument(id); triggerSync() }

    fun getDrawingsForDocument(documentId: String): Flow<List<DrawingEntity>> = drawingDao.getDrawingsForDocument(documentId)
    suspend fun getDrawingForPage(documentId: String, page: Int): DrawingEntity? = drawingDao.getDrawingForPage(documentId, page)
    suspend fun upsertDrawing(drawing: DrawingEntity) { drawingDao.upsertDrawing(drawing); triggerSync() }

    fun getAnnotationsForDocument(docId: String) = annotationDao.getAnnotationsForDocument(docId)
    suspend fun upsertAnnotation(annot: AnnotationEntity) { annotationDao.upsertAnnotation(annot); triggerSync() }
    suspend fun deleteAnnotation(id: Long) { annotationDao.deleteAnnotation(id); triggerSync() }

    fun getHighlightsForDocument(docId: String) = highlightDao.getHighlightsForDocument(docId)
    suspend fun insertHighlight(highlight: HighlightEntity) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { highlightDao.insertHighlight(highlight); triggerSync() }
    suspend fun deleteHighlight(id: Long) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { highlightDao.deleteHighlightById(id); triggerSync() }
    suspend fun updateHighlightNote(highlightId: Long, note: String) = withContext(Dispatchers.IO) { highlightDao.updateNote(highlightId, note); triggerSync() }

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


    suspend fun updateMetadata(
        id: String, type: String, last: String, first: String, title: String,
        subtitle: String, edition: String, city: String, publisher: String,
        year: String, journal: String, volume: String, pages: String,
        url: String, accessDate: String, doi: String = "", isbn: String = ""
    ) { 
        getDocumentById(id)?.let { 
            upsertDocument(it.copy(
                docType = type, authorLastName = last, authorFirstName = first,
                title = title, subtitle = subtitle, edition = edition, city = city,
                publisher = publisher, year = year, journal = journal, volume = volume,
                pages = pages, url = url, accessDate = accessDate,
                doi = if (doi.isNotBlank()) doi else it.doi,
                isbn = if (isbn.isNotBlank()) isbn else it.isbn
            )) 
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
    suspend fun updatePagePadding(id: String, mode: String, color: String) {
        documentDao.updatePagePadding(id, mode, color)
        triggerSync()
    }
    suspend fun updateReadingTheme(id: String, theme: String) {
        documentDao.updateReadingTheme(id, theme)
        triggerSync()
    }
    suspend fun updateReadingMode(id: String, mode: String) {
        documentDao.updateReadingMode(id, mode)
        triggerSync()
    }
    suspend fun updateLockedStatus(id: String, isLocked: Boolean) {
        documentDao.updateLockedStatus(id, isLocked)
        triggerSync()
    }

    fun getAudioRecordingsForDocument(docId: String): Flow<List<AudioRecordingEntity>> =
        audioRecordingDao?.getRecordingsForDocument(docId) ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun getAudioRecordingsListForDocument(docId: String): List<AudioRecordingEntity> = withContext(Dispatchers.IO) {
        audioRecordingDao?.getRecordingsListForDocument(docId).orEmpty()
    }

    suspend fun insertAudioRecording(recording: AudioRecordingEntity): Long = withContext(Dispatchers.IO) {
        val id = audioRecordingDao?.insertRecording(recording) ?: 0L
        triggerSync()
        id
    }

    suspend fun updateAudioRecording(id: Long, durationMs: Long, title: String) = withContext(Dispatchers.IO) {
        audioRecordingDao?.updateDurationAndTitle(id, durationMs, title)
        triggerSync()
    }

    suspend fun deleteAudioRecording(id: Long) = withContext(Dispatchers.IO) {
        audioRecordingDao?.deleteRecordingById(id)
        triggerSync()
    }

    fun getBookmarksForDocument(docId: String): Flow<List<BookmarkEntity>> = bookmarkDao.getBookmarksForDocument(docId)

    suspend fun toggleBookmark(docId: String, page: Int, defaultLabel: String = ""): Boolean = withContext(Dispatchers.IO) {
        val existing = bookmarkDao.getBookmark(docId, page)
        val added = if (existing != null) {
            bookmarkDao.deleteBookmark(docId, page)
            false
        } else {
            bookmarkDao.upsertBookmark(BookmarkEntity(documentId = docId, page = page, label = defaultLabel))
            true
        }
        triggerSync()
        added
    }

    suspend fun updateBookmarkLabel(id: Long, label: String) = withContext(Dispatchers.IO) {
        bookmarkDao.updateLabel(id, label)
        triggerSync()
    }

    suspend fun deleteBookmarkById(id: Long) = withContext(Dispatchers.IO) {
        bookmarkDao.deleteBookmarkById(id)
        triggerSync()
    }

    suspend fun shiftPagesAfterInsert(docId: String, afterPage: Int) = withContext(Dispatchers.IO) {
        runInTransaction {
            annotationDao.shiftPagesAfter(docId, afterPage)
            highlightDao.shiftPagesAfter(docId, afterPage)
            drawingDao.shiftPagesAfter(docId, afterPage)
            bookmarkDao.negateShiftedPages(docId, afterPage)
            bookmarkDao.restoreNegatedPages(docId)
        }
        triggerSync()
    }
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

    suspend fun importISBN(docId: String, isbn: String) = withContext(Dispatchers.IO) {
        val metadata = metadataService.searchISBN(isbn)
        if (metadata != null) {
            getDocumentById(docId)?.let {
                upsertDocument(it.copy(
                    isbn = isbn,
                    title = metadata.title.takeIf { t -> t.isNotBlank() } ?: it.title,
                    subtitle = metadata.subtitle.takeIf { s -> s.isNotBlank() } ?: it.subtitle,
                    authorFirstName = metadata.authorFirstName.takeIf { a -> a.isNotBlank() } ?: it.authorFirstName,
                    authorLastName = metadata.authorLastName.takeIf { a -> a.isNotBlank() } ?: it.authorLastName,
                    publisher = metadata.publisher.takeIf { p -> p.isNotBlank() } ?: it.publisher,
                    year = metadata.year.takeIf { y -> y.isNotBlank() } ?: it.year,
                    pages = metadata.pages.takeIf { p -> p.isNotBlank() } ?: it.pages
                ))
            }
        }
        metadata
    }
}
