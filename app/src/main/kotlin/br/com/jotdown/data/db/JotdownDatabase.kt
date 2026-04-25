package br.com.jotdown.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import br.com.jotdown.data.dao.*
import br.com.jotdown.data.entity.*

@Database(
    entities = [
        DocumentEntity::class,
        AnnotationEntity::class,
        HighlightEntity::class,
        DrawingEntity::class,
        FolderEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class JotdownDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun highlightDao(): HighlightDao
    abstract fun drawingDao(): DrawingDao
    abstract fun folderDao(): FolderDao

    companion object {
        @Volatile
        private var INSTANCE: JotdownDatabase? = null

        fun getInstance(context: Context): JotdownDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    JotdownDatabase::class.java,
                    "jotdown.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
