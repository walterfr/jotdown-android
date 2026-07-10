package br.com.jotdown.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.*
import br.com.jotdown.JotdownApplication
import br.com.jotdown.data.dao.DocumentSummary
import br.com.jotdown.data.entity.DocumentEntity
import br.com.jotdown.data.repository.DocumentRepository
import br.com.jotdown.data.entity.FolderEntity
import br.com.jotdown.data.sync.CloudFileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.flatMap
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** UI model for a PDF that lives in the user's configured Drive folder. */
data class DriveDocumentUiItem(
    val driveFileId: String,
    val name: String,
    val sizeBytes: Long,
    val isFolder: Boolean = false,
    val mimeType: String = "",
    /** Non-null when this file has already been downloaded and imported locally. */
    val localDocId: String? = null,
    /** 0-100 while downloading, null when idle. */
    val downloadProgress: Int? = null
)

class LibraryViewModel(private val repository: DocumentRepository, private val application: JotdownApplication) : ViewModel() {
    val syncWorkInfo = application.syncManager.getSyncWorkInfo()

    private val _currentFolder = MutableStateFlow<FolderEntity?>(null)
    val currentFolder: StateFlow<FolderEntity?> = _currentFolder.asStateFlow()

    private val _currentFilter = MutableStateFlow("Tudo")
    val currentFilter: StateFlow<String> = _currentFilter.asStateFlow()

    private val _currentTab = MutableStateFlow("Tudo")
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow("Recentes") // "Recentes", "A-Z", "Z-A", "Fichamentos", "Favoritos"
    val sortOrder: StateFlow<String> = _sortOrder.asStateFlow()

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setSortOrder(order: String) { _sortOrder.value = order }

    val folders: StateFlow<List<FolderEntity>> = repository.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableTags: StateFlow<List<String>> = repository.getAllDocumentSummaries()
        .map { docs -> docs.flatMap { it.labels.split(",").map { t -> t.trim() }.filter { t -> t.isNotEmpty() } }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val displayDocuments: StateFlow<List<DocumentSummary>> = combine(
        _currentFilter, _currentFolder, _currentTab, _searchQuery, _sortOrder
    ) { filter, folder, tab, query, sort ->
        Tuple5(filter, folder, tab, query, sort)
    }.flatMapLatest { (filter, folder, tab, query, sort) ->
        val rawDocs = when {
            filter == "Recentes" -> repository.getAllDocumentSummaries()
            filter == "Favoritos" -> repository.getFavoriteDocumentSummaries()
            filter == "Lixo" -> repository.getTrashedDocumentSummaries()
            filter.startsWith("Tag:") -> {
                val tag = filter.removePrefix("Tag:")
                repository.getAllDocumentSummaries().map { list -> list.filter { it.labels.split(",").map { l -> l.trim() }.contains(tag) } }
            }
            else -> repository.getDocumentSummariesByFolder(folder?.id)
        }
        
        rawDocs.map { list ->
            // Filtro por Tab
            var filtered = when (tab) {
                "PDF" -> list.filter { it.docType != "nota" } 
                "Nota" -> list.filter { it.docType == "nota" || it.annotationCount > 0 || it.highlightCount > 0 }
                "Pasta" -> emptyList()
                "Drive"  -> emptyList() // Drive tab uses its own driveDocuments flow
                else -> list
            }
            
            // Filtro por Busca
            if (query.isNotBlank()) {
                filtered = filtered.filter { 
                    it.title.contains(query, ignoreCase = true) ||
                    it.fileName.contains(query, ignoreCase = true) ||
                    it.authorLastName.contains(query, ignoreCase = true) ||
                    it.labels.contains(query, ignoreCase = true)
                }
            }
            
            // Ordenação
            when (sort) {
                "A-Z" -> filtered.sortedBy { it.title.ifBlank { it.fileName }.lowercase() }
                "Z-A" -> filtered.sortedByDescending { it.title.ifBlank { it.fileName }.lowercase() }
                "Fichamentos" -> filtered.sortedByDescending { it.highlightCount + it.annotationCount }
                "Favoritos" -> filtered.sortedWith(compareByDescending<DocumentSummary> { it.isFavorite }.thenByDescending { it.dateAdded })
                else -> filtered.sortedByDescending { it.dateAdded } // "Recentes"
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

    fun enterFolder(folder: FolderEntity?) { _currentFolder.value = folder }
    fun setFilter(filter: String) { _currentFilter.value = filter; if (filter != "Tudo") _currentFolder.value = null }
    fun setTab(tab: String) { _currentTab.value = tab }

    fun renameFolder(id: Long, newName: String) = viewModelScope.launch { repository.renameFolder(id, newName) }
    fun renameDocument(id: String, newTitle: String) = viewModelScope.launch { repository.renameDocument(id, newTitle) }
    fun updateLabels(id: String, labels: String) = viewModelScope.launch { repository.updateDocumentLabels(id, labels) }

    fun renameTagGlobally(oldTag: String, newTag: String) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            try {
                val allDocs = repository.getAllDocumentSummaries().first()
                allDocs.forEach { doc ->
                    if (doc.labels.contains(oldTag, ignoreCase = true)) {
                        val tags = doc.labels.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val newTagsList = tags.map { if (it.equals(oldTag, ignoreCase = true)) newTag else it }
                        val finalTags = newTagsList.joinToString(", ")
                        repository.updateDocumentLabels(doc.id, finalTags)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private var isMerging = false

    fun mergeIntoFolder(doc1Id: String, doc2Id: String) = viewModelScope.launch {
        if (isMerging) return@launch
        isMerging = true
        try {
            val folderId = repository.insertFolder(FolderEntity(name = "Nova Pasta"))
            repository.setDocumentFolder(doc1Id, folderId)
            repository.setDocumentFolder(doc2Id, folderId)
        } finally {
            isMerging = false
        }
    }

    fun moveToFolder(docId: String, folderId: Long) = viewModelScope.launch { repository.setDocumentFolder(docId, folderId) }
    fun removeFromFolder(docId: String) = viewModelScope.launch { repository.setDocumentFolder(docId, null) }

    fun deleteCurrentFolder() = viewModelScope.launch {
        val folder = _currentFolder.value ?: return@launch
        repository.clearFolder(folder.id)
        repository.deleteFolder(folder.id)
        _currentFolder.value = null
    }

    fun toggleFavorite(id: String, isFavorite: Boolean) = viewModelScope.launch { repository.updateFavoriteStatus(id, isFavorite) }
    fun moveToTrash(id: String) = viewModelScope.launch { repository.updateTrashStatus(id, true); repository.setDocumentFolder(id, null) }
    fun restoreFromTrash(id: String) = viewModelScope.launch { repository.updateTrashStatus(id, false) }

    // ── Drive Library ────────────────────────────────────────────────────────

    private val _driveDocuments = MutableStateFlow<List<DriveDocumentUiItem>>(emptyList())
    val driveDocuments: StateFlow<List<DriveDocumentUiItem>> = _driveDocuments.asStateFlow()

    private val _driveLoading = MutableStateFlow(false)
    val driveLoading: StateFlow<Boolean> = _driveLoading.asStateFlow()

    private val _driveError = MutableStateFlow<String?>(null)
    val driveError: StateFlow<String?> = _driveError.asStateFlow()

    private val _drivePath = MutableStateFlow<List<CloudFileInfo>>(emptyList())
    val drivePath: StateFlow<List<CloudFileInfo>> = _drivePath.asStateFlow()

    fun loadDriveDocuments(context: Context) = viewModelScope.launch {
        val prefs = context.getSharedPreferences("jotdown_drive", Context.MODE_PRIVATE)
        val rootFolderId = prefs.getString("folder_id", null)
        if (rootFolderId == null) {
            _driveError.value = "Nenhuma pasta configurada. Vá em Configurações > Biblioteca do Drive."
            return@launch
        }
        val currentFolderId = _drivePath.value.lastOrNull()?.id ?: rootFolderId

        _driveLoading.value = true
        _driveError.value = null
        val result = withContext(Dispatchers.IO) {
            application.syncProvider.listCloudDocuments(context, currentFolderId)
        }
        result.fold(
            onSuccess = { files ->
                val items = withContext(Dispatchers.IO) {
                    // Uma query em lote em vez de uma por arquivo (blocos de 900 respeitam
                    // o limite de variáveis do SQLite)
                    val localByDriveId = files.map { it.id }.chunked(900)
                        .flatMap { repository.getDocumentsByDriveFileIds(it) }
                        .associateBy { it.driveFileId }
                    files.map { file ->
                        DriveDocumentUiItem(
                            driveFileId = file.id,
                            name = file.name,
                            sizeBytes = file.sizeBytes,
                            isFolder = file.isFolder,
                            mimeType = file.mimeType,
                            localDocId = localByDriveId[file.id]?.id
                        )
                    }
                }
                _driveDocuments.value = items
            },
            onFailure = { e ->
                _driveError.value = "Erro ao listar arquivos: ${e.message}"
            }
        )
        _driveLoading.value = false
    }

    fun navigateIntoDriveFolder(context: Context, folderId: String, folderName: String) {
        val newPath = _drivePath.value.toMutableList()
        newPath.add(CloudFileInfo(id = folderId, name = folderName, sizeBytes = 0L, isFolder = true))
        _drivePath.value = newPath
        loadDriveDocuments(context)
    }

    fun navigateDriveUp(context: Context) {
        if (_drivePath.value.isNotEmpty()) {
            val newPath = _drivePath.value.toMutableList()
            // removeAt em vez de removeLast(): com compileSdk 35, removeLast() resolve para
            // java.util.List.removeLast (API 35+) e crasha com NoSuchMethodError em Android <15
            newPath.removeAt(newPath.lastIndex)
            _drivePath.value = newPath
            loadDriveDocuments(context)
        }
    }

    /**
     * Downloads a Drive PDF to local storage, generates a cover thumbnail, saves it in Room,
     * and calls [onComplete] with the new local document ID upon success.
     */
    fun importFromDrive(
        context: Context,
        item: DriveDocumentUiItem,
        onComplete: (docId: String) -> Unit
    ) = viewModelScope.launch {
        updateDriveProgress(item.driveFileId, 0)
        val result = withContext(Dispatchers.IO) {
            try {
                val docId = UUID.randomUUID().toString()
                
                val ext = when {
                    item.name.endsWith(".txt", ignoreCase = true) -> "txt"
                    item.name.endsWith(".md", ignoreCase = true) -> "md"
                    item.name.endsWith(".pdf", ignoreCase = true) -> "pdf"
                    // Sem extensão no nome: decide pelo mimeType reportado pelo Drive,
                    // senão um text/plain sem extensão seria salvo e aberto como PDF
                    item.mimeType == "text/plain" -> "txt"
                    item.mimeType == "text/markdown" || item.mimeType == "text/x-markdown" -> "md"
                    else -> "pdf"
                }
                
                val destDir = File(context.filesDir, if (ext == "pdf") "pdfs" else "docs").also { it.mkdirs() }
                val destFile = File(destDir, "$docId.$ext")
                
                application.syncProvider.downloadDriveFile(
                    context = context,
                    fileId = item.driveFileId,
                    destFile = destFile,
                    onProgress = { progress -> updateDriveProgress(item.driveFileId, progress) }
                ).getOrThrow()

                if (ext == "pdf") {
                    val coverDir = File(context.filesDir, "covers").also { it.mkdirs() }
                    br.com.jotdown.util.PdfCoverUtil.generateCover(destFile, File(coverDir, "$docId.jpg"))
                }

                val cleanTitle = item.name.removeSuffix(".$ext").removeSuffix(".${ext.uppercase()}")
                repository.saveDocument(
                    DocumentEntity(
                        id = docId,
                        fileName = item.name,
                        title = cleanTitle,
                        dateAdded = System.currentTimeMillis(),
                        pdfFilePath = destFile.absolutePath,
                        driveFileId = item.driveFileId
                    )
                )
                Result.success(docId)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        result.fold(
            onSuccess = { docId ->
                _driveDocuments.value = _driveDocuments.value.map {
                    if (it.driveFileId == item.driveFileId) it.copy(localDocId = docId, downloadProgress = null) else it
                }
                onComplete(docId)
            },
            onFailure = { e ->
                _driveError.value = "Erro ao baixar: ${e.message}"
                updateDriveProgress(item.driveFileId, null)
            }
        )
    }

    private fun updateDriveProgress(driveFileId: String, progress: Int?) {
        _driveDocuments.value = _driveDocuments.value.map {
            if (it.driveFileId == driveFileId) it.copy(downloadProgress = progress) else it
        }
    }

    // ── Local Import ─────────────────────────────────────────────────────────

    fun importPdf(context: Context, uri: Uri) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            try {
                var fileName = "documento.pdf"
                if (uri.scheme == "content") {
                    context.contentResolver.query(uri, null, null, null, null)?.use { if (it.moveToFirst()) { val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (idx >= 0) fileName = it.getString(idx) } }
                }
                val docId = UUID.randomUUID().toString()
                val pdfDir = File(context.filesDir, "pdfs").also { it.mkdirs() }
                val pdfFile = File(pdfDir, "$docId.pdf")
                context.contentResolver.openInputStream(uri)?.use { input -> pdfFile.outputStream().use { output -> input.copyTo(output) } }

                if (pdfFile.exists() && pdfFile.length() > 0L) {
                    val coverDir = File(context.filesDir, "covers").also { it.mkdirs() }
                    val coverFile = File(coverDir, "$docId.jpg")
                    br.com.jotdown.util.PdfCoverUtil.generateCover(pdfFile, coverFile)
                    var cleanTitle = fileName.removeSuffix(".pdf").removeSuffix(".PDF")
                    var extractedAuthor = ""; var extractedYear = ""
                    val parts = cleanTitle.split(Regex(" - |_")).map { it.trim() }.filter { it.isNotEmpty() }
                    if (parts.size >= 2 && !parts[0].any { it.isDigit() }) {
                        extractedAuthor = parts[0]
                        if (parts[1].matches(Regex("\\d{4}"))) { extractedYear = parts[1]; if (parts.size > 2) cleanTitle = parts.drop(2).joinToString(" - ") } else { cleanTitle = parts.drop(1).joinToString(" - ") }
                    }
                    repository.saveDocument(DocumentEntity(id = docId, fileName = fileName, title = cleanTitle, dateAdded = System.currentTimeMillis(), folderId = _currentFolder.value?.id, pdfFilePath = pdfFile.absolutePath, authorLastName = extractedAuthor, year = extractedYear))
                }
            } catch (e: Exception) { Log.e("Jotdown", "Erro: ${e.message}") }
        }
    }

    // 📝 NOVO: Cria um PDF com pautas (estilo caderno) e suporte a templates
    fun createBlankNote(context: Context, title: String, template: String = "Pautado") = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            try {
                val docId = UUID.randomUUID().toString()
                val pdfDir = File(context.filesDir, "pdfs").also { it.mkdirs() }
                val pdfFile = File(pdfDir, "$docId.pdf")

                val pdfDocument = android.graphics.pdf.PdfDocument()
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // 1. Fundo da folha
                val bgColor = if (template == "Fichamento" || template == "Branco") "#FFFFFF" else "#FAFAFA"
                canvas.drawColor(android.graphics.Color.parseColor(bgColor))

                val paint = android.graphics.Paint()

                when (template) {
                    "Pautado" -> {
                        paint.color = android.graphics.Color.parseColor("#D1D5DB")
                        paint.strokeWidth = 1f
                        var y = 80f
                        while (y < 842f) { canvas.drawLine(0f, y, 595f, y, paint); y += 30f }
                        paint.color = android.graphics.Color.parseColor("#FCA5A5")
                        paint.strokeWidth = 2f
                        canvas.drawLine(80f, 0f, 80f, 842f, paint)
                    }
                    "Quadriculado" -> {
                        paint.color = android.graphics.Color.parseColor("#E5E7EB")
                        paint.strokeWidth = 1f
                        var step = 20f
                        while (step < 842f) { canvas.drawLine(0f, step, 595f, step, paint); step += 20f }
                        step = 20f
                        while (step < 595f) { canvas.drawLine(step, 0f, step, 842f, paint); step += 20f }
                    }
                    "Fichamento" -> {
                        paint.color = android.graphics.Color.parseColor("#374151")
                        paint.textSize = 14f
                        paint.isAntiAlias = true
                        canvas.drawText("FICHAMENTO DE LEITURA", 210f, 60f, paint)
                        paint.strokeWidth = 2f
                        canvas.drawLine(50f, 80f, 545f, 80f, paint)
                        
                        paint.textSize = 10f
                        paint.color = android.graphics.Color.parseColor("#9CA3AF")
                        canvas.drawText("Referência ABNT:", 50f, 110f, paint)
                        paint.strokeWidth = 1f
                        canvas.drawLine(50f, 130f, 545f, 130f, paint)
                        
                        canvas.drawText("Ideias Centrais:", 50f, 160f, paint)
                        canvas.drawLine(50f, 180f, 545f, 180f, paint)
                        canvas.drawLine(50f, 210f, 545f, 210f, paint)
                        canvas.drawLine(50f, 240f, 545f, 240f, paint)
                        
                        canvas.drawText("Citações Importantes:", 50f, 290f, paint)
                        canvas.drawLine(50f, 310f, 545f, 310f, paint)
                    }
                    // "Branco" não desenha linhas adicionais
                }

                pdfDocument.finishPage(page)
                
                pdfFile.outputStream().use { out -> pdfDocument.writeTo(out) }
                pdfDocument.close()

                val coverDir = File(context.filesDir, "covers").also { it.mkdirs() }
                val coverFile = File(coverDir, "$docId.jpg")
                br.com.jotdown.util.PdfCoverUtil.generateCover(pdfFile, coverFile)

                val doc = DocumentEntity(
                    id = docId, fileName = "$title.pdf", title = title,
                    dateAdded = System.currentTimeMillis(), folderId = _currentFolder.value?.id, 
                    pdfFilePath = pdfFile.absolutePath, docType = "nota" 
                )
                repository.saveDocument(doc)
            } catch (e: Exception) { Log.e("Jotdown", "Erro ao criar caderno: ${e.message}") }
        }
    }

    fun deleteDocument(context: Context, id: String) = viewModelScope.launch {
        val doc = repository.getDocumentById(id)
        doc?.pdfFilePath?.let { File(it).delete() }
        
        // Fallback for backups restored from different devices/ROMs where the absolute path might have changed
        val fallbackPdfFile = File(context.filesDir, "pdfs/$id.pdf")
        if (fallbackPdfFile.exists()) fallbackPdfFile.delete()
        val fallbackTxtFile = File(context.filesDir, "docs/$id.txt")
        if (fallbackTxtFile.exists()) fallbackTxtFile.delete()
        val fallbackMdFile = File(context.filesDir, "docs/$id.md")
        if (fallbackMdFile.exists()) fallbackMdFile.delete()

        val coverFile = File(context.filesDir, "covers/$id.jpg")
        if (coverFile.exists()) coverFile.delete()
        repository.deleteDocument(id)
    }

    fun exportBackup(context: Context) = viewModelScope.launch { br.com.jotdown.util.BackupUtil.exportBackup(context) }
    fun importBackup(context: Context, uri: Uri) = viewModelScope.launch { br.com.jotdown.util.BackupUtil.importBackup(context, uri) }
    
    suspend fun getBackupFile(context: Context): File? = br.com.jotdown.util.BackupUtil.getBackupFileForSaving(context)
    fun saveBackupToUri(context: Context, sourceFile: File, uri: Uri) = viewModelScope.launch { 
        br.com.jotdown.util.BackupUtil.saveBackupToUri(context, sourceFile, uri) 
    }
}

class LibraryViewModelFactory(
    private val repository: DocumentRepository,
    private val application: br.com.jotdown.JotdownApplication
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LibraryViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
