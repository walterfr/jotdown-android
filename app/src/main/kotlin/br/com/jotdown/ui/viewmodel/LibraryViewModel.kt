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
import kotlinx.coroutines.flow.flatMap
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class LibraryViewModel(private val repository: DocumentRepository) : ViewModel() {
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
    fun moveToTrash(id: String) = viewModelScope.launch { repository.updateTrashStatus(id, true); repository.setDocumentFolder(id, null) }
    fun restoreFromTrash(id: String) = viewModelScope.launch { repository.updateTrashStatus(id, false) }

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
        val coverFile = File(context.filesDir, "covers/$id.jpg")
        if (coverFile.exists()) coverFile.delete()
        repository.deleteDocument(id)
    }

    fun exportBackup(context: Context) = viewModelScope.launch { br.com.jotdown.util.BackupUtil.exportBackup(context) }
    fun importBackup(context: Context, uri: Uri) = viewModelScope.launch { br.com.jotdown.util.BackupUtil.importBackup(context, uri) }
}

class LibraryViewModelFactory(private val repository: DocumentRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T { @Suppress("UNCHECKED_CAST") return LibraryViewModel(repository) as T }
}