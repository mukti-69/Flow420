package io.github.aedev.flow.notification

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import io.github.aedev.flow.BuildConfig
import io.github.aedev.flow.data.local.LocalDataManager
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.utils.UpdateManager
import io.github.aedev.flow.utils.shouldBlockLaunch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Worker that checks for application updates in the background.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    companion object {
        const val WORK_NAME = "update_check_work"
        private const val TAG = "UpdateCheckWorker"
        private const val COOLDOWN_HOURS = 12L

        suspend fun schedulePeriodicCheck(
            context: Context,
            reschedule: Boolean = false,
        ) {
            val notificationsEnabled = PlayerPreferences(context).notificationsEnabled.first()
            if (!notificationsEnabled) {
                cancelScheduledChecks(context)
                Log.d(TAG, "Skipping update check scheduling because notifications are disabled")
                return
            }

            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()

            val workRequest =
                PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                    12,
                    TimeUnit.HOURS,
                ).setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        WorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS,
                    ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                periodicWorkPolicy(reschedule),
                workRequest,
            )
            Log.d(TAG, "Scheduled update check every 12 hours")
        }

        fun cancelScheduledChecks(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled scheduled update checks")
        }
    }

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            if (!PlayerPreferences(applicationContext).notificationsEnabled.first()) {
                Log.d(TAG, "Notifications disabled, skipping update check")
                return@withContext Result.success()
            }

            if (BuildConfig.DEBUG && !isForcedCheck()) {
                Log.d(TAG, "Skipping background update check in DEBUG mode")
                return@withContext Result.success()
            }

            try {
                val dataManager = LocalDataManager(applicationContext)
                val lastCheck = dataManager.lastUpdateCheck.first()
                val currentTime = System.currentTimeMillis()

                if (currentTime - lastCheck < TimeUnit.HOURS.toMillis(COOLDOWN_HOURS) && !isForcedCheck()) {
                    Log.d(TAG, "Skipping check due to cooldown")
                    return@withContext Result.success()
                }

                Log.d(TAG, "Checking for updates...")

                // Refresh the owner's supported-version floor first: it decides whether the alert
                // has to be mandatory, and caching it here keeps the launch gate working offline.
                val policy = UpdateManager.fetchPolicy()
                if (policy != null) {
                    dataManager.setLastUpdatePolicy(policy)
                }

                val updateInfo = UpdateManager.checkForUpdate(BuildConfig.VERSION_NAME)

                if (updateInfo != null && updateInfo.isNewer) {
                    Log.d(TAG, "New version found: ${updateInfo.version}")
                    dataManager.setLastUpdateInfo(BuildConfig.VERSION_NAME, updateInfo)

                    val mandatory =
                        shouldBlockLaunch(
                            policy = policy ?: dataManager.lastUpdatePolicy.first(),
                            installedVersionCode = BuildConfig.VERSION_CODE,
                            updateAvailable = true,
                        )

                    NotificationHelper.showUpdateNotification(
                        applicationContext,
                        updateInfo.version,
                        updateInfo.changelog,
                        updateInfo.downloadUrl,
                        mandatory = mandatory,
                    )
                } else {
                    Log.d(TAG, "No new updates found")
                }

                dataManager.setLastUpdateCheck(currentTime)

                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check for updates", e)
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }

    private fun isForcedCheck(): Boolean = inputData.getBoolean("force", false)
}
