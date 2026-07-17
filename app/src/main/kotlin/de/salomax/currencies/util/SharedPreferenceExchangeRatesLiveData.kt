package de.salomax.currencies.util

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.lifecycle.LiveData
import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Rate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

// SharedPreferences keys for the cached "rates" bucket. Shared with Database so
// writer and reader agree on the schema. The leading underscore separates
// metadata keys from currency-code entries (e.g. "USD", "EUR").
internal const val KEY_RATES_BASE = "_base"
internal const val KEY_RATES_DATE = "_date"
internal const val KEY_RATES_TIME = "_time"
internal const val KEY_RATES_PROVIDER = "_provider"

// Sentinel for "no API provider stored yet"; ApiProvider.fromId maps it to the
// default provider. Kept as -1 to match previously persisted values.
internal const val NO_PROVIDER_ID = -1

private const val METADATA_KEY_PREFIX = "_"

class SharedPreferenceExchangeRatesLiveData(private val sharedPrefs: SharedPreferences) : LiveData<ExchangeRates?>() {

    private fun getValueFromPreferences(): ExchangeRates? {
        if (sharedPrefs.getString(KEY_RATES_BASE, null) == null || sharedPrefs.getString(KEY_RATES_DATE, null) == null)
            return null
        // values were previously stored as Float; skip stale entries and return null if all are stale
        val rates = sharedPrefs.all.entries
            .filter { !it.key.startsWith(METADATA_KEY_PREFIX) }
            .sortedBy { it.key }
            .mapNotNull { entry ->
                val str = entry.value as? String ?: return@mapNotNull null
                Currency.fromString(entry.key!!)?.let { Rate(it, BigDecimal(str)) }
            }
        if (rates.isEmpty()) return null
        return ExchangeRates(
            success = true, // success always true, when serving cached data
            error = null, // error message always null, when serving cached data
            base = Currency.fromString(sharedPrefs.getString(KEY_RATES_BASE, null)!!),
            date = LocalDate.parse(sharedPrefs.getString(KEY_RATES_DATE, null))!!,
            time = sharedPrefs.getString(KEY_RATES_TIME, null)?.let { LocalTime.parse(it) },
            rates = rates,
            provider = sharedPrefs.getInt(KEY_RATES_PROVIDER, NO_PROVIDER_ID).let { ApiProvider.fromId(it) }
        )
    }

    private val preferenceChangeListener = OnSharedPreferenceChangeListener { _: SharedPreferences?, _: String? ->
            postValue(getValueFromPreferences())
    }

    override fun onActive() {
        super.onActive()
        postValue(getValueFromPreferences())
        sharedPrefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun onInactive() {
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        super.onInactive()
    }

}
