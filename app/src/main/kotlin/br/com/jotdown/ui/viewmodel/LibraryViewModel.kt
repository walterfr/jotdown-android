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

    val folders: StateFlow<List<FolderEntity>> = repository.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val documents: StateFlow<List<DocumentSummary>> = _currentFolder.flatMapLatest { folder ->
        repository.getDocumentSummariesByFolder(folder?.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun enterFolder(folder: FolderEntity?) { _currentFolder.value = folder }

    fun renameFolder(id: Long, newName: String) = viewModelScope.launch { repository.renameFolder(id, newName) }
    fun renameDocument(id: String, newTitle: String) = viewModelScope.launch { repository.renameDocument(id, newTitle) }

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

                context.contentResolver.openInputStream(uri)?.use { input ->
                    pdfFile.outputStream().use { output -> input.copyTo(output) }
                }

                if (pdfFile.exists() && pdfFile.length() > 0L) {
                    val doc = DocumentEntity(
                        id = docId, fileName = fileName, title = fileName.removeSuffix(".pdf").removeSuffix(".PDF"),
                        dateAdded = System.currentTimeMillis(), folderId = _currentFolder.value?.id, pdfFilePath = pdfFile.absolutePath
                    )
                    repository.saveDocument(doc)
                }
            } catch (e: Exception) { Log.e("Jotdown", "Erro: ${e.message}") }
        }
    }

    fun deleteDocument(context: Context, id: String) = viewModelScope.launch {
        val doc = repository.getDocumentById(id)
        doc?.pdfFilePath?.let { File(it).delete() }
        repository.deleteDocument(id)
    }
}
class LibraryViewModelFactory(private val repository: DocumentRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return LibraryViewModel(repository) as T
    }
}
