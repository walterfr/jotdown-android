package br.com.jotdown.data.sync

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncProviderImpl : SyncProvider {

    private val clientId = "475243510962-jk2e11rfp9egof4sn3j7i1h9604lig4s.apps.googleusercontent.com"

    private fun getSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
    }

    override fun getSignInIntent(context: Context): Intent? {
        val client = GoogleSignIn.getClient(context, getSignInOptions())
        return client.signInIntent
    }

    override suspend fun handleSignInResult(context: Context, intent: Intent?): Result<Unit> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
            val account = task.result
            if (account != null) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Account is null"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getSignedInAccountEmail(context: Context): String? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account?.email
    }

    override fun signOut(context: Context) {
        val client = GoogleSignIn.getClient(context, getSignInOptions())
        client.signOut()
    }

    private fun getDriveService(context: Context): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = account.account

        return Drive.Builder(
            com.google.api.client.http.javanet.NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("Jotdown")
            .build()
    }

    override suspend fun backupNow(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService(context) ?: throw Exception("Not signed in")
            
            // 1. Prepare zip
            val backupDir = java.io.File(context.cacheDir, "backup")
            if (backupDir.exists()) backupDir.deleteRecursively()
            backupDir.mkdirs()
            
            val dbDir = java.io.File(context.getDatabasePath("jotdown.db").parent)
            val pdfDir = java.io.File(context.filesDir, "pdfs")
            
            // Copy db and pdfs to backupDir
            val dbBackupDir = java.io.File(backupDir, "databases")
            if (!dbBackupDir.exists()) dbBackupDir.mkdirs()
            dbDir.listFiles()?.forEach { it.copyTo(java.io.File(dbBackupDir, it.name), overwrite = true) }
            
            val pdfBackupDir = java.io.File(backupDir, "pdfs")
            if (!pdfBackupDir.exists()) pdfBackupDir.mkdirs()
            if (pdfDir.exists()) {
                pdfDir.listFiles()?.forEach { it.copyTo(java.io.File(pdfBackupDir, it.name), overwrite = true) }
            }
            
            val zipFile = java.io.File(context.cacheDir, "jotdown_backup.zip")
            br.com.jotdown.util.ZipUtil.zipDirectory(backupDir, zipFile)
            
            // 2. Upload to Drive appDataFolder
            val fileMetadata = com.google.api.services.drive.model.File().apply {
                name = "jotdown_backup.zip"
                parents = listOf("appDataFolder")
            }
            
            // Check if exists
            val fileList = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name='jotdown_backup.zip'")
                .execute()
                
            val fileContent = com.google.api.client.http.FileContent("application/zip", zipFile)
            
            if (fileList.files.isNotEmpty()) {
                val fileId = fileList.files[0].id
                drive.files().update(fileId, null, fileContent).execute()
            } else {
                drive.files().create(fileMetadata, fileContent).execute()
            }
            
            // Cleanup
            backupDir.deleteRecursively()
            zipFile.delete()
            
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun restoreNow(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService(context) ?: throw Exception("Not signed in")
            
            val fileList = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name='jotdown_backup.zip'")
                .execute()
                
            if (fileList.files.isEmpty()) {
                throw Exception("No backup found")
            }
            
            val fileId = fileList.files[0].id
            val zipFile = java.io.File(context.cacheDir, "jotdown_backup_downloaded.zip")
            
            java.io.FileOutputStream(zipFile).use { fos ->
                drive.files().get(fileId).executeMediaAndDownloadTo(fos)
            }
            
            val extractDir = java.io.File(context.cacheDir, "extracted_backup")
            if (extractDir.exists()) extractDir.deleteRecursively()
            extractDir.mkdirs()
            
            br.com.jotdown.util.ZipUtil.unzip(zipFile, extractDir)
            
            val backupFolder = java.io.File(extractDir, "backup")
            
            // Replace DB
            val dbBackupDir = java.io.File(backupFolder, "databases")
            if (dbBackupDir.exists()) {
                val dbDir = java.io.File(context.getDatabasePath("jotdown.db").parent)
                dbBackupDir.listFiles()?.forEach { it.copyTo(java.io.File(dbDir, it.name), overwrite = true) }
            }
            
            // Replace PDFs
            val pdfBackupDir = java.io.File(backupFolder, "pdfs")
            if (pdfBackupDir.exists()) {
                val pdfDir = java.io.File(context.filesDir, "pdfs")
                if (!pdfDir.exists()) pdfDir.mkdirs()
                pdfBackupDir.listFiles()?.forEach { it.copyTo(java.io.File(pdfDir, it.name), overwrite = true) }
            }
            
            extractDir.deleteRecursively()
            zipFile.delete()
            
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
