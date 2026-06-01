package br.com.jotdown.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupUtil {

    private const val DB_NAME = "jotdown_stable.db"

    /**
     * Gera o ficheiro ZIP de backup contendo DB, PDFs e Capas.
     */
    private suspend fun createZipBackup(context: Context): File? = withContext(Dispatchers.IO) {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.getDefault()).format(Date())
            val backupFile = File(context.cacheDir, "Jotdown_Backup_$dateFormat.zip")
            val out = ZipOutputStream(FileOutputStream(backupFile))

            // 1. Backup do Banco de Dados (Room)
            val dbNames = listOf(DB_NAME, "$DB_NAME-wal", "$DB_NAME-shm")
            for (dbName in dbNames) {
                val file = context.getDatabasePath(dbName)
                if (file.exists()) {
                    out.putNextEntry(ZipEntry("db/${file.name}"))
                    file.inputStream().use { it.copyTo(out) }
                    out.closeEntry()
                }
            }

            // 2. Backup das Capas
            val coverDir = File(context.filesDir, "covers")
            if (coverDir.exists()) {
                coverDir.listFiles()?.forEach { cover ->
                    out.putNextEntry(ZipEntry("covers/${cover.name}"))
                    cover.inputStream().use { it.copyTo(out) }
                    out.closeEntry()
                }
            }

            // 3. Backup dos PDFs físicos
            val pdfDir = File(context.filesDir, "pdfs")
            if (pdfDir.exists()) {
                pdfDir.listFiles()?.forEach { pdf ->
                    out.putNextEntry(ZipEntry("pdfs/${pdf.name}"))
                    pdf.inputStream().use { it.copyTo(out) }
                    out.closeEntry()
                }
            }

            out.close()
            return@withContext backupFile
        } catch (e: Exception) {
            Log.e("BackupUtil", "Erro ao criar ZIP: ${e.message}")
            return@withContext null
        }
    }

    /**
     * Exporta via Share Sheet (Compartilhamento padrão)
     */
    suspend fun exportBackup(context: Context) {
        val backupFile = createZipBackup(context) ?: return
        
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

    /**
     * Salva o conteúdo de um arquivo temporário em uma URI escolhida pelo usuário (SAF)
     */
    suspend fun saveBackupToUri(context: Context, sourceFile: File, targetUri: Uri) = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Backup salvo com sucesso!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("BackupUtil", "Erro ao salvar em URI: ${e.message}")
        }
    }

    /**
     * Gera o arquivo e retorna para uso com ACTION_CREATE_DOCUMENT
     */
    suspend fun getBackupFileForSaving(context: Context): File? = createZipBackup(context)

    suspend fun importBackup(context: Context, uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val dbDir = context.getDatabasePath(DB_NAME).parentFile
                val pdfDir = File(context.filesDir, "pdfs").also { if (!it.exists()) it.mkdirs() }
                val coverDir = File(context.filesDir, "covers").also { if (!it.exists()) it.mkdirs() }

                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext
                val zis = ZipInputStream(inputStream)
                var entry = zis.nextEntry
                
                while (entry != null) {
                    val name = entry.name
                    when {
                        name.startsWith("db/") -> {
                            val fileName = name.removePrefix("db/")
                            val targetFile = File(dbDir, fileName)
                            targetFile.outputStream().use { zis.copyTo(it) }
                        }
                        name.startsWith("pdfs/") -> {
                            val fileName = name.removePrefix("pdfs/")
                            val targetFile = File(pdfDir, fileName)
                            targetFile.outputStream().use { zis.copyTo(it) }
                        }
                        name.startsWith("covers/") -> {
                            val fileName = name.removePrefix("covers/")
                            val targetFile = File(coverDir, fileName)
                            targetFile.outputStream().use { zis.copyTo(it) }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                zis.close()

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Backup restaurado! Reiniciando aplicativo...", Toast.LENGTH_LONG).show()
                }
                Thread.sleep(1500)
                android.os.Process.killProcess(android.os.Process.myPid())
            } catch (e: Exception) { 
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Erro ao restaurar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
