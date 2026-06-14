package br.com.jotdown.data.sync

import android.content.Context
import android.content.Intent
import java.io.File

class SyncProviderImpl : SyncProvider {
    private val notSupported = UnsupportedOperationException("Cloud Sync is not supported in FOSS version.")

    override suspend fun backupNow(context: Context): Result<Unit> = Result.failure(notSupported)
    override suspend fun restoreNow(context: Context): Result<Unit> = Result.failure(notSupported)
    override fun getSignInIntent(context: Context): Intent? = null
    override suspend fun handleSignInResult(context: Context, intent: Intent?): Result<Unit> = Result.failure(notSupported)
    override fun getSignedInAccountEmail(context: Context): String? = null
    override fun signOut(context: Context) { /* no-op */ }

    // Drive Library — not available in FOSS
    override fun hasDriveReadAccess(context: Context): Boolean = false
    override fun getDriveLibraryIntent(context: Context): Intent? = null
    override suspend fun findDriveFolderByName(context: Context, name: String): Result<DriveFileInfo?> = Result.failure(notSupported)
    override suspend fun listDrivePdfs(context: Context, folderId: String): Result<List<DriveFileInfo>> = Result.failure(notSupported)
    override suspend fun downloadDriveFile(context: Context, fileId: String, destFile: File, onProgress: (Int) -> Unit): Result<Unit> = Result.failure(notSupported)
    override suspend fun listDriveFolders(context: Context, parentId: String): Result<List<DriveFileInfo>> = Result.failure(notSupported)
}
