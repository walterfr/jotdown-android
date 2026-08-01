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
        DictionaryCache::class,
        NoteEntity::class
    ],
    version = 15,
    exportSchema = false
)
abstract class JotdownDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun highlightDao(): HighlightDao
    abstract fun drawingDao(): DrawingDao
    abstract fun folderDao(): FolderDao
    abstract fun dictionaryCacheDao(): DictionaryCacheDao
    abstract fun noteDao(): br.com.jotdown.data.dao.NoteDao

    companion object {
        @Volatile
        private var INSTANCE: JotdownDatabase? = null

        /** Adds the driveFileId column — no data loss. */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE documents ADD COLUMN driveFileId TEXT DEFAULT NULL")
            }
        }

        /** Adds readingStatus column for tracking reading progress. */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE documents ADD COLUMN readingStatus TEXT DEFAULT 'TO_READ'")
            }
        }

        /** Adds goal fields to folders: description, deadline, isGoal. */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE folders ADD COLUMN description TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE folders ADD COLUMN deadline INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE folders ADD COLUMN isGoal INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Creates notes table for atomic notes (Zettelkasten). */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE notes (
                        id TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL DEFAULT '',
                        content TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        labels TEXT NOT NULL DEFAULT '',
                        sourceDocId TEXT DEFAULT NULL,
                        sourcePage INTEGER DEFAULT NULL
                    )
                """)
                database.execSQL("CREATE INDEX idx_notes_source ON notes(sourceDocId)")
            }
        }

        /** Adds DOI field to documents for CrossRef metadata import. */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE documents ADD COLUMN doi TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): JotdownDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    JotdownDatabase::class.java,
                    "jotdown_stable.db"
                )
                .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
