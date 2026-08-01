package br.com.jotdown.data.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
}
