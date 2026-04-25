package br.com.jotdown.data.dao
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import br.com.jotdown.data.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Query("SELECT * FROM folders")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    @Query("UPDATE folders SET name = :newName WHERE id = :id")
    suspend fun renameFolder(id: Long, newName: String)
}
