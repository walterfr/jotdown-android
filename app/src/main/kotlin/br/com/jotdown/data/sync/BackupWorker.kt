package br.com.jotdown.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import br.com.jotdown.JotdownApplication

class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as JotdownApplication
        val syncProvider = app.syncProvider
        
        // Check if signed in
        val email = syncProvider.getSignedInAccountEmail(app)
        if (email == null) {
            return Result.failure()
        }

        val result = syncProvider.backupNow(app)
        return if (result.isSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
