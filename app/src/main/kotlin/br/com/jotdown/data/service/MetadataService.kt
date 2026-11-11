package br.com.jotdown.data.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class DOIMetadata(
    val title: String = "",
    val authorFirstName: String = "",
    val authorLastName: String = "",
    val publisher: String = "",
    val year: String = "",
    val journal: String = "",
    val volume: String = "",
    val pages: String = ""
)

data class ISBNMetadata(
    val title: String = "",
    val subtitle: String = "",
    val authorFirstName: String = "",
    val authorLastName: String = "",
    val publisher: String = "",
    val year: String = "",
    val pages: String = ""
)

class MetadataService {
    suspend fun searchDOI(doi: String): DOIMetadata? = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "https://api.crossref.org/works/${URLEncoder.encode(doi, "UTF-8")}"
            val response = URL(url).readText()
            val json = JSONObject(response)
            val message = json.optJSONObject("message") ?: return@withContext null

            val title = message.optString("title", "").replace("[\\[\\]]".toRegex(), "")
            val authors = message.optJSONArray("author")
            val (firstName, lastName) = if (authors != null && authors.length() > 0) {
                val author = authors.getJSONObject(0)
                Pair(
                    author.optString("given", ""),
                    author.optString("family", "")
                )
            } else {
                Pair("", "")
            }
            val publisher = message.optString("publisher", "")
            val year = message.optJSONObject("issued")?.optJSONArray("date-parts")?.optJSONArray(0)?.optInt(0, 0)?.toString() ?: ""
            val journal = message.optString("container-title", "")
            val volume = message.optString("volume", "")
            val pages = message.optString("page", "")

            DOIMetadata(title, firstName, lastName, publisher, year, journal, volume, pages)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun searchISBN(isbn: String): ISBNMetadata? = withContext(Dispatchers.IO) {
        val cleanIsbn = isbn.filter { it.isDigit() || it == 'X' || it == 'x' }
        if (cleanIsbn.isBlank()) return@withContext null

        return@withContext try {
            val urlString = "https://openlibrary.org/api/books?bibkeys=ISBN:$cleanIsbn&format=json&jscmd=data"
            val url = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "Jotdown/0.3.0 (Android; https://github.com/walterfr/jotdown)")
            }
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val key = if (json.has("ISBN:$cleanIsbn")) "ISBN:$cleanIsbn" else "ISBN:${cleanIsbn.uppercase()}"
            val book = json.optJSONObject(key) ?: return@withContext null

            val title = book.optString("title", "")
            val subtitle = book.optString("subtitle", "")

            val authors = book.optJSONArray("authors")
            var firstName = ""
            var lastName = ""
            if (authors != null && authors.length() > 0) {
                val fullName = authors.getJSONObject(0).optString("name", "").trim()
                if (fullName.isNotEmpty()) {
                    val parts = fullName.split("\\s+".toRegex())
                    if (parts.size == 1) {
                        lastName = parts[0]
                    } else {
                        lastName = parts.last()
                        firstName = parts.dropLast(1).joinToString(" ")
                    }
                }
            }

            val publishers = book.optJSONArray("publishers")
            val publisher = if (publishers != null && publishers.length() > 0) {
                publishers.getJSONObject(0).optString("name", "")
            } else ""

            val publishDate = book.optString("publish_date", "")
            val year = Regex("\\b(19|20)\\d{2}\\b").find(publishDate)?.value ?: publishDate

            val numPages = book.optInt("number_of_pages", 0)
            val pages = if (numPages > 0) numPages.toString() else book.optString("pagination", "")

            ISBNMetadata(
                title = title,
                subtitle = subtitle,
                authorFirstName = firstName,
                authorLastName = lastName,
                publisher = publisher,
                year = year,
                pages = pages
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
