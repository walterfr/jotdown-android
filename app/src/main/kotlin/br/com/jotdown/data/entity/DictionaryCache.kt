package br.com.jotdown.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dictionary_cache")
data class DictionaryCache(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val sourceLang: String,
    val targetLang: String?,
    val definition: String?,
    val translation: String?,
    val fetchedAt: Long = System.currentTimeMillis()
)
