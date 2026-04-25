package br.com.jotdown.data.dao

import androidx.room.*
import br.com.jotdown.data.entity.AnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {

    @Query("SELECT * FROM annotations WHERE documentId = :documentId ORDER BY page ASC")
    fun getAnnotationsForDocument(documentId: String): Flow<List<AnnotationEntity>>

    @Upsert
    suspend fun upsertAnnotation(annotation: AnnotationEntity)

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteAnnotation(id: Long)

    @Query("DELETE FROM annotations WHERE documentId = :documentId")
    suspend fun deleteAllForDocument(documentId: String)
}
