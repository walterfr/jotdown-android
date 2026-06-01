package br.com.jotdown.data.repository

import br.com.jotdown.data.dao.DictionaryCacheDao
import br.com.jotdown.data.dao.OfflineDictionaryDao
import br.com.jotdown.data.entity.DictionaryCache
import br.com.jotdown.data.manager.OfflineDictionaryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DictionaryRepository(
    private val cacheDao: DictionaryCacheDao,
    private val offlineDao: OfflineDictionaryDao,
    private val offlineManager: OfflineDictionaryManager
) {
    suspend fun getEntry(word: String, lang: String): DictionaryCache? {
        // Tenta buscar no dicionário offline
        val result = offlineDao.getDefinition(word, lang)
        if (result != null) {
            // Pode persistir no cache se quiser, mas como já é local, não é estritamente necessário.
            // Para manter histórico, vamos inserir.
            withContext(Dispatchers.IO) { cacheDao.insert(result) }
            return result
        }
        return null
    }

    fun isDownloaded(lang: String): Boolean {
        return offlineDao.isDownloaded(lang)
    }

    suspend fun downloadDictionary(lang: String, onProgress: (Int) -> Unit): Boolean {
        return offlineManager.downloadDictionary(lang, onProgress)
    }

    fun deleteDictionary(lang: String): Boolean {
        return offlineManager.deleteDictionary(lang)
    }
}
