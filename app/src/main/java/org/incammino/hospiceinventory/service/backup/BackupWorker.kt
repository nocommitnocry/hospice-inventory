package org.incammino.hospiceinventory.service.backup

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Worker per backup automatico schedulato.
 * Esegue backup giornaliero alle 18:00 se l'utente è loggato su Google Drive.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupManager: BackupManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "BackupWorker"
        const val WORK_NAME = "automatic_backup"

        /**
         * Schedula backup giornaliero alle 18:00.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            // Calcola delay fino alle 18:00
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 18)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            val initialDelay = target.timeInMillis - now.timeInMillis

            val workRequest = PeriodicWorkRequestBuilder<BackupWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )

            val delayMinutes = initialDelay / 1000 / 60
            Log.i(TAG, "Backup schedulato, prossimo tra $delayMinutes minuti")
        }

        /**
         * Cancella scheduling.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Backup scheduling cancellato")
        }

        /**
         * Verifica se il backup automatico e' schedulato.
         */
        fun isScheduled(context: Context): Boolean {
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WORK_NAME)
                .get()
            return workInfos.any { !it.state.isFinished }
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Avvio backup automatico")

        return when (val result = backupManager.performBackup()) {
            is BackupResult.Success -> {
                Log.i(TAG, "Backup automatico completato: ${result.message}")
                Result.success()
            }
            is BackupResult.Error -> {
                Log.e(TAG, "Backup automatico fallito: ${result.message}")
                // Retry con backoff esponenziale
                if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
            is BackupResult.NotSignedIn -> {
                Log.w(TAG, "Utente non loggato, skip backup")
                // Non retry se non e' loggato
                Result.failure()
            }
        }
    }
}
