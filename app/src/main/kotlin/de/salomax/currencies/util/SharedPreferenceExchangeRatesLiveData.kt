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

class SharedPreferenceExchangeRatesLiveData(private val sharedPrefs: SharedPreferences) : LiveData<ExchangeRates?>() {

    private fun getValueFromPreferences(): ExchangeRates? {
        if (sharedPrefs.getString("_base", null) == null || sharedPrefs.getString("_date", null) == null)
            return null
        // values were previously stored as Float; skip stale entries and return null if all are stale
        val rates = sharedPrefs.all.entries
            .filter { !it.key.startsWith("_") }
            .sortedBy { it.key }
            .mapNotNull { entry ->
                val str = entry.value as? String ?: return@mapNotNull null
                Currency.fromString(entry.key!!)?.let { Rate(it, BigDecimal(str)) }
            }
        if (rates.isEmpty()) return null
        return ExchangeRates(
            true, // success always true, when serving cached data
            null, // error message always null, when serving cached data
            Currency.fromString(sharedPrefs.getString("_base", null)!!),
            LocalDate.parse(sharedPrefs.getString("_date", null))!!,
            rates,
            sharedPrefs.getInt("_provider", -1).let { ApiProvider.fromId(it) }
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
