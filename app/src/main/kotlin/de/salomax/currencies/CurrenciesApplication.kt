package de.salomax.currencies

import android.app.Application
import de.salomax.currencies.repository.BackupScheduler
import de.salomax.currencies.repository.Database
import java.net.InetAddress
import kotlin.concurrent.thread

class CurrenciesApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        warmSharedPreferences()
        prewarmProviderDns()
        reregisterScheduledBackup()
    }

    // WorkManager persists jobs, but re-enqueuing on startup is a cheap way to
    // recover from edge cases (app updates that changed the worker class,
    // "Force stop" that dropped scheduled work, users who cleared app data).
    // No-op when the user hasn't enabled scheduled backups.
    private fun reregisterScheduledBackup() {
        thread(name = "backup-scheduler-init", isDaemon = true) {
            runCatching { BackupScheduler.reschedule(this) }
        }
    }

    // Force each SharedPreferences file the app uses to load from disk on a
    // background thread ahead of the first Activity. SharedPreferences is
    // in-memory after the first read, so subsequent main-thread reads in
    // BaseActivity.onCreate (theme, pure-black) hit RAM instead of blocking
    // on I/O during app startup.
    private fun warmSharedPreferences() {
        thread(name = "prefs-warm", isDaemon = true) {
            runCatching {
                Database(this).getTheme()
            }
        }
    }

    // Resolve the currently-selected provider's host on a background thread so
    // the first network request doesn't pay for DNS. The preference read and
    // resolution both run off the main thread. Failures (offline, DNS outage)
    // are silent — this is a best-effort warm-up, not a health check.
    private fun prewarmProviderDns() {
        thread(name = "dns-prewarm", isDaemon = true) {
            val host = Database(this).getApiProvider().getHost() ?: return@thread
            runCatching { InetAddress.getAllByName(host) }
        }
    }

}
