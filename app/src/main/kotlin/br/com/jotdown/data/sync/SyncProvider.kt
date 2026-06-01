package br.com.jotdown.data.sync

import android.content.Context
import android.content.Intent

interface SyncProvider {
    suspend fun backupNow(context: Context): Result<Unit>
    suspend fun restoreNow(context: Context): Result<Unit>
    
    fun getSignInIntent(context: Context): Intent?
    suspend fun handleSignInResult(context: Context, intent: Intent?): Result<Unit>
    fun getSignedInAccountEmail(context: Context): String?
    fun signOut(context: Context)
}
