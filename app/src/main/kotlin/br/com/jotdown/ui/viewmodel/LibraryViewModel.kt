package br.com.jotdown.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.*
import br.com.jotdown.data.dao.DocumentSummary
import br.com.jotdown.data.entity.DocumentEntity
import br.com.jotdown.data.repository.DocumentRepository
import br.com.jotdown.data.entity.FolderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class LibraryViewModel(private val repository: DocumentRepository) : ViewModel() {
    private val _currentFolder = MutableStateFlow<FolderEntity?>(null)
    val currentFolder: StateFlow<FolderEntity?> = _currentFolder.asStateFlow()

    private val _currentFilter = MutableStateFlow("Tudo")
    val currentFilter: StateFlow<String> = _currentFilter.asStateFlow()

    val folders: StateFlow<List<FolderEntity>> = repository.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 🏷️ NOVO: Extrai todas as tags únicas de todos os documentos
    val availableTags: StateFlow<List<String>> = repository.getAllDocumentSummaries()
        .map { docs ->
            docs.flatMap { it.labels.split(",").map { t -> t.trim() }.filter { t -> t.isNotEmpty() } }
                .distinct().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val displayDocuments: StateFlow<List<DocumentSummary>> = combine(_currentFilter, _currentFolder) { filter, folder ->
        Pair(filter, folder)
    }.flatMapLatest { (filter, folder) ->
        when {
            filter == "Recentes" -> repository.getAllDocumentSummaries()
            filter == "Favoritos" -> repository.getFavoriteDocumentSummaries()
            filter == "Lixo" -> repository.getTrashedDocumentSummaries()
            filter.startsWith("Tag:") -> {
                val tag = filter.removePrefix("Tag:")
                repository.getAllDocumentSummaries().map { list ->
                    list.filter { it.labels.split(",").map { l -> l.trim() }.contains(tag) }
                }
            }
            else -> repository.getDocumentSummariesByFolder(folder?.id)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun enterFolder(folder: FolderEntity?) { _currentFolder.value = folder }
    fun setFilter(filter: String) {
        _currentFilter.value = filter
        if (filter != "Tudo") _currentFolder.value = null
    }

    fun renameFolder(id: Long, newName: String) = viewModelScope.launch { repository.renameFolder(id, newName) }
    fun renameDocument(id: String, newTitle: String) = viewModelScope.launch { repository.renameDocument(id, newTitle) }
    
    // 🏷️ NOVO: Salva as etiquetas do documento
    fun updateLabels(id: String, labels: String) = viewModelScope.launch { repository.updateDocumentLabels(id, labels) }

    fun mergeIntoFolder(doc1Id: String, doc2Id: String) = viewModelScope.launch {
        val folderId = repository.insertFolder(FolderEntity(name = "Nova Pasta"))
        repository.setDocumentFolder(doc1Id, folderId)
        repository.setDocumentFolder(doc2Id, folderId)
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
    fun moveToTrash(id: String) = viewModelScope.launch {
        repository.updateTrashStatus(id, true)
        repository.setDocumentFolder(id, null)
    }
    fun restoreFromTrash(id: String) = viewModelScope.launch { repository.updateTrashStatus(id, false) }

    fun importPdf(context: Context, uri: Uri) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            try {
                var fileName = "documento.pdf"
                if (uri.scheme == "content") {
                    context.contentResolver.query(uri, null, null, null, null)?.use {
                        if (it.moveToFirst()) {
                            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (index >= 0) fileName = it.getString(index)
                        }
                    }
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
                    var extractedAuthor = ""
                    var extractedYear = ""

                    val parts = cleanTitle.split(Regex(" - |_")).map { it.trim() }.filter { it.isNotEmpty() }
                    if (parts.size >= 2 && !parts[0].any { it.isDigit() }) {
                        extractedAuthor = parts[0]
                        if (parts[1].matches(Regex("\\d{4}"))) {
                            extractedYear = parts[1]
                            if (parts.size > 2) cleanTitle = parts.drop(2).joinToString(" - ")
                        } else { cleanTitle = parts.drop(1).joinToString(" - ") }
                    }

                    repository.saveDocument(DocumentEntity(id = docId, fileName = fileName, title = cleanTitle, dateAdded = System.currentTimeMillis(), folderId = _currentFolder.value?.id, pdfFilePath = pdfFile.absolutePath, authorLastName = extractedAuthor, year = extractedYear))
                }
            } catch (e: Exception) { Log.e("Jotdown", "Erro: ${e.message}") }
        }
    }

    fun deleteDocument(context: Context, id: String) = viewModelScope.launch {
        val doc = repository.getDocumentById(id)
        doc?.pdfFilePath?.let { File(it).delete() }
        val coverFile = File(context.filesDir, "covers/$id.jpg")
        if (coverFile.exists()) coverFile.delete()
        repository.deleteDocument(id)
    }

    fun exportBackup(context: Context) = viewModelScope.launch { br.com.jotdown.util.BackupUtil.exportBackup(context) }
    fun importBackup(context: Context, uri: Uri) = viewModelScope.launch { br.com.jotdown.util.BackupUtil.importBackup(context, uri) }
}

class LibraryViewModelFactory(private val repository: DocumentRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return LibraryViewModel(repository) as T
    }
}