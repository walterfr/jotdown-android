package br.com.jotdown.data.manager

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class OfflineDictionaryManager(private val context: Context) {

    suspend fun downloadDictionary(
        language: String,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val fileName = "dict_$language.db"
        val downloadUrl = "https://github.com/walterfr/jotdown-android/releases/download/v1.0-dictionaries/$fileName"
        val outputFile = context.getDatabasePath(fileName)

        // Certifique-se de que o diretório de bancos de dados existe
        outputFile.parentFile?.mkdirs()

        try {
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext false
            }

            val fileLength = connection.contentLength

            val input = BufferedInputStream(connection.inputStream)
            val output = FileOutputStream(outputFile)

            val data = ByteArray(8192)
            var total: Long = 0
            var count: Int
            var lastProgress = 0

            while (input.read(data).also { count = it } != -1) {
                total += count
                output.write(data, 0, count)
                
                if (fileLength > 0) {
                    val progress = ((total * 100) / fileLength).toInt()
                    if (progress != lastProgress) {
                        lastProgress = progress
                        withContext(Dispatchers.Main) {
                            onProgress(progress)
                        }
                    }
                }
            }

            output.flush()
            output.close()
            input.close()
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            // Se falhar, tenta apagar o arquivo corrompido
            if (outputFile.exists()) {
                outputFile.delete()
            }
            return@withContext false
        }
    }

    fun deleteDictionary(language: String): Boolean {
        val file = context.getDatabasePath("dict_$language.db")
        return if (file.exists()) file.delete() else false
    }
}
