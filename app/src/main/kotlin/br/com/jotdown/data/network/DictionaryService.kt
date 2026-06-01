package br.com.jotdown.data.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// Google Cloud Definition (placeholder, actual endpoint may differ)
interface GoogleDefinitionService {
    @POST("v1/documents:analyzeSyntax")
    suspend fun getDefinition(@Body request: Map<String, Any>): GoogleDefinitionResponse
}

// Google Cloud Translation
interface GoogleTranslationService {
    @POST("language/translate/v2")
    suspend fun translate(@Body request: Map<String, String>): GoogleTranslationResponse
}

// LibreTranslate
interface LibreTranslateService {
    @POST("/translate")
    suspend fun translate(@Body body: LibreTranslateRequest): LibreTranslateResponse
}

// Wiktionary via MediaWiki API (GET request)
interface WiktionaryService {
    @GET("/w/api.php")
    suspend fun getDefinition(
        @Query("action") action: String = "query",
        @Query("prop") prop: String = "extracts",
        @Query("exintro") exintro: Int = 1,
        @Query("explaintext") explaintext: Int = 1,
        @Query("titles") titles: String,
        @Query("format") format: String = "json"
    ): WiktionaryResponse
}

// Data classes for responses (simplified)
data class GoogleDefinitionResponse(val definitions: List<String>)

data class GoogleTranslationResponse(val data: TranslationData)

data class TranslationData(val translations: List<TranslatedText>)

data class TranslatedText(val translatedText: String)

data class LibreTranslateRequest(val q: String, val source: String, val target: String, val format: String = "text")

data class LibreTranslateResponse(val translatedText: String)

data class WiktionaryResponse(val query: WiktionaryQuery)

data class WiktionaryQuery(val pages: Map<String, Page>)

data class Page(val extract: String?)
