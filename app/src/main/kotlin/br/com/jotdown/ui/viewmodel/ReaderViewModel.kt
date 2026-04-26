package br.com.jotdown.ui.viewmodel

import android.util.Log
import androidx.lifecycle.*
import br.com.jotdown.data.entity.*
import br.com.jotdown.data.repository.DocumentRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class ReaderViewModel(
    private val repository: DocumentRepository,
    private val documentId: String
) : ViewModel() {

    private val _document = MutableStateFlow<DocumentEntity?>(null)
    val document: StateFlow<DocumentEntity?> = _document.asStateFlow()

    val pdfFile: StateFlow<File?> = _document.map { doc ->
        doc?.pdfFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) file else null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _activeTool = MutableStateFlow(Tool.NONE)
    val activeTool: StateFlow<Tool> = _activeTool.asStateFlow()

    // 🎨 NOVO: Memória fotográfica de cores por ferramenta!
    private val toolColors = mutableMapOf(
        Tool.PEN to 0xFF000000.toInt(),
        Tool.PENCIL to 0xFF3B82F6.toInt(),
        Tool.HIGHLIGHTER to 0xFFFDE047.toInt()
    )
    private val _strokeColor = MutableStateFlow(toolColors[Tool.PEN]!!)
    val strokeColor: StateFlow<Int> = _strokeColor.asStateFlow()

    val annotations: StateFlow<List<AnnotationEntity>> = repository
        .getAnnotations(documentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val highlights: StateFlow<List<HighlightEntity>> = repository
        .getHighlights(documentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val drawings: StateFlow<List<DrawingEntity>> = repository
        .getDrawings(documentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            _document.value = repository.getDocumentById(documentId)
        }
    }

    fun setCurrentPage(page: Int) { _currentPage.value = page }
    
    fun setActiveTool(tool: Tool) {
        _activeTool.value = tool
        toolColors[tool]?.let { _strokeColor.value = it }
    }

    fun toggleTool(tool: Tool) {
        val newTool = if (_activeTool.value == tool) Tool.NONE else tool
        _activeTool.value = newTool
        toolColors[newTool]?.let { _strokeColor.value = it }
    }

    fun setStrokeColor(color: Int) {
        _strokeColor.value = color
        if (_activeTool.value == Tool.PEN || _activeTool.value == Tool.PENCIL || _activeTool.value == Tool.HIGHLIGHTER) {
            toolColors[_activeTool.value] = color
        }
    }

    fun addAnnotation(page: Int, x: Float, y: Float, text: String) {
        viewModelScope.launch { repository.saveAnnotation(AnnotationEntity(id = System.currentTimeMillis(), documentId = documentId, page = page, x = x, y = y, text = text)) }
    }
    fun updateAnnotation(id: Long, text: String) {
        viewModelScope.launch { val existing = annotations.value.find { it.id == id } ?: return@launch; repository.saveAnnotation(existing.copy(text = text)) }
    }
    fun deleteAnnotation(id: Long) { viewModelScope.launch { repository.deleteAnnotation(id) } }
    
    fun addHighlight(page: Int, text: String) {
        viewModelScope.launch { repository.saveHighlight(HighlightEntity(id = System.currentTimeMillis(), documentId = documentId, page = page, text = text)) }
    }
    fun deleteHighlight(id: Long) { viewModelScope.launch { repository.deleteHighlight(id) } }

    fun saveDrawing(page: Int, pathsJson: String) {
        viewModelScope.launch { repository.saveDrawing(DrawingEntity(documentId = documentId, page = page, pathsJson = pathsJson)) }
    }

    fun saveMetadata(docType: String, authorLastName: String, authorFirstName: String, title: String, subtitle: String, edition: String, city: String, publisher: String, year: String, journal: String, volume: String, pages: String, url: String, accessDate: String) {
        viewModelScope.launch {
            val doc = _document.value ?: return@launch
            val updated = doc.copy(docType = docType, authorLastName = authorLastName, authorFirstName = authorFirstName, title = title, subtitle = subtitle, edition = edition, city = city, publisher = publisher, year = year, journal = journal, volume = volume, pages = pages, url = url, accessDate = accessDate)
            repository.saveDocument(updated)
            _document.value = updated
        }
    }
}

enum class Tool { NONE, SELECT, PEN, PENCIL, HIGHLIGHTER, ERASER, ANNOTATION }

class ReaderViewModelFactory(private val repository: DocumentRepository, private val documentId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T { @Suppress("UNCHECKED_CAST") return ReaderViewModel(repository, documentId) as T }
}