package br.com.jotdown.util

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import kotlin.system.exitProcess

object BackupUtil {
    suspend fun exportBackup(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.getDefault()).format(Date())
                val backupFile = File(context.cacheDir, "Jotdown_Backup_$dateFormat.zip")
                val out = ZipOutputStream(FileOutputStream(backupFile))

                // Backup dos ficheiros do banco de dados (Room)
                val dbNames = listOf("jotdown.db", "jotdown.db-wal", "jotdown.db-shm")
                for (dbName in dbNames) {
                    val file = context.getDatabasePath(dbName)
                    if (file.exists()) {
                        out.putNextEntry(ZipEntry("db/${file.name}"))
                        file.inputStream().use { it.copyTo(out) }
                        out.closeEntry()
                    }
                }

                // Backup dos PDFs fÃ­sicos
                val pdfDir = File(context.filesDir, "pdfs")
                if (pdfDir.exists()) {
                                    // Backup das Capas (Novo v1.1)
                val coverDir = File(context.filesDir, "covers")
                if (coverDir.exists()) {
                    coverDir.listFiles()?.forEach { cover ->
                        out.putNextEntry(ZipEntry("covers/${cover.name}"))
                        cover.inputStream().use { it.copyTo(out) }
                        out.closeEntry()
                    }
                }
                
                pdfDir.listFiles()?.forEach { pdf ->
                        out.putNextEntry(ZipEntry("pdfs/${pdf.name}"))
                        pdf.inputStream().use { it.copyTo(out) }
                        out.closeEntry()
                    }
                }
                out.close()

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
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    suspend fun importBackup(context: Context, uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val dbDir = context.getDatabasePath("jotdown.db").parentFile
                val pdfDir = File(context.filesDir, "pdfs")
                if (!pdfDir.exists()) pdfDir.mkdirs()

                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext
                val zis = ZipInputStream(inputStream)
                var entry = zis.nextEntry
                
                while (entry != null) {
                    val name = entry.name
                    if (name.startsWith("db/")) {
                        val fileName = name.removePrefix("db/")
                        val targetFile = File(dbDir, fileName)
                        targetFile.outputStream().use { zis.copyTo(it) }
                    } else if (name.startsWith("pdfs/")) {
                        val fileName = name.removePrefix("pdfs/")
                        val targetFile = File(pdfDir, fileName)
                        targetFile.outputStream().use { zis.copyTo(it) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                zis.close()

                // Sucesso na importaÃ§Ã£o - Exige reinicializaÃ§Ã£o para o Room carregar o novo DB
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

