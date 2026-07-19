package de.salomax.currencies.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result
import de.salomax.currencies.R
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Rate
import de.salomax.currencies.model.Timeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDate

private const val NO_HTTP_STATUS = -1
private const val HTTP_OK = 200
private const val MIN_UPDATE_DISPLAY_MS = 750L
// How far back to keep timeline data. The UI only renders the last year, but a
// small buffer avoids re-fetching when the sliding window shifts by a day.
private const val TIMELINE_WINDOW_DAYS = 400L

// Non-breaking space + 👀 emoji, used as the trailing eyeballs on the bold
// error message shown in the UI (rendered as HTML by the calling view).
private const val EYES_SUFFIX = "\u00A0\uD83D\uDC40"
// Non-breaking space + 🤓 emoji, used at the end of the "try another API"
// suggestion line.
private const val NERD_SUFFIX = "\u00A0\uD83E\uDD13"

class ExchangeRatesRepository(private val context: Context) {

    private val db = Database(context)
    private val liveExchangeRates = db.getExchangeRates()
    private val liveTimeline = MutableLiveData<Timeline?>()
    private var liveError = MutableLiveData<String?>()
    private var isUpdating = db.isUpdating()

    // Track in-flight fetches so rapid re-triggers (swipe-to-refresh spam,
    // toolbar tap during pull, currency swap chains) share the current job
    // instead of spawning parallel HTTP requests against the same endpoint.
    // A single key per fetch shape is enough: rates has no args, timeline
    // is keyed by "base|symbol".
    private var ratesJob: Job? = null
    private val timelineJobs = mutableMapOf<String, Job>()

    /**
     * Gets and returns all latest exchange rates from the API.
     */
    fun getExchangeRates(): LiveData<ExchangeRates?> {
        if (ratesJob?.isActive != true) {
            ratesJob = launchApiCall { start ->
                ExchangeRatesService.getRates(
                    apiProvider = db.getApiProvider(),
                    date = db.getHistoricalDate(),
                    context
                ).processResponse(
                    start = start,
                    successFlag = { success },
                    errorMessage = { error },
                    onSuccess = { db.insertExchangeRates(it) }
                )
            }
        }
        return liveExchangeRates
    }

    /**
     * Gets and returns the timeline of the last year of the given base and target currency.
     *
     * Persists per-pair timelines and refreshes only the missing tail on each call, so
     * repeated opens paint instantly from cache and hit the network only for the days
     * that were added since the last fetch.
     */
    fun getTimeline(base: Currency, symbol: Currency): LiveData<Timeline?> {
        val key = "${base.iso4217Alpha()}|${symbol.iso4217Alpha()}"
        val existing = timelineJobs[key]
        if (existing?.isActive != true) {
            val provider = db.getApiProvider()
            val cached = db.getCachedTimeline(provider, base, symbol)
            // Fast-paint: show cached data while the tail refresh runs.
            if (cached != null) liveTimeline.postValue(cached)

            val today = LocalDate.now()
            val windowStart = today.minusDays(TIMELINE_WINDOW_DAYS)
            // Re-fetch the last cached day (in case it was preliminary) plus everything
            // after it. If we have no cache, fetch the full window.
            val fetchStart = cached?.rates?.keys?.lastOrNull()
                ?.let { maxOf(it, windowStart) }
                ?: windowStart

            val job = launchApiCall { start ->
                ExchangeRatesService.getTimeline(
                    apiProvider = provider,
                    base = base,
                    symbol = symbol,
                    startDate = fetchStart,
                    endDate = today,
                    context = context
                ).processResponse(
                    start = start,
                    successFlag = { success },
                    errorMessage = { error },
                    onSuccess = { fresh ->
                        val merged = mergeTimeline(cached, fresh, base, symbol, windowStart)
                        db.putCachedTimeline(merged, base, symbol)
                        CoroutineScope(Dispatchers.Main).launch {
                            liveTimeline.setValue(merged)
                        }
                    }
                )
            }
            timelineJobs[key] = job
        }
        return liveTimeline
    }

    private fun mergeTimeline(
        cached: Timeline?,
        fresh: Timeline,
        base: Currency,
        symbol: Currency,
        windowStart: LocalDate,
    ): Timeline {
        val merged = sortedMapOf<LocalDate, Rate>()
        cached?.rates?.forEach { (date, rate) ->
            if (!date.isBefore(windowStart)) merged[date] = rate
        }
        // Fresh values overwrite cached values for the same date.
        fresh.rates?.forEach { (date, rate) -> merged[date] = rate }
        return Timeline(
            success = merged.isNotEmpty(),
            error = if (merged.isEmpty()) fresh.error else null,
            base = base.iso4217Alpha(),
            startDate = merged.keys.firstOrNull(),
            endDate = merged.keys.lastOrNull(),
            rates = merged,
            provider = fresh.provider ?: cached?.provider,
        )
    }

    private fun launchApiCall(block: suspend (start: Long) -> Unit): Job {
        val start = System.currentTimeMillis()
        db.setUpdating(true)
        return CoroutineScope(Dispatchers.IO).launch { block(start) }
    }

    private suspend fun <T : Any> Result<T, FuelError>.processResponse(
        start: Long,
        successFlag: T.() -> Boolean?,
        errorMessage: T.() -> String?,
        onSuccess: suspend (T) -> Unit,
    ) {
        val data = component1()
        val fuelError = component2()
        if (data != null && fuelError == null) {
            val ok = data.successFlag()
            if (ok == null || ok == true) {
                postIsUpdating(start)
                onSuccess(data)
                liveError.postValue(null)
            } else {
                postError(data.errorMessage())
            }
        } else {
            handleGenericError(fuelError)
        }
    }

    private fun handleGenericError(fuelError: FuelError?) {
        when {
            // shouldn't happen...
            fuelError == null ->
                postError(R.string.error_generic.text())
            // print http response code, if available
            fuelError.response.statusCode != NO_HTTP_STATUS && fuelError.response.statusCode != HTTP_OK -> {
                postError(R.string.error_http.text(fuelError.response.statusCode))
            }
            // generic network error
            else -> {
                when (fuelError.exception) {
                    // timeout after 15s. likely server not reachable
                    is SocketTimeoutException ->
                        postError(R.string.error_timeout.text())
                    // happens e.g. when device is offline or there's a DNS error
                    is UnknownHostException ->
                        postError(R.string.error_no_data.text())
                    // received no data - happens e.g. with RUB @ Norges Bank
                    is NoSuchElementException ->
                        postError(R.string.error_empty_response.text())
                    // everything else
                    else ->
                        postError(
                            fuelError.localizedMessage?.let { R.string.error.text(it) }
                                ?: R.string.error_generic.text()
                        )
                }
            }
        }
    }

    fun getError(): LiveData<String?> {
        return liveError
    }

    fun isUpdating(): LiveData<Boolean> {
        return isUpdating
    }

    /*
     * "update" for at least 750ms
     */
    private suspend fun postIsUpdating(start: Long) {
        val now = System.currentTimeMillis()
        if (now - start < MIN_UPDATE_DISPLAY_MS) {
            db.setUpdating(true)

            withContext(Dispatchers.Main) {
                launch {
                    delay(MIN_UPDATE_DISPLAY_MS - (now - start))
                    db.setUpdating(false)
                }
            }
        } else
            db.setUpdating(false)
    }

    private fun postError(message: String?) {
        // disable progress bar
        db.setUpdating(false)

        // post error
        var errorMessage = "<b>" + (message ?: R.string.error_api_error.text()) + "$EYES_SUFFIX</b>"
        // tell the user the API can be changed
        if (message?.contains(R.string.error_no_data.text()) != true)
            errorMessage += "\n<br>${R.string.error_try_another_api.text()}$NERD_SUFFIX"
        liveError.postValue(errorMessage)
        // Preserve any cached/previously-fetched timeline instead of wiping it —
        // showing stale-but-valid data beats a blank chart when the network hiccups.
    }

    private fun Int.text(vararg message: Any): String {
        return context.getString(this, *message)
    }

}
