package br.com.jotdown.data.sync

import android.content.Context
import android.content.Intent
import java.io.File

interface SyncProvider {
    suspend fun backupNow(context: Context): Result<Unit>
    suspend fun restoreNow(context: Context): Result<Unit>

    fun getSignInIntent(context: Context): Intent?
    suspend fun handleSignInResult(context: Context, intent: Intent?): Result<Unit>
    fun getSignedInAccountEmail(context: Context): String?
    fun signOut(context: Context)

    // ── Drive Library ──────────────────────────────────────────────────────────
    /** Returns true if the signed-in account already has DRIVE_READONLY permission. */
    fun hasDriveReadAccess(context: Context): Boolean
    /** Returns a sign-in Intent that requests both DRIVE_APPDATA and DRIVE_READONLY. */
    fun getDriveLibraryIntent(context: Context): Intent?
    /** Finds a folder by name in the user's Drive root. Returns null if not found. */
    suspend fun findDriveFolderByName(context: Context, name: String): Result<CloudFileInfo?>
    /** Lists all PDF files inside the given Drive folder ID. */
    suspend fun listCloudDocuments(context: Context, folderId: String): Result<List<CloudFileInfo>>
    /** Downloads a Drive file to [destFile], reporting progress 0-100 via [onProgress]. */
    suspend fun downloadDriveFile(
        context: Context,
        fileId: String,
        destFile: File,
        onProgress: (Int) -> Unit
    ): Result<Unit>
    /** Lists subfolders of [parentId] (defaults to root). */
    suspend fun listDriveFolders(context: Context, parentId: String = "root"): Result<List<CloudFileInfo>>
}
