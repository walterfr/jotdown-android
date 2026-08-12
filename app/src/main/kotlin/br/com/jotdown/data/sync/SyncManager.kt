package br.com.jotdown.data.sync

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.*
import java.util.concurrent.TimeUnit

class SyncManager(private val context: Context) {
    
    fun schedulePeriodicBackup(hours: Long = 1) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val request = PeriodicWorkRequestBuilder<BackupWorker>(hours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
            
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "PeriodicBackup",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun triggerImmediateSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        // backupNow() re-zips and re-uploads the whole DB + all PDFs every time it
        // runs — not incremental. At 15s, a normal editing burst (a few highlights
        // a minute apart) could trigger several full re-uploads instead of one.
        // 5 minutes collapses that into a single sync per active session without
        // hurting durability: local DB is already the source of truth, and
        // schedulePeriodicBackup() is the real safety net if a session never idles.
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(constraints)
            .setInitialDelay(5, TimeUnit.MINUTES) // Debounce
            .build()
            
        WorkManager.getInstance(context).enqueueUniqueWork(
            "ImmediateSync",
            ExistingWorkPolicy.REPLACE, // Replaces previous pending work, acting as a debounce
            request
        )
    }

    fun getSyncWorkInfo(): LiveData<List<WorkInfo>> {
        return WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData("ImmediateSync")
    }
}
