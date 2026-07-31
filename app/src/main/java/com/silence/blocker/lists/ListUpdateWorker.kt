package com.silence.blocker.lists

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.silence.blocker.SilenceApp
import com.silence.blocker.data.SettingsStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic remote pack refresh (default every 24 hours).
 */
class ListUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? SilenceApp ?: return Result.failure()
        val settings = app.settingsStore
        if (!settings.autoUpdateLists.first()) {
            return Result.success()
        }
        val result = app.listUpdateManager.updateEnabledPacks(respectWifiOnly = true)
        return if (result.failCount > 0 && result.successCount == 0 && !result.skippedWifi) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        const val UNIQUE_NAME = "silence_list_update"

        fun schedule(context: Context, wifiOnly: Boolean) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
                )
                .build()
            val request = PeriodicWorkRequestBuilder<ListUpdateWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }

        fun rescheduleFromSettings(context: Context) {
            // Fire-and-forget read is awkward; call from coroutine in app
            val app = context.applicationContext
            // Best-effort: schedule with unmetered; worker itself rechecks wifi-only
            schedule(app, wifiOnly = true)
        }
    }
}

suspend fun scheduleListUpdates(context: Context, settings: SettingsStore) {
    if (settings.autoUpdateLists.first()) {
        ListUpdateWorker.schedule(context, wifiOnly = settings.updateOnWifiOnly.first())
    } else {
        ListUpdateWorker.cancel(context)
    }
}
