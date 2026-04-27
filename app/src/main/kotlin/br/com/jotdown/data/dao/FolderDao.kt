package br.com.jotdown.data.dao

import androidx.room.*
import br.com.jotdown.data.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertFolder(folder: FolderEntity): Long

    @Delete
    fun deleteFolder(folder: FolderEntity)

    @Query("SELECT * FROM folders WHERE id = :id")
    fun getFolderById(id: Long): FolderEntity?
}