package br.com.jotdown.util

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import br.com.jotdown.data.network.GoogleDefinitionService
import br.com.jotdown.data.network.GoogleTranslationService
import br.com.jotdown.data.network.LibreTranslateService
import br.com.jotdown.data.network.WiktionaryService

object NetworkModule {
    private const val GOOGLE_BASE_URL = "https://language.googleapis.com/"
    private const val GOOGLE_TRANSLATE_BASE_URL = "https://translation.googleapis.com/"
    private const val LIBRETRANSLATE_BASE_URL = "https://libretranslate.com/"
    private const val WIKTIONARY_BASE_URL = "https://en.wiktionary.org/"

    private val retrofitGoogle = Retrofit.Builder()
        .baseUrl(GOOGLE_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val retrofitGoogleTranslate = Retrofit.Builder()
        .baseUrl(GOOGLE_TRANSLATE_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val retrofitLibre = Retrofit.Builder()
        .baseUrl(LIBRETRANSLATE_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val retrofitWiktionary = Retrofit.Builder()
        .baseUrl(WIKTIONARY_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val googleDefinitionService: GoogleDefinitionService =
        retrofitGoogle.create(GoogleDefinitionService::class.java)

    val googleTranslationService: GoogleTranslationService =
        retrofitGoogleTranslate.create(GoogleTranslationService::class.java)

    val libreTranslateService: LibreTranslateService =
        retrofitLibre.create(LibreTranslateService::class.java)

    val wiktionaryService: WiktionaryService =
        retrofitWiktionary.create(WiktionaryService::class.java)
}
