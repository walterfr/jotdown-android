package br.com.jotdown.data.dao
import androidx.room.*
import br.com.jotdown.data.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
@JvmSuppressWildcards
abstract class FolderDao {
    @Insert
    abstract suspend fun insert(folder: FolderEntity): Long

    @Query("SELECT * FROM folders")
    abstract fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("DELETE FROM folders WHERE id = :id")
    abstract suspend fun deleteFolder(id: Long): Int

    @Query("UPDATE folders SET name = :newName WHERE id = :id")
    abstract suspend fun renameFolder(id: Long, newName: String): Int
}
