package br.com.jotdown.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.jotdown.data.repository.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReaderTab(val documentId: String, val title: String)

class ReaderTabsViewModel(
    application: Application,
    private val repository: DocumentRepository
) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("reader_tabs", 0)
    private val _tabs = MutableStateFlow(readSavedIds().map { ReaderTab(it, it) })
    val tabs: StateFlow<List<ReaderTab>> = _tabs

    private val _activeDocumentId = MutableStateFlow(
        preferences.getString("active_document_id", null)?.takeIf { id -> _tabs.value.any { it.documentId == id } }
            ?: _tabs.value.firstOrNull()?.documentId
    )
    val activeDocumentId: StateFlow<String?> = _activeDocumentId

    init {
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) {
                _tabs.value.mapNotNull { tab ->
                    repository.getDocumentById(tab.documentId)?.let { document ->
                        ReaderTab(tab.documentId, document.title.takeIf(String::isNotBlank) ?: document.fileName)
                    }
                }.associateBy { it.documentId }
            }
            _tabs.value = _tabs.value.mapNotNull { restored[it.documentId] }
            if (_activeDocumentId.value !in _tabs.value.map { it.documentId }) {
                _activeDocumentId.value = _tabs.value.firstOrNull()?.documentId
            }
            persist()
        }
    }

    fun openDocument(documentId: String, allowMultiple: Boolean) {
        val current = _tabs.value
        if (current.any { it.documentId == documentId }) {
            _activeDocumentId.value = documentId
        } else {
            _tabs.value = if (allowMultiple) current + ReaderTab(documentId, documentId)
            else listOf(ReaderTab(documentId, documentId))
            _activeDocumentId.value = documentId
            persist()
        }

        viewModelScope.launch {
            val title = withContext(Dispatchers.IO) {
                repository.getDocumentById(documentId)?.let { document ->
                    document.title.takeIf(String::isNotBlank) ?: document.fileName
                }
            } ?: return@launch
            _tabs.value = _tabs.value.map { if (it.documentId == documentId) it.copy(title = title) else it }
            persist()
        }
    }

    fun select(documentId: String) {
        if (_tabs.value.any { it.documentId == documentId }) {
            _activeDocumentId.value = documentId
            persist()
        }
    }

    /** Returns true when caller should leave the reader because no tabs remain. */
    fun close(documentId: String): Boolean {
        val oldTabs = _tabs.value
        val index = oldTabs.indexOfFirst { it.documentId == documentId }
        if (index < 0) return oldTabs.isEmpty()

        val updated = oldTabs.filterNot { it.documentId == documentId }
        _tabs.value = updated
        if (_activeDocumentId.value == documentId) {
            _activeDocumentId.value = updated.getOrNull(index.coerceAtMost(updated.lastIndex))?.documentId
        }
        persist()
        return updated.isEmpty()
    }

    private fun readSavedIds(): List<String> = preferences.getString("open_document_ids", "")
        .orEmpty().split('\n').filter(String::isNotBlank).distinct()

    private fun persist() {
        preferences.edit()
            .putString("open_document_ids", _tabs.value.joinToString("\n") { it.documentId })
            .putString("active_document_id", _activeDocumentId.value)
            .apply()
    }
}

class ReaderTabsViewModelFactory(
    private val application: Application,
    private val repository: DocumentRepository
) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReaderTabsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReaderTabsViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
