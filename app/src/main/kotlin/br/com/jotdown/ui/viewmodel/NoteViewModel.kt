package br.com.jotdown.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.jotdown.data.entity.NoteEntity
import br.com.jotdown.data.repository.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NoteViewModel(
    private val repository: DocumentRepository,
    private val noteId: String
) : ViewModel() {

    private val _note = MutableStateFlow<NoteEntity?>(null)
    val note: StateFlow<NoteEntity?> = _note

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content

    /** Texto do destaque que originou a ficha, resolvido pelo id. */
    private val _quote = MutableStateFlow<String?>(null)
    val quote: StateFlow<String?> = _quote

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val loadedNote = repository.getNoteById(noteId)
            _note.value = loadedNote
            loadedNote?.let {
                _title.value = it.title
                _content.value = it.content
                val hid = it.sourceHighlightId
                val docId = it.sourceDocId
                if (hid != null && docId != null) {
                    _quote.value = repository.getHighlightsForDocument(docId).first().find { h -> h.id == hid }?.text
                }
            }
        }
    }

    fun updateTitle(newTitle: String) {
        _title.value = newTitle
    }

    fun updateContent(newContent: String) {
        _content.value = newContent
    }

    fun saveNote() = viewModelScope.launch(Dispatchers.IO) {
        val currentNote = _note.value
        if (currentNote != null) {
            repository.updateNote(noteId, _title.value, _content.value)
        }
    }

    fun deleteNote() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteNote(noteId)
    }
}

class NoteViewModelFactory(
    private val repository: DocumentRepository,
    private val noteId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NoteViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NoteViewModel(repository, noteId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
