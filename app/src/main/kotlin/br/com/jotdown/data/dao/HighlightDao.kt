package br.com.jotdown.data.dao

import androidx.room.*
import br.com.jotdown.data.entity.HighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {

    @Query("SELECT * FROM highlights WHERE documentId = :documentId ORDER BY page ASC")
    fun getHighlightsForDocument(documentId: String): Flow<List<HighlightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: HighlightEntity)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteHighlight(id: Long)

    @Query("DELETE FROM highlights WHERE documentId = :documentId")
    suspend fun deleteAllForDocument(documentId: String)
}
