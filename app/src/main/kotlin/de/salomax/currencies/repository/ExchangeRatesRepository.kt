package de.salomax.currencies.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result
import de.salomax.currencies.R
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Timeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private const val NO_HTTP_STATUS = -1
private const val HTTP_OK = 200
private const val MIN_UPDATE_DISPLAY_MS = 750L

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

    /**
     * Gets and returns all latest exchange rates from the API.
     */
    fun getExchangeRates(): LiveData<ExchangeRates?> {
        launchApiCall { start ->
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
        return liveExchangeRates
    }

    /**
     * Gets and returns the timeline of the last year of the given base and target currency
     */
    fun getTimeline(base: Currency, symbol: Currency): LiveData<Timeline?> {
        launchApiCall { start ->
            ExchangeRatesService.getTimeline(
                apiProvider = db.getApiProvider(),
                base = base,
                symbol = symbol,
                context = context
            ).processResponse(
                start = start,
                successFlag = { success },
                errorMessage = { error },
                onSuccess = { timeline ->
                    CoroutineScope(Dispatchers.Main).launch {
                        liveTimeline.setValue(timeline)
                    }
                }
            )
        }
        return liveTimeline
    }

    private fun launchApiCall(block: suspend (start: Long) -> Unit) {
        val start = System.currentTimeMillis()
        db.setUpdating(true)
        CoroutineScope(Dispatchers.IO).launch { block(start) }
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

        // reset timeline
        liveTimeline.postValue(null)
    }

    private fun Int.text(vararg message: Any): String {
        return context.getString(this, *message)
    }

}
