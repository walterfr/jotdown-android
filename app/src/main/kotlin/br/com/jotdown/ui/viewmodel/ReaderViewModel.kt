package br.com.jotdown.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.jotdown.data.entity.*
import br.com.jotdown.data.repository.DictionaryRepository
import br.com.jotdown.data.repository.DocumentRepository
import br.com.jotdown.ui.screens.reader.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

sealed class DictionaryLookupState {
    object Idle : DictionaryLookupState()
    data class Loading(val word: String) : DictionaryLookupState()
    data class Success(val entry: DictionaryCache) : DictionaryLookupState()
    data class NotFound(val word: String) : DictionaryLookupState()
    data class NotDownloaded(val language: String, val word: String) : DictionaryLookupState()
    data class Downloading(val progress: Int) : DictionaryLookupState()
    data class Error(val message: String) : DictionaryLookupState()
    object PhraseLoading : DictionaryLookupState()
    data class PhraseSuccess(val entries: List<DictionaryCache>) : DictionaryLookupState()
    data class PhraseNotFound(val phrase: String) : DictionaryLookupState()
}

class ReaderViewModel(
    private val repository: DocumentRepository,
    private val documentId: String,
    private val dictionaryRepository: DictionaryRepository
) : ViewModel() {

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

    private val _dictionaryState = MutableStateFlow<DictionaryLookupState>(DictionaryLookupState.Idle)
    val dictionaryState: StateFlow<DictionaryLookupState> = _dictionaryState

    private val _dictionaryLanguage = MutableStateFlow("pt")
    val dictionaryLanguage: StateFlow<String> = _dictionaryLanguage

    fun setDictionaryLanguage(lang: String) {
        _dictionaryLanguage.value = lang
        val currentWord = when (val s = _dictionaryState.value) {
            is DictionaryLookupState.Success -> s.entry.word
            is DictionaryLookupState.NotFound -> s.word
            is DictionaryLookupState.NotDownloaded -> s.word
            is DictionaryLookupState.Loading -> s.word
            else -> null
        }
        if (currentWord != null) {
            lookupDictionary(currentWord, lang)
        }
    }

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

    /** Dispara busca inteligente: 1 palavra → definição, 2+ palavras → tradução de frase */
    fun captureForDictionary(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size > 1) {
            translatePhrase(trimmed, _dictionaryLanguage.value)
        } else {
            val word = words.firstOrNull()?.replace(Regex("[^\\p{L}]"), "") ?: return
            if (word.isBlank()) return
            lookupDictionary(word, _dictionaryLanguage.value)
        }
    }

    fun translatePhrase(phrase: String, language: String = "en") {
        if (phrase.isBlank()) return
        _dictionaryState.value = DictionaryLookupState.PhraseLoading
        viewModelScope.launch(Dispatchers.IO) {
            if (!dictionaryRepository.isDownloaded(language)) {
                _dictionaryState.value = DictionaryLookupState.NotDownloaded(language, phrase)
                return@launch
            }
            try {
                val entries = dictionaryRepository.translatePhrase(phrase, language)
                if (entries.isNotEmpty()) {
                    _dictionaryState.value = DictionaryLookupState.PhraseSuccess(entries)
                } else {
                    _dictionaryState.value = DictionaryLookupState.PhraseNotFound(phrase)
                }
            } catch (e: Exception) {
                _dictionaryState.value = DictionaryLookupState.Error(e.message ?: "Erro desconhecido")
            }
        }
    }

    fun lookupDictionary(word: String, language: String = "en") {
        if (word.isBlank()) return
        
        _dictionaryState.value = DictionaryLookupState.Loading(word)
        
        viewModelScope.launch(Dispatchers.IO) {
            if (!dictionaryRepository.isDownloaded(language)) {
                _dictionaryState.value = DictionaryLookupState.NotDownloaded(language, word)
                return@launch
            }

            try {
                val entry = dictionaryRepository.getEntry(word, language)
                if (entry != null) {
                    _dictionaryState.value = DictionaryLookupState.Success(entry)
                } else {
                    _dictionaryState.value = DictionaryLookupState.NotFound(word)
                }
            } catch (e: Exception) {
                _dictionaryState.value = DictionaryLookupState.Error(e.message ?: "Erro desconhecido")
            }
        }
    }


    fun downloadDictionary(language: String, wordToLookup: String? = null) {
        _dictionaryState.value = DictionaryLookupState.Downloading(0)
        viewModelScope.launch(Dispatchers.IO) {
            val success = dictionaryRepository.downloadDictionary(language) { progress ->
                _dictionaryState.value = DictionaryLookupState.Downloading(progress)
            }
            if (success) {
                if (wordToLookup != null) {
                    lookupDictionary(wordToLookup, language)
                } else {
                    _dictionaryState.value = DictionaryLookupState.Idle
                }
            } else {
                _dictionaryState.value = DictionaryLookupState.Error("Falha ao baixar o dicionário ($language)")
            }
        }
    }

    fun clearDictionaryState() { _dictionaryState.value = DictionaryLookupState.Idle }

    fun saveDrawing(page: Int, json: String) = viewModelScope.launch(Dispatchers.IO) {
        val existing = drawings.value.find { it.page == page }
        if (existing != null) {
            repository.upsertDrawing(existing.copy(pathsJson = json))
        } else {
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

    fun updateHighlight(id: Long, text: String) = viewModelScope.launch(Dispatchers.IO) { highlights.value.find { it.id == id }?.let { repository.insertHighlight(it.copy(text = text)) } }
    fun deleteHighlight(id: Long) = viewModelScope.launch(Dispatchers.IO) { repository.deleteHighlight(id) }
    fun addHighlight(page: Int, text: String) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertHighlight(HighlightEntity(id = 0L, documentId = documentId, page = page, text = text))
    }

    fun saveMetadata(docType: String, authorLastName: String, authorFirstName: String, title: String, subtitle: String, edition: String, city: String, publisher: String, year: String, journal: String, volume: String, pages: String, url: String, accessDate: String) = viewModelScope.launch(Dispatchers.IO) {
        repository.updateMetadata(documentId, docType, authorLastName, authorFirstName, title, subtitle, edition, city, publisher, year, journal, volume, pages, url, accessDate)
        _document.value = repository.getDocumentById(documentId)
    }
}

class ReaderViewModelFactory(
    private val repository: DocumentRepository,
    private val documentId: String,
    private val dictionaryRepository: DictionaryRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReaderViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReaderViewModel(repository, documentId, dictionaryRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}