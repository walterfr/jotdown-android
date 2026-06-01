package br.com.jotdown.data.dao

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import br.com.jotdown.data.entity.DictionaryCache
import java.io.File

class OfflineDictionaryDao(private val context: Context) {

    fun getDefinition(word: String, lang: String = "en"): DictionaryCache? {
        val dbFile = context.getDatabasePath("dict_$lang.db")
        if (!dbFile.exists()) {
            return null
        }

        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            
            // Remove acentos e converte para minúsculas
            val normalizedWord = java.text.Normalizer.normalize(word.trim().lowercase(), java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            val searchWord = normalizedWord
            val fallbacks = listOf(
                searchWord,
                if (searchWord.endsWith("s")) searchWord.dropLast(1) else null,
                if (searchWord.endsWith("es")) searchWord.dropLast(2) else null,
                if (lang == "en" && searchWord.endsWith("ed")) searchWord.dropLast(2) else null,
                if (lang == "en" && searchWord.endsWith("ing")) searchWord.dropLast(3) else null,
                if (lang == "pt" && searchWord.endsWith("ões")) searchWord.dropLast(3) + "ão" else null,
                if (lang == "pt" && searchWord.endsWith("oes")) searchWord.dropLast(3) + "ao" else null
            ).mapNotNull { it }.distinct()

            for (fw in fallbacks) {
                val cursor = db.rawQuery("SELECT word, wordtype, definition FROM entries WHERE word = ? COLLATE NOCASE", arrayOf(fw))
                if (cursor.moveToFirst()) {
                    val dbWord = cursor.getString(0)
                    val pos = cursor.getString(1) ?: ""
                    val def = cursor.getString(2) ?: ""
                    
                    val fullDef = buildString {
                        if (pos.isNotBlank()) append("[$pos] ")
                        append(def)
                    }
                    cursor.close()
                    return DictionaryCache(
                        word = dbWord,
                        sourceLang = lang,
                        targetLang = null,
                        definition = fullDef,
                        translation = null,
                        fetchedAt = System.currentTimeMillis()
                    )
                }
                cursor.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            db?.close()
        }
        
        return null
    }

    fun isDownloaded(lang: String = "en"): Boolean {
        return context.getDatabasePath("dict_$lang.db").exists()
    }
}
