package br.com.jotdown.data.dao
import androidx.room.*
import br.com.jotdown.data.entity.AnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
@JvmSuppressWildcards
abstract class AnnotationDao {
    @Query("SELECT * FROM annotations WHERE documentId = :documentId ORDER BY page ASC")
    abstract fun getAnnotationsForDocument(documentId: String): Flow<List<AnnotationEntity>>

    @Upsert
    abstract suspend fun upsertAnnotation(annotation: AnnotationEntity): Long

    @Query("DELETE FROM annotations WHERE id = :id")
    abstract suspend fun deleteAnnotation(id: Long): Int

    @Query("DELETE FROM annotations WHERE documentId = :documentId")
    abstract suspend fun deleteAllForDocument(documentId: String): Int
}

