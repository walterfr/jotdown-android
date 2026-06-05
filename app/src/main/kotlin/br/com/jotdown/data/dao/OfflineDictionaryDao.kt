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
                val cursor = db.rawQuery("SELECT * FROM entries WHERE word = ? COLLATE NOCASE", arrayOf(fw))
                if (cursor.moveToFirst()) {
                    val dbWord = cursor.getFirstText("word", "term", "headword") ?: fw
                    val pos = cursor.getFirstText("wordtype", "type", "pos", "part_of_speech") ?: ""
                    val def = cursor.getFirstText("definition", "definitions", "meaning", "gloss", "description")
                    val translation = cursor.getFirstText(
                        "translation",
                        "translations",
                        "translated",
                        "translated_word",
                        "target",
                        "target_word",
                        "pt",
                        "en"
                    )

                    val fullDef = buildString {
                        if (pos.isNotBlank()) append("[$pos] ")
                        if (!def.isNullOrBlank()) append(def)
                    }.takeIf { it.isNotBlank() }

                    cursor.close()
                    return DictionaryCache(
                        word = dbWord,
                        sourceLang = lang,
                        targetLang = if (!translation.isNullOrBlank()) oppositeLanguage(lang) else null,
                        definition = fullDef,
                        translation = translation,
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

    private fun android.database.Cursor.getFirstText(vararg columns: String): String? {
        for (column in columns) {
            val index = getColumnIndex(column)
            if (index >= 0 && !isNull(index)) {
                val value = getString(index)?.trim()
                if (!value.isNullOrBlank()) return value
            }
        }
        return null
    }

    private fun oppositeLanguage(lang: String): String? {
        return when (lang) {
            "en" -> "pt"
            "pt" -> "en"
            else -> null
        }
    }
}
