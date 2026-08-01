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
    version = 19,
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

        /** Adds linkedDocId to highlights for citation linking. */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE highlights ADD COLUMN linkedDocId TEXT DEFAULT NULL")
            }
        }

        /** Liga a ficha ao destaque que a originou (citação por referência). */
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE notes ADD COLUMN sourceHighlightId INTEGER DEFAULT NULL")
            }
        }

        /**
         * Remove fichas e metas. A tabela notes some inteira; folders precisa ser
         * reconstruída porque o SQLite do minSdk 26 não tem DROP COLUMN, e deixar
         * colunas órfãs faria o Room acusar schema divergente.
         */
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS notes")
                database.execSQL("CREATE TABLE IF NOT EXISTS folders_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
                database.execSQL("INSERT INTO folders_new (id, name) SELECT id, name FROM folders")
                database.execSQL("DROP TABLE folders")
                database.execSQL("ALTER TABLE folders_new RENAME TO folders")
            }
        }

        /**
         * A citação passa a carregar o próprio fichamento. Troca linkedDocId por
         * note — o vínculo citação→documento saiu de cena. Tabela reconstruída
         * porque não há DROP COLUMN aqui; as citações em si são preservadas.
         */
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE highlights_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        documentId TEXT NOT NULL,
                        page INTEGER NOT NULL,
                        text TEXT NOT NULL,
                        note TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("INSERT INTO highlights_new (id, documentId, page, text) SELECT id, documentId, page, text FROM highlights")
                database.execSQL("DROP TABLE highlights")
                database.execSQL("ALTER TABLE highlights_new RENAME TO highlights")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_highlights_documentId ON highlights(documentId)")
            }
        }

        fun getInstance(context: Context): JotdownDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    JotdownDatabase::class.java,
                    "jotdown_stable.db"
                )
                .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19)
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
