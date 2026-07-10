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
import java.io.File

class SyncProviderImpl : SyncProvider {

    // ── Sign-In Options ────────────────────────────────────────────────────────

    /** Options for the existing backup flow (DRIVE_APPDATA only). */
    private fun getSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
    }

    /** Options for Drive Library — requests both DRIVE_APPDATA and DRIVE_READONLY. */
    private fun getDriveLibrarySignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA), Scope(DriveScopes.DRIVE_READONLY))
            .build()
    }

    // ── Auth Methods ───────────────────────────────────────────────────────────

    override fun getSignInIntent(context: Context): Intent? {
        val client = GoogleSignIn.getClient(context, getSignInOptions())
        return client.signInIntent
    }

    override fun getDriveLibraryIntent(context: Context): Intent? {
        val client = GoogleSignIn.getClient(context, getDriveLibrarySignInOptions())
        return client.signInIntent
    }

    override fun hasDriveReadAccess(context: Context): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_READONLY))
    }

    override suspend fun handleSignInResult(context: Context, intent: Intent?): Result<Unit> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
            val account = task.result
            if (account != null) Result.success(Unit)
            else Result.failure(Exception("Account is null"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getSignedInAccountEmail(context: Context): String? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account?.email
    }

    override fun signOut(context: Context) {
        GoogleSignIn.getClient(context, getSignInOptions()).signOut()
    }

    // ── Drive Services ─────────────────────────────────────────────────────────

    /** Drive service with DRIVE_APPDATA scope — used for backup/restore. */
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
        ).setApplicationName("Jotdown").build()
    }

    /** Drive service with DRIVE_READONLY scope — used for Drive Library. */
    private fun getDriveReadonlyService(context: Context): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_APPDATA, DriveScopes.DRIVE_READONLY)
        )
        credential.selectedAccount = account.account
        return Drive.Builder(
            com.google.api.client.http.javanet.NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Jotdown").build()
    }

    // ── Drive Library Methods ──────────────────────────────────────────────────

    override suspend fun findDriveFolderByName(context: Context, name: String): Result<CloudFileInfo?> =
        withContext(Dispatchers.IO) {
            try {
                val drive = getDriveReadonlyService(context)
                    ?: throw Exception("Sem autenticação. Conecte-se ao Google.")
                val safeQ = name.replace("'", "\\'")
                val result = drive.files().list()
                    .setQ("mimeType='application/vnd.google-apps.folder' and name='$safeQ' and trashed=false")
                    .setFields("files(id, name)")
                    .setSpaces("drive")
                    .execute()
                val folder = result.files.firstOrNull()
                Result.success(folder?.let { CloudFileInfo(it.id, it.name, 0L) })
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun listCloudDocuments(context: Context, folderId: String): Result<List<CloudFileInfo>> =
        withContext(Dispatchers.IO) {
            try {
                val drive = getDriveReadonlyService(context)
                    ?: throw Exception("Sem autenticação. Conecte-se ao Google.")
                val files = mutableListOf<CloudFileInfo>()
                var pageToken: String? = null
                // Pagina até o fim: sem o loop, o Drive retorna no máximo 100 itens por chamada
                do {
                    val result = drive.files().list()
                        .setQ("'$folderId' in parents and (mimeType='application/pdf' or mimeType='text/plain' or mimeType='text/markdown' or mimeType='text/x-markdown' or mimeType='application/vnd.google-apps.folder') and trashed=false")
                        .setFields("nextPageToken, files(id, name, size, modifiedTime, mimeType)")
                        .setOrderBy("folder, name")
                        .setSpaces("drive")
                        .setPageSize(1000)
                        .setPageToken(pageToken)
                        .execute()
                    result.files.mapTo(files) { f ->
                        CloudFileInfo(
                            id = f.id,
                            name = f.name,
                            sizeBytes = f.getSize() ?: 0L,
                            modifiedTime = f.modifiedTime?.toStringRfc3339() ?: "",
                            isFolder = f.mimeType == "application/vnd.google-apps.folder",
                            mimeType = f.mimeType ?: ""
                        )
                    }
                    pageToken = result.nextPageToken
                } while (pageToken != null)
                Result.success(files)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun listDriveFolders(context: Context, parentId: String): Result<List<CloudFileInfo>> =
        withContext(Dispatchers.IO) {
            try {
                val drive = getDriveReadonlyService(context)
                    ?: throw Exception("Sem autenticação. Conecte-se ao Google.")
                val safeId = parentId.ifBlank { "root" }
                val folders = mutableListOf<CloudFileInfo>()
                var pageToken: String? = null
                do {
                    val result = drive.files().list()
                        .setQ("'$safeId' in parents and mimeType='application/vnd.google-apps.folder' and trashed=false")
                        .setFields("nextPageToken, files(id, name)")
                        .setOrderBy("name")
                        .setSpaces("drive")
                        .setPageSize(1000)
                        .setPageToken(pageToken)
                        .execute()
                    result.files.mapTo(folders) { CloudFileInfo(it.id, it.name, 0L) }
                    pageToken = result.nextPageToken
                } while (pageToken != null)
                Result.success(folders)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun downloadDriveFile(
        context: Context,
        fileId: String,
        destFile: File,
        onProgress: (Int) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveReadonlyService(context)
                ?: throw Exception("Sem autenticação. Conecte-se ao Google.")

            // Fetch file size for progress reporting
            val meta = drive.files().get(fileId).setFields("size").execute()
            val totalSize = meta.getSize()?.takeIf { it > 0L } ?: 1L

            onProgress(0)
            destFile.parentFile?.mkdirs()

            drive.files().get(fileId).executeMediaAsInputStream().use { input ->
                destFile.outputStream().buffered().use { out ->
                    val buffer = ByteArray(16 * 1024)
                    var downloaded = 0L
                    var bytes = input.read(buffer)
                    while (bytes >= 0) {
                        out.write(buffer, 0, bytes)
                        downloaded += bytes
                        val pct = ((downloaded * 99L) / totalSize).toInt().coerceIn(0, 99)
                        onProgress(pct)
                        bytes = input.read(buffer)
                    }
                }
            }
            onProgress(100)
            Result.success(Unit)
        } catch (e: Exception) {
            destFile.delete() // clean up partial file
            Result.failure(e)
        }
    }

    // ── Backup / Restore ───────────────────────────────────────────────────────

    override suspend fun backupNow(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService(context) ?: throw Exception("Not signed in")

            val backupDir = java.io.File(context.cacheDir, "backup")
            if (backupDir.exists()) backupDir.deleteRecursively()
            backupDir.mkdirs()

            val dbDir = java.io.File(context.getDatabasePath("jotdown.db").parent)
            val pdfDir = java.io.File(context.filesDir, "pdfs")

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

            val fileMetadata = com.google.api.services.drive.model.File().apply {
                name = "jotdown_backup.zip"
                parents = listOf("appDataFolder")
            }

            val fileList = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name='jotdown_backup.zip'")
                .execute()

            val fileContent = com.google.api.client.http.FileContent("application/zip", zipFile)

            if (fileList.files.isNotEmpty()) {
                drive.files().update(fileList.files[0].id, null, fileContent).execute()
            } else {
                drive.files().create(fileMetadata, fileContent).execute()
            }

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

            if (fileList.files.isEmpty()) throw Exception("No backup found")

            val fileId = fileList.files[0].id
            val zipFile = java.io.File(context.cacheDir, "jotdown_backup_downloaded.zip")

            val application = context.applicationContext as br.com.jotdown.JotdownApplication
            application.database.close()

            java.io.FileOutputStream(zipFile).use { fos ->
                drive.files().get(fileId).executeMediaAndDownloadTo(fos)
            }

            val extractDir = java.io.File(context.cacheDir, "extracted_backup")
            if (extractDir.exists()) extractDir.deleteRecursively()
            extractDir.mkdirs()

            br.com.jotdown.util.ZipUtil.unzip(zipFile, extractDir)

            val backupFolder = java.io.File(extractDir, "backup")

            val dbBackupDir = java.io.File(backupFolder, "databases")
            if (dbBackupDir.exists()) {
                val dbDir = java.io.File(context.getDatabasePath("jotdown.db").parent)
                dbBackupDir.listFiles()?.forEach { it.copyTo(java.io.File(dbDir, it.name), overwrite = true) }
            }

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
