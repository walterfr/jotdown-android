package br.com.jotdown.data.dao

import androidx.room.*
import br.com.jotdown.data.entity.HighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE documentId = :docId ORDER BY page ASC")
    fun getHighlightsForDocument(docId: String): Flow<List<HighlightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertHighlight(highlight: HighlightEntity): Long

    @Query("DELETE FROM highlights WHERE id = :id")
    fun deleteHighlightById(id: Long): Int
}