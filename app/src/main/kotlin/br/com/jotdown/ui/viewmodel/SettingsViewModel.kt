package br.com.jotdown.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.jotdown.JotdownApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val application: JotdownApplication) : ViewModel() {
    private val syncProvider = application.syncProvider

    private val _signedInEmail = MutableStateFlow<String?>(null)
    val signedInEmail: StateFlow<String?> = _signedInEmail.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val dictionaryRepository = application.dictionaryRepository

    private val _isPtDictDownloaded = MutableStateFlow(false)
    val isPtDictDownloaded: StateFlow<Boolean> = _isPtDictDownloaded.asStateFlow()

    private val _isEnDictDownloaded = MutableStateFlow(false)
    val isEnDictDownloaded: StateFlow<Boolean> = _isEnDictDownloaded.asStateFlow()

    private val _dictDownloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val dictDownloadProgress: StateFlow<Map<String, Int>> = _dictDownloadProgress.asStateFlow()

    init {
        checkDictionaryStatus()
    }

    fun checkDictionaryStatus() {
        _isPtDictDownloaded.value = dictionaryRepository.isDownloaded("pt")
        _isEnDictDownloaded.value = dictionaryRepository.isDownloaded("en")
    }

    fun downloadDictionary(lang: String) {
        viewModelScope.launch {
            _dictDownloadProgress.value = _dictDownloadProgress.value.toMutableMap().apply { put(lang, 0) }
            val success = dictionaryRepository.downloadDictionary(lang) { progress ->
                _dictDownloadProgress.value = _dictDownloadProgress.value.toMutableMap().apply { put(lang, progress) }
            }
            if (success) {
                _syncMessage.value = "Dicionário [$lang] baixado com sucesso!"
            } else {
                _syncMessage.value = "Erro ao baixar dicionário [$lang]."
            }
            _dictDownloadProgress.value = _dictDownloadProgress.value.toMutableMap().apply { remove(lang) }
            checkDictionaryStatus()
        }
    }

    fun deleteDictionary(lang: String) {
        if (dictionaryRepository.deleteDictionary(lang)) {
            _syncMessage.value = "Dicionário [$lang] removido."
        } else {
            _syncMessage.value = "Erro ao remover dicionário [$lang]."
        }
        checkDictionaryStatus()
    }

    fun checkSignInStatus() {
        _signedInEmail.value = syncProvider.getSignedInAccountEmail(application)
    }

    fun setSyncMessage(message: String) {
        _syncMessage.value = message
    }

    fun getSignInIntent(): Intent? {
        return syncProvider.getSignInIntent(application)
    }

    fun handleSignInResult(intent: Intent?) {
        viewModelScope.launch {
            val result = syncProvider.handleSignInResult(application, intent)
            if (result.isSuccess) {
                checkSignInStatus()
            } else {
                _syncMessage.value = "Erro ao fazer login: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun signOut() {
        syncProvider.signOut(application)
        _signedInEmail.value = null
    }

    fun backupNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Fazendo backup para o Google Drive..."
            val result = syncProvider.backupNow(application)
            _isSyncing.value = false
            if (result.isSuccess) {
                _syncMessage.value = "Backup concluído com sucesso."
            } else {
                _syncMessage.value = "Erro ao realizar backup: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun restoreNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Iniciando restauração..."
            val result = syncProvider.restoreNow(application)
            _isSyncing.value = false
            if (result.isSuccess) {
                _syncMessage.value = "Restauração concluída. O aplicativo será reiniciado em instantes..."
                kotlinx.coroutines.delay(2000)
                val intent = application.packageManager.getLaunchIntentForPackage(application.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                    application.startActivity(intent)
                }
                Runtime.getRuntime().exit(0)
            } else {
                _syncMessage.value = "Erro ao restaurar: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun schedulePeriodicBackup(hours: Long) {
        application.syncManager.schedulePeriodicBackup(hours)
    }

    // ── Drive Library ─────────────────────────────────────────────────────────

    private val drivePrefs = application.getSharedPreferences("jotdown_drive", Context.MODE_PRIVATE)

    private val _driveFolderName = MutableStateFlow<String?>(drivePrefs.getString("folder_name", null))
    val driveFolderName: StateFlow<String?> = _driveFolderName.asStateFlow()

    private val _driveFolderConnecting = MutableStateFlow(false)
    val driveFolderConnecting: StateFlow<Boolean> = _driveFolderConnecting.asStateFlow()

    fun hasDriveReadAccess(): Boolean = syncProvider.hasDriveReadAccess(application)

    fun getDriveLibraryIntent(): Intent? = syncProvider.getDriveLibraryIntent(application)

    fun handleDriveLibrarySignIn(intent: Intent?, pendingFolderName: String) {
        viewModelScope.launch {
            val result = syncProvider.handleSignInResult(application, intent)
            if (result.isSuccess) {
                checkSignInStatus()
                if (pendingFolderName.isNotBlank()) connectDriveFolder(pendingFolderName)
            } else {
                _syncMessage.value = "Erro ao autorizar acesso ao Drive: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun connectDriveFolder(folderName: String) = viewModelScope.launch {
        _driveFolderConnecting.value = true
        _syncMessage.value = "Buscando pasta \"$folderName\" no Drive..."
        val result = syncProvider.findDriveFolderByName(application, folderName)
        result.fold(
            onSuccess = { folder ->
                if (folder != null) {
                    drivePrefs.edit()
                        .putString("folder_id", folder.id)
                        .putString("folder_name", folderName)
                        .apply()
                    _driveFolderName.value = folderName
                    _syncMessage.value = "✓ Pasta \"$folderName\" conectada com sucesso!"
                } else {
                    _syncMessage.value = "Pasta \"$folderName\" não encontrada no seu Drive."
                }
            },
            onFailure = { e ->
                _syncMessage.value = "Erro ao buscar pasta: ${e.message}"
            }
        )
        _driveFolderConnecting.value = false
    }

    fun disconnectDriveFolder() {
        drivePrefs.edit().remove("folder_id").remove("folder_name").apply()
        _driveFolderName.value = null
        _syncMessage.value = null
    }
}

class SettingsViewModelFactory(private val application: JotdownApplication) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
