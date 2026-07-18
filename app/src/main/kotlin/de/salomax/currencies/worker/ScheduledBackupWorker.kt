package de.salomax.currencies.worker

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.salomax.currencies.repository.BackupManager
import de.salomax.currencies.repository.BackupResult
import de.salomax.currencies.repository.BackupScheduler
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Written by the worker; also used as a filter when we prune old files so we
// never touch anything the user might have dropped into the same folder.
private const val BACKUP_FILE_PREFIX = "currencies-backup-"
private const val BACKUP_FILE_SUFFIX = ".json"
private const val BACKUP_MIME = "application/json"

// Timestamp is embedded in the filename so files sort chronologically and the
// prune step can pick the oldest without reading each file's metadata.
private val TIMESTAMP_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

class ScheduledBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val treeUri = BackupScheduler.getTreeUri(applicationContext) ?: return Result.success()
        val tree = DocumentFile.fromTreeUri(applicationContext, treeUri) ?: return Result.failure()
        if (!tree.canWrite()) return Result.retry()

        val filename = BACKUP_FILE_PREFIX +
            LocalDateTime.now().format(TIMESTAMP_FORMATTER) +
            BACKUP_FILE_SUFFIX

        // createFile returns null on collision/permission failure; treat as
        // retry rather than success so WorkManager backs off and tries later
        // instead of silently dropping the backup.
        val target = tree.createFile(BACKUP_MIME, filename) ?: return Result.retry()

        // Scheduled backups are always plaintext: prompting the user for a
        // password at midnight defeats the point of a scheduled job, and
        // stashing the password in prefs would defeat the point of the
        // password. Users who need encryption use the manual export.
        val result = BackupManager(applicationContext).export(target.uri, password = null)
        return when (result) {
            is BackupResult.Success -> {
                pruneOldBackups(tree)
                Result.success()
            }
            is BackupResult.Failure -> {
                target.delete()
                Result.retry()
            }
            // Password states can't happen on export — collapse to failure so
            // the compiler enforces exhaustiveness if the sealed class grows.
            is BackupResult.PasswordRequired,
            is BackupResult.WrongPassword -> {
                target.delete()
                Result.failure()
            }
        }
    }

    private fun pruneOldBackups(tree: DocumentFile) {
        val retention = BackupScheduler.getRetention(applicationContext).coerceAtLeast(1)
        val ours = tree.listFiles()
            .filter { it.isFile && (it.name?.startsWith(BACKUP_FILE_PREFIX) == true) }
            // Filename embeds the timestamp so lexicographic order = chronological.
            .sortedByDescending { it.name }
        ours.drop(retention).forEach { it.delete() }
    }
}
