package br.com.jotdown.data.sync

import android.content.Context
import android.content.Intent

class SyncProviderImpl : SyncProvider {
    override suspend fun backupNow(context: Context): Result<Unit> {
        return Result.failure(UnsupportedOperationException("Cloud Sync is not supported in FOSS version."))
    }

    override suspend fun restoreNow(context: Context): Result<Unit> {
        return Result.failure(UnsupportedOperationException("Cloud Sync is not supported in FOSS version."))
    }

    override fun getSignInIntent(context: Context): Intent? {
        return null
    }

    override suspend fun handleSignInResult(context: Context, intent: Intent?): Result<Unit> {
        return Result.failure(UnsupportedOperationException("Cloud Sync is not supported in FOSS version."))
    }

    override fun getSignedInAccountEmail(context: Context): String? {
        return null
    }

    override fun signOut(context: Context) {
        // No-op
    }
}
