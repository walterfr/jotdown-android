package br.com.jotdown.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import br.com.jotdown.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupUtil {

    private const val DB_NAME = "jotdown_stable.db"

    /**
     * Generates a backup ZIP file containing Room DB, covers, PDFs, and synced audio recordings.
     */
    suspend fun createZipBackupFile(context: Context): File? = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val backupFile = File(context.cacheDir, "Jotdown_Backup_$dateFormat.zip")
        try {
            ZipOutputStream(FileOutputStream(backupFile)).use { out ->
                // 1. Room DB + WAL/SHM
                val dbNames = listOf(DB_NAME, "$DB_NAME-wal", "$DB_NAME-shm")
                for (dbName in dbNames) {
                    val file = context.getDatabasePath(dbName)
                    if (file.exists()) {
                        out.putNextEntry(ZipEntry("db/${file.name}"))
                        file.inputStream().use { it.copyTo(out) }
                        out.closeEntry()
                    }
                }

                // 2. Covers
                addDirectoryToZip(out, File(context.filesDir, "covers"), "covers")

                // 3. PDFs
                addDirectoryToZip(out, File(context.filesDir, "pdfs"), "pdfs")

                // 4. Synced audio recordings (recursively: audio/<docId>/<file>.m4a)
                addDirectoryToZip(out, File(context.filesDir, "audio"), "audio")
            }
            backupFile
        } catch (e: Exception) {
            Log.e("BackupUtil", "Erro ao criar ZIP: ${e.message}")
            runCatching { backupFile.delete() }
            null
        }
    }

    private fun addDirectoryToZip(out: ZipOutputStream, dir: File, prefix: String) {
        if (!dir.exists() || !dir.isDirectory) return
        dir.listFiles()?.forEach { child ->
            val entryName = "$prefix/${child.name}"
            if (child.isDirectory) {
                addDirectoryToZip(out, child, entryName)
            } else if (child.isFile) {
                out.putNextEntry(ZipEntry(entryName))
                child.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
    }

    suspend fun exportBackup(context: Context) {
        val backupFile = createZipBackupFile(context) ?: return
        withContext(Dispatchers.Main) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", backupFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Salvar Backup do Jotdown").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    suspend fun saveBackupToUri(context: Context, sourceFile: File, targetUri: Uri) = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.msg_backup_done), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("BackupUtil", "Erro ao salvar em URI: ${e.message}")
        }
    }

    suspend fun getBackupFileForSaving(context: Context): File? = createZipBackupFile(context)

    suspend fun restoreFromZipFile(context: Context, zipFile: File): Boolean = withContext(Dispatchers.IO) {
        if (!zipFile.exists()) return@withContext false
        try {
            FileInputStream(zipFile).use { input ->
                restoreFromStream(context, input)
            }
        } catch (e: Exception) {
            Log.e("BackupUtil", "Erro ao restaurar ZIP: ${e.message}")
            false
        }
    }

    private fun restoreFromStream(context: Context, inputStream: InputStream): Boolean {
        val stagingDir = File(context.cacheDir, "restore_staging_${System.nanoTime()}").also { it.mkdirs() }
        try {
            val stagedSubdirs = listOf("db", "pdfs", "covers", "audio").associateWith { name ->
                File(stagingDir, name).also { it.mkdirs() }
            }
            var restoredAny = false
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val matchedPrefix = stagedSubdirs.keys.firstOrNull { prefix -> name.startsWith("$prefix/") }
                    if (matchedPrefix != null) {
                        val relPath = name.removePrefix("$matchedPrefix/")
                        val stageSubdir = stagedSubdirs.getValue(matchedPrefix)
                        val safeTarget = resolveSafeTarget(stageSubdir, relPath)
                        if (safeTarget != null) {
                            if (entry.isDirectory) {
                                safeTarget.mkdirs()
                            } else {
                                safeTarget.parentFile?.mkdirs()
                                safeTarget.outputStream().use { zis.copyTo(it) }
                                restoredAny = true
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (!restoredAny) return false

            val dbDir = context.getDatabasePath(DB_NAME).parentFile?.also { if (!it.exists()) it.mkdirs() }
                ?: return false
            val pdfDir = File(context.filesDir, "pdfs").also { if (!it.exists()) it.mkdirs() }
            val coverDir = File(context.filesDir, "covers").also { if (!it.exists()) it.mkdirs() }
            val audioDir = File(context.filesDir, "audio").also { if (!it.exists()) it.mkdirs() }

            val application = context.applicationContext as br.com.jotdown.JotdownApplication
            application.database.close()

            copyDirectoryContents(stagedSubdirs.getValue("db"), dbDir)
            copyDirectoryContents(stagedSubdirs.getValue("pdfs"), pdfDir)
            copyDirectoryContents(stagedSubdirs.getValue("covers"), coverDir)
            copyDirectoryContents(stagedSubdirs.getValue("audio"), audioDir)
            return true
        } finally {
            runCatching { stagingDir.deleteRecursively() }
        }
    }

    private fun copyDirectoryContents(sourceDir: File, targetDir: File) {
        if (!sourceDir.exists() || !sourceDir.isDirectory) return
        sourceDir.listFiles()?.forEach { child ->
            val dest = File(targetDir, child.name)
            if (child.isDirectory) {
                if (!dest.exists()) dest.mkdirs()
                copyDirectoryContents(child, dest)
            } else if (child.isFile) {
                dest.parentFile?.mkdirs()
                child.inputStream().use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    private fun resolveSafeTarget(baseDir: File, relativePath: String): File? {
        if (relativePath.isBlank()) return null
        val target = File(baseDir, relativePath)
        val baseCanonical = baseDir.canonicalPath + File.separator
        val targetCanonical = target.canonicalPath
        return if (targetCanonical.startsWith(baseCanonical)) target else null
    }

    suspend fun importBackup(context: Context, uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val ok = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    restoreFromStream(context, inputStream)
                } ?: false
                if (!ok) throw IllegalStateException("Invalid or empty backup archive")

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.msg_restore_done), Toast.LENGTH_LONG).show()
                }

                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                Runtime.getRuntime().exit(0)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val msg = context.getString(R.string.msg_restore_error, e.message ?: "")
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
