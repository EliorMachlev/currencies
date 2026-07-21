package de.salomax.currencies

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import de.salomax.currencies.repository.Database
import java.net.InetAddress
import kotlin.concurrent.thread

// Kept in sync with Database.getTheme() unified values.
private const val THEME_LIGHT = 0
private const val THEME_DARK = 1
private const val THEME_OLED = 2

class CurrenciesApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        applyNightMode()
        warmSharedPreferences()
        prewarmProviderDns()
    }

    // Apply the persisted day/night mode before any Activity is created, so
    // BaseActivity.setTheme(AppTheme_PureBlack) resolves against the correct
    // night qualifier on the very first frame. Otherwise the pure-black
    // background renders as the day-mode color until setDefaultNightMode
    // triggers a recreate.
    private fun applyNightMode() {
        AppCompatDelegate.setDefaultNightMode(
            when (Database(this).getTheme()) {
                THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                THEME_DARK, THEME_OLED -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
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
