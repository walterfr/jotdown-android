package br.com.jotdown

import android.app.Application
import br.com.jotdown.data.dao.OfflineDictionaryDao
import br.com.jotdown.data.db.JotdownDatabase
import br.com.jotdown.data.manager.OfflineDictionaryManager
import br.com.jotdown.data.repository.DictionaryRepository
import br.com.jotdown.data.repository.DocumentRepository

class JotdownApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(this)
    }

    val database by lazy { JotdownDatabase.getInstance(this) }

    val repository by lazy {
        DocumentRepository(
            documentDao   = database.documentDao(),
            annotationDao = database.annotationDao(),
            highlightDao  = database.highlightDao(),
            drawingDao    = database.drawingDao(),
            folderDao     = database.folderDao()
        )
    }

    private val offlineDictionaryDao by lazy { OfflineDictionaryDao(this) }
    private val offlineDictionaryManager by lazy { OfflineDictionaryManager(this) }

    val dictionaryRepository by lazy {
        DictionaryRepository(
            cacheDao = database.dictionaryCacheDao(),
            offlineDao = offlineDictionaryDao,
            offlineManager = offlineDictionaryManager
        )
    }

    val syncProvider by lazy { br.com.jotdown.data.sync.SyncProviderImpl() }
    val syncManager by lazy { br.com.jotdown.data.sync.SyncManager(this) }
}
