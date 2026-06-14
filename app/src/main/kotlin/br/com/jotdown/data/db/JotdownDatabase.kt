package br.com.jotdown.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import br.com.jotdown.data.dao.*
import br.com.jotdown.data.entity.*

@Database(
    entities = [
        DocumentEntity::class,
        AnnotationEntity::class,
        HighlightEntity::class,
        DrawingEntity::class,
        FolderEntity::class,
        DictionaryCache::class
    ],
    version = 11,
    exportSchema = false
)
abstract class JotdownDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun highlightDao(): HighlightDao
    abstract fun drawingDao(): DrawingDao
    abstract fun folderDao(): FolderDao
    abstract fun dictionaryCacheDao(): DictionaryCacheDao

    companion object {
        @Volatile
        private var INSTANCE: JotdownDatabase? = null

        /** Adds the driveFileId column — no data loss. */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE documents ADD COLUMN driveFileId TEXT DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): JotdownDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    JotdownDatabase::class.java,
                    "jotdown_stable.db"
                )
                .addMigrations(MIGRATION_10_11)
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
