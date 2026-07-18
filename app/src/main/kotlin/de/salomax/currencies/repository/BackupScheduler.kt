package de.salomax.currencies.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import de.salomax.currencies.worker.ScheduledBackupWorker
import java.util.concurrent.TimeUnit

// Preference keys — shared between the settings UI and the WorkManager worker.
// Persisted in the default SharedPreferences file. The tree URI is stored as
// its string form; the worker resolves it back through DocumentFile.
internal const val PREF_KEY_SCHEDULE_FREQUENCY = "backup_schedule_frequency"
internal const val PREF_KEY_SCHEDULE_TREE_URI = "backup_schedule_tree_uri"
internal const val PREF_KEY_SCHEDULE_RETENTION = "backup_schedule_retention"

// Frequency tokens persisted as-is so the UI list values match the pref value
// directly (no int/enum indirection to keep in sync).
internal const val FREQUENCY_OFF = "off"
internal const val FREQUENCY_DAILY = "daily"
internal const val FREQUENCY_WEEKLY = "weekly"
internal const val FREQUENCY_MONTHLY = "monthly"

// WorkManager's minimum periodic interval is 15 min; monthly is expressed as
// 30 days to avoid a variable-length month calculation — close enough for a
// user-facing "monthly" cadence.
private const val DAILY_HOURS = 24L
private const val WEEKLY_HOURS = 24L * 7
private const val MONTHLY_HOURS = 24L * 30

internal const val DEFAULT_RETENTION = 5

// Single WorkManager unique-work name so re-scheduling replaces any previous
// job for this feature rather than accumulating parallel workers.
internal const val WORK_NAME_SCHEDULED_BACKUP = "scheduled_backup"

object BackupScheduler {

    fun prefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    fun getFrequency(context: Context): String =
        prefs(context).getString(PREF_KEY_SCHEDULE_FREQUENCY, FREQUENCY_OFF) ?: FREQUENCY_OFF

    fun getTreeUri(context: Context): Uri? =
        prefs(context).getString(PREF_KEY_SCHEDULE_TREE_URI, null)?.let(Uri::parse)

    fun getRetention(context: Context): Int =
        prefs(context).getInt(PREF_KEY_SCHEDULE_RETENTION, DEFAULT_RETENTION)

    fun setFrequency(context: Context, frequency: String) {
        prefs(context).edit().putString(PREF_KEY_SCHEDULE_FREQUENCY, frequency).apply()
        reschedule(context)
    }

    fun setTreeUri(context: Context, uri: Uri) {
        prefs(context).edit().putString(PREF_KEY_SCHEDULE_TREE_URI, uri.toString()).apply()
        reschedule(context)
    }

    fun setRetention(context: Context, count: Int) {
        prefs(context).edit().putInt(PREF_KEY_SCHEDULE_RETENTION, count).apply()
        // Retention is read by the worker at run time — no need to re-enqueue.
    }

    /**
     * Enqueue (or cancel) the periodic worker based on the current preference
     * state. Called from settings changes and from application startup so the
     * worker is re-registered after upgrades or reinstalls.
     */
    fun reschedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        val frequency = getFrequency(context)
        val treeUri = getTreeUri(context)
        if (frequency == FREQUENCY_OFF || treeUri == null) {
            wm.cancelUniqueWork(WORK_NAME_SCHEDULED_BACKUP)
            return
        }
        val hours = when (frequency) {
            FREQUENCY_DAILY -> DAILY_HOURS
            FREQUENCY_WEEKLY -> WEEKLY_HOURS
            FREQUENCY_MONTHLY -> MONTHLY_HOURS
            else -> {
                wm.cancelUniqueWork(WORK_NAME_SCHEDULED_BACKUP)
                return
            }
        }
        val request = PeriodicWorkRequestBuilder<ScheduledBackupWorker>(hours, TimeUnit.HOURS)
            .build()
        // UPDATE (not KEEP) so that changing the frequency actually shortens the
        // interval; KEEP would silently ignore the new value.
        wm.enqueueUniquePeriodicWork(
            WORK_NAME_SCHEDULED_BACKUP,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
