package br.com.jotdown

import android.app.Application
import br.com.jotdown.data.db.JotdownDatabase
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
}

