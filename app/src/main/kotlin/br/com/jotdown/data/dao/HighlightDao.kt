package br.com.jotdown.data.dao
import androidx.room.*
import br.com.jotdown.data.entity.HighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
@JvmSuppressWildcards
abstract class HighlightDao {
    @Query("SELECT * FROM highlights WHERE documentId = :documentId ORDER BY page ASC")
    abstract fun getHighlightsForDocument(documentId: String): Flow<List<HighlightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertHighlight(highlight: HighlightEntity): Long

    @Query("DELETE FROM highlights WHERE id = :id")
    abstract suspend fun deleteHighlight(id: Long): Int

    @Query("DELETE FROM highlights WHERE documentId = :documentId")
    abstract suspend fun deleteAllForDocument(documentId: String): Int
}
