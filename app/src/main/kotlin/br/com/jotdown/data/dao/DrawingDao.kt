package br.com.jotdown.data.dao

import androidx.room.*
import br.com.jotdown.data.entity.DrawingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DrawingDao {

    @Query("SELECT * FROM drawings WHERE documentId = :documentId ORDER BY page ASC")
    fun getDrawingsForDocument(documentId: String): Flow<List<DrawingEntity>>

    @Upsert
    suspend fun upsertDrawing(drawing: DrawingEntity)

    @Query("DELETE FROM drawings WHERE documentId = :documentId AND page = :page")
    suspend fun deleteDrawingForPage(documentId: String, page: Int)

    @Query("DELETE FROM drawings WHERE documentId = :documentId")
    suspend fun deleteAllForDocument(documentId: String)
}
