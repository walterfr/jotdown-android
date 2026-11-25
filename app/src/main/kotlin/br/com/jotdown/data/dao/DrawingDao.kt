package br.com.jotdown.data.dao
import androidx.room.*
import br.com.jotdown.data.entity.DrawingEntity
import kotlinx.coroutines.flow.Flow

@Dao
@JvmSuppressWildcards
abstract class DrawingDao {
    @Query("SELECT * FROM drawings WHERE documentId = :documentId ORDER BY page ASC")
    abstract fun getDrawingsForDocument(documentId: String): Flow<List<DrawingEntity>>

    @Query("SELECT * FROM drawings WHERE documentId = :documentId AND page = :page LIMIT 1")
    abstract suspend fun getDrawingForPage(documentId: String, page: Int): DrawingEntity?

    @Upsert
    abstract suspend fun upsertDrawing(drawing: DrawingEntity): Long

    @Query("DELETE FROM drawings WHERE documentId = :documentId AND page = :page")
    abstract suspend fun deleteDrawingForPage(documentId: String, page: Int): Int

    @Query("DELETE FROM drawings WHERE documentId = :documentId")
    abstract suspend fun deleteAllForDocument(documentId: String): Int

    @Query("UPDATE drawings SET page = page + 1 WHERE documentId = :documentId AND page > :afterPage")
    abstract suspend fun shiftPagesAfter(documentId: String, afterPage: Int): Int
}

