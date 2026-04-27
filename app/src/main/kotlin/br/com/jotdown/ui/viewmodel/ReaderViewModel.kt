package br.com.jotdown.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.jotdown.data.entity.*
import br.com.jotdown.data.repository.DocumentRepository
import br.com.jotdown.ui.screens.reader.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class ReaderViewModel(private val repository: DocumentRepository, private val documentId: String) : ViewModel() {
    private val _pdfFile = MutableStateFlow<File?>(null)
    val pdfFile: StateFlow<File?> = _pdfFile

    private val _document = MutableStateFlow<DocumentEntity?>(null)
    val document: StateFlow<DocumentEntity?> = _document

    private val _activeTool = MutableStateFlow(Tool.NONE)
    val activeTool: StateFlow<Tool> = _activeTool

    private val _strokeColor = MutableStateFlow(0xFF000000.toInt())
    val strokeColor: StateFlow<Int> = _strokeColor

    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage

    val annotations = repository.getAnnotationsForDocument(documentId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val highlights = repository.getHighlightsForDocument(documentId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val drawings = repository.getDrawingsForDocument(documentId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val doc = repository.getDocumentById(documentId)
            _document.value = doc
            doc?.pdfFilePath?.let { _pdfFile.value = File(it) }
        }
    }

    fun toggleTool(tool: Tool) { _activeTool.value = if (_activeTool.value == tool) Tool.NONE else tool }
    fun setActiveTool(tool: Tool) { _activeTool.value = tool }
    fun setStrokeColor(color: Int) { _strokeColor.value = color }
    fun setCurrentPage(page: Int) { _currentPage.value = page }

    private val _strokeWidthMultiplier = MutableStateFlow(1.0f)
    val strokeWidthMultiplier: StateFlow<Float> = _strokeWidthMultiplier
    fun setStrokeWidthMultiplier(v: Float) { _strokeWidthMultiplier.value = v }

    // ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂºÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¯ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â A CORREÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢O MESTRA: Atualiza o registo existente em vez de duplicar
    fun saveDrawing(page: Int, json: String) = viewModelScope.launch(Dispatchers.IO) {
        val existing = drawings.value.find { it.page == page }
        if (existing != null) {
            // Se jÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡ desenhou nesta pÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡gina antes, atualiza a mesma folha!
            repository.upsertDrawing(existing.copy(pathsJson = json))
        } else {
            // Primeira vez a desenhar nesta folha
            repository.upsertDrawing(DrawingEntity(documentId = documentId, page = page, pathsJson = json))
        }
    }

    fun addAnnotation(page: Int, x: Float, y: Float, text: String) = viewModelScope.launch(Dispatchers.IO) {
        repository.upsertAnnotation(AnnotationEntity(id = 0L, documentId = documentId, page = page, x = x, y = y, text = text))
    }

    fun updateAnnotation(id: Long, text: String) = viewModelScope.launch(Dispatchers.IO) {
        val current = annotations.value.find { it.id == id }
        current?.let { repository.upsertAnnotation(it.copy(text = text)) }
    }

    fun deleteAnnotation(id: Long) = viewModelScope.launch(Dispatchers.IO) { repository.deleteAnnotation(id) }

    fun updateHighlight(id: Long, text: String) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { highlights.value.find { it.id == id }?.let { repository.insertHighlight(it.copy(text = text)) } }
    fun deleteHighlight(id: Long) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { repository.deleteHighlight(id) }
    fun addHighlight(page: Int, text: String) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertHighlight(HighlightEntity(id = 0L, documentId = documentId, page = page, text = text))
    }

    fun saveMetadata(docType: String, authorLastName: String, authorFirstName: String, title: String, subtitle: String, edition: String, city: String, publisher: String, year: String, journal: String, volume: String, pages: String, url: String, accessDate: String) = viewModelScope.launch(Dispatchers.IO) {
        repository.updateMetadata(documentId, docType, authorLastName, authorFirstName, title, subtitle, edition, city, publisher, year, journal, volume, pages, url, accessDate)
        _document.value = repository.getDocumentById(documentId)
    }
}

class ReaderViewModelFactory(private val repository: DocumentRepository, private val documentId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReaderViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReaderViewModel(repository, documentId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}