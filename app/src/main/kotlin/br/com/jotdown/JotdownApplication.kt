package br.com.jotdown

import android.app.Application
import br.com.jotdown.data.db.JotdownDatabase
import br.com.jotdown.data.repository.DocumentRepository

class JotdownApplication : Application() {

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

