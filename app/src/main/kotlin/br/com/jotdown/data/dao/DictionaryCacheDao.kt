package br.com.jotdown.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import br.com.jotdown.data.entity.DictionaryCache

@Dao
interface DictionaryCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DictionaryCache)

    @Query("""
        SELECT * FROM dictionary_cache 
        WHERE word = :word AND sourceLang = :src AND (targetLang = :tgt OR :tgt IS NULL)
        ORDER BY fetchedAt DESC LIMIT 1
    """)
    suspend fun getLatest(word: String, src: String, tgt: String?): DictionaryCache?

    @Query("DELETE FROM dictionary_cache WHERE fetchedAt < :expiration")
    suspend fun deleteOlderThan(expiration: Long)
}
