package br.com.jotdown.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Open FOSS WebDAV backup/restore sync client built on OkHttp3 + BackupUtil ZIP archives.
 */
object WebDavSyncUtil {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    fun normalizeRemoteUrl(serverUrl: String, remoteFileName: String): String? {
        val base = serverUrl.trim()
        val file = remoteFileName.trim().trimStart('/')
        if (base.isBlank() || file.isBlank()) return null

        val uri = try {
            URI(base)
        } catch (_: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
        if (scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null

        return "${base.trimEnd('/')}/$file"
    }

    fun parentCollectionUrl(fileUrl: String): String? {
        val trimmed = fileUrl.trim()
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
        if (scheme != "https" || uri.host.isNullOrBlank()) return null

        val withoutQuery = trimmed.substringBefore('?').substringBefore('#')
        val lastSlash = withoutQuery.lastIndexOf('/')
        val schemeEnd = withoutQuery.indexOf("://") + 3
        if (lastSlash <= schemeEnd) return null
        return withoutQuery.substring(0, lastSlash + 1)
    }

    fun isPlainHttp(url: String): Boolean =
        url.trim().lowercase(Locale.US).startsWith("http://")

    fun buildBasicAuthHeader(username: String, password: String): String {
        val raw = "$username:$password".toByteArray(Charsets.UTF_8)
        return "Basic ${Base64.getEncoder().encodeToString(raw)}"
    }

    fun isSuccessfulStatus(code: Int): Boolean = code in 200..299 || code == 207

    fun isValidZipHeader(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()

    private fun validateServerUrl(serverUrl: String): String? {
        val trimmed = serverUrl.trim()
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
        if (scheme != "https" || uri.host.isNullOrBlank()) return null
        return trimmed
    }

    private fun Request.Builder.applyAuth(username: String, password: String): Request.Builder {
        if (username.isNotBlank() || password.isNotBlank()) {
            header("Authorization", buildBasicAuthHeader(username, password))
        }
        return this
    }

    suspend fun testConnection(
        serverUrl: String,
        username: String,
        password: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        val validUrl = validateServerUrl(serverUrl)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid WebDAV URL"))

        try {
            val propfindReq = Request.Builder()
                .url(validUrl)
                .method("PROPFIND", RequestBody.create(null, ByteArray(0)))
                .header("Depth", "0")
                .applyAuth(username, password)
                .build()

            val code = client.newCall(propfindReq).execute().use { resp ->
                if (resp.code() == 405) {
                    val headReq = Request.Builder()
                        .url(validUrl)
                        .head()
                        .applyAuth(username, password)
                        .build()
                    client.newCall(headReq).execute().use { it.code() }
                } else {
                    resp.code()
                }
            }

            if (isSuccessfulStatus(code)) {
                Result.success(code)
            } else {
                Result.failure(IOException("HTTP $code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadBackup(
        context: Context,
        serverUrl: String,
        remoteFileName: String,
        username: String,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetUrl = normalizeRemoteUrl(serverUrl, remoteFileName)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid WebDAV URL or filename"))

        val backupFile = BackupUtil.createZipBackupFile(context)
            ?: return@withContext Result.failure(IOException("Failed to build local backup archive"))

        try {
            parentCollectionUrl(targetUrl)?.let { collectionUrl ->
                runCatching {
                    val mkcolReq = Request.Builder()
                        .url(collectionUrl)
                        .method("MKCOL", null)
                        .applyAuth(username, password)
                        .build()
                    client.newCall(mkcolReq).execute().close()
                }
            }

            val putReq = Request.Builder()
                .url(targetUrl)
                .put(RequestBody.create(MediaType.parse("application/zip"), backupFile))
                .applyAuth(username, password)
                .build()

            client.newCall(putReq).execute().use { resp ->
                if (isSuccessfulStatus(resp.code())) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("HTTP ${resp.code()}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { backupFile.delete() }
        }
    }

    suspend fun downloadBackup(
        context: Context,
        serverUrl: String,
        remoteFileName: String,
        username: String,
        password: String
    ): Result<File> = withContext(Dispatchers.IO) {
        val targetUrl = normalizeRemoteUrl(serverUrl, remoteFileName)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid WebDAV URL or filename"))

        val tempFile = File(context.cacheDir, "webdav_restore_${System.currentTimeMillis()}.zip")
        try {
            val getReq = Request.Builder()
                .url(targetUrl)
                .get()
                .applyAuth(username, password)
                .build()

            client.newCall(getReq).execute().use { resp ->
                if (!isSuccessfulStatus(resp.code())) {
                    runCatching { tempFile.delete() }
                    return@withContext Result.failure(IOException("HTTP ${resp.code()}"))
                }
                val body = resp.body() ?: run {
                    runCatching { tempFile.delete() }
                    return@withContext Result.failure(IOException("Empty response body"))
                }
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            val header = ByteArray(4)
            val readCount = tempFile.inputStream().use { it.read(header) }
            if (readCount < 4 || !isValidZipHeader(header)) {
                runCatching { tempFile.delete() }
                return@withContext Result.failure(IOException("Downloaded file is not a valid ZIP archive"))
            }

            Result.success(tempFile)
        } catch (e: Exception) {
            runCatching { tempFile.delete() }
            Result.failure(e)
        }
    }
}
