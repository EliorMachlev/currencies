package de.salomax.currencies

import android.app.Application
import de.salomax.currencies.repository.Database
import java.net.InetAddress
import kotlin.concurrent.thread

class CurrenciesApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        prewarmProviderDns()
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
