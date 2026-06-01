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
            
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(constraints)
            .setInitialDelay(15, TimeUnit.SECONDS) // Debounce
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
