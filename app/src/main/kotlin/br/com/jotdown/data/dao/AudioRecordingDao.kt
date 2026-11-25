package br.com.jotdown.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import br.com.jotdown.data.entity.AudioRecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioRecordingDao {
    @Query("SELECT * FROM audio_recordings WHERE documentId = :documentId ORDER BY createdAt DESC")
    fun getRecordingsForDocument(documentId: String): Flow<List<AudioRecordingEntity>>

    @Query("SELECT * FROM audio_recordings WHERE documentId = :documentId")
    suspend fun getRecordingsListForDocument(documentId: String): List<AudioRecordingEntity>

    @Query("SELECT * FROM audio_recordings WHERE id = :id LIMIT 1")
    suspend fun getRecordingById(id: Long): AudioRecordingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: AudioRecordingEntity): Long

    @Query("UPDATE audio_recordings SET durationMs = :durationMs, title = :title WHERE id = :id")
    suspend fun updateDurationAndTitle(id: Long, durationMs: Long, title: String): Int

    @Query("DELETE FROM audio_recordings WHERE id = :id")
    suspend fun deleteRecordingById(id: Long): Int
}
