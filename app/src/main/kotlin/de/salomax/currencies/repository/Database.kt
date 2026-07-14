package de.salomax.currencies.repository

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.util.SharedPreferenceBooleanLiveData
import de.salomax.currencies.util.SharedPreferenceExchangeRatesLiveData
import de.salomax.currencies.util.SharedPreferenceIntLiveData
import de.salomax.currencies.util.SharedPreferenceLongLiveData
import de.salomax.currencies.util.SharedPreferenceStringLiveData
import de.salomax.currencies.util.toLocalDate
import de.salomax.currencies.util.toMillis
import java.math.BigDecimal
import java.time.LocalDate

private const val DEFAULT_FEE_PERCENT = "2.2"
private const val LEGACY_FEE_KEY = "_fee"

class Database(context: Context) {

    /*
     * current exchange rates from api =============================================================
     */
    private val prefsRates: SharedPreferences = context.getSharedPreferences("rates", MODE_PRIVATE)

    private val keyDate = "_date"
    private val keyBaseRate = "_base"
    private val keyProvider = "_provider"

    fun insertExchangeRates(items: ExchangeRates) {
        // don't insert null-values. this would clear the cache
        if (items.date != null)
            prefsRates.apply {
                val editor = edit()
                // clear old values
                editor.clear()
                // apply new ones
                editor.putString(keyDate, items.date.toString())
                editor.putString(keyBaseRate, items.base?.iso4217Alpha())
                editor.putInt(keyProvider, items.provider?.id ?: -1)
                items.rates?.forEach { rate ->
                    editor.putString(rate.currency.iso4217Alpha(), rate.value.toPlainString())
                }
                // persist
                editor.apply()
            }
    }

    fun getExchangeRates(): LiveData<ExchangeRates?> {
        return SharedPreferenceExchangeRatesLiveData(prefsRates)
    }

    fun getDate(): LocalDate? {
        return prefsRates.getString(keyDate, null)?.let { LocalDate.parse(it) }
    }

    /*
     * last state ==================================================================================
     */
    private val prefsLastState: SharedPreferences = context.getSharedPreferences("last_state", MODE_PRIVATE)

    private val keyLastStateFrom = "_last_from"
    private val keyLastStateTo = "_last_to"
    private val keyIsUpdating = "_isUpdating"
    private val keyHistoricalDate = "_historical_date"

    fun saveLastUsedRates(from: Currency?, to: Currency?) {
        prefsLastState.apply {
            from?.let { edit().putString(keyLastStateFrom, it.iso4217Alpha()).apply() }
            to?.let { edit().putString(keyLastStateTo, it.iso4217Alpha()).apply() }
        }
    }

    fun getLastBaseCurrency(): LiveData<Currency?> {
        return SharedPreferenceStringLiveData(prefsLastState, keyLastStateFrom, "USD")
            .map { Currency.fromString(it!!) }
    }

    fun getLastDestinationCurrency(): LiveData<Currency?> {
        return SharedPreferenceStringLiveData(prefsLastState, keyLastStateTo, "EUR")
            .map { Currency.fromString(it!!) }
    }

    fun setUpdating(updating: Boolean) {
        prefsLastState.edit().putBoolean(keyIsUpdating, updating).apply()
    }

    fun isUpdating(): SharedPreferenceBooleanLiveData {
        return SharedPreferenceBooleanLiveData(prefsLastState, keyIsUpdating, false)
    }

    fun setHistoricalDate(date: LocalDate?) {
        prefsLastState.edit().putLong(keyHistoricalDate, date?.toMillis() ?: -1).apply()
    }

    fun getHistoricalLiveDate(): LiveData<LocalDate?> {
        return SharedPreferenceLongLiveData(prefsLastState, keyHistoricalDate, -1).map {
            if (it == -1L) null
            else it.toLocalDate()
        }
    }

    fun getHistoricalDate(): LocalDate? {
        return when (val date = prefsLastState.getLong(keyHistoricalDate, -1)) {
            -1L -> null
            else -> date.toLocalDate()
        }
    }

    /*
     * starred currencies ==========================================================================
     */
    private val prefsStarredCurrencies: SharedPreferences =
        context.getSharedPreferences("starred_currencies", MODE_PRIVATE)

    private val keyStars = "_stars"
    private val keyStarsOrder = "_starsOrder"
    private val keyStarredEnabled = "_starredActive"

    private fun readOrderedStarCodes(): List<String> {
        val stored = prefsStarredCurrencies.getString(keyStarsOrder, null)
        if (stored != null)
            return if (stored.isEmpty()) emptyList() else stored.split(",")
        // migrate legacy Set<String> to ordered CSV (alphabetical)
        val legacy = prefsStarredCurrencies.getStringSet(keyStars, HashSet<String>())!!
        return legacy.sorted()
    }

    private fun writeOrderedStarCodes(codes: List<String>) {
        prefsStarredCurrencies.edit()
            .putString(keyStarsOrder, codes.joinToString(","))
            .apply()
    }

    fun toggleCurrencyStar(currency: Currency) {
        val code = currency.iso4217Alpha()
        val current = readOrderedStarCodes()
        val next = if (current.contains(code)) current.minus(code) else current.plus(code)
        writeOrderedStarCodes(next)
    }

    fun getStarredCurrencies(): LiveData<List<Currency>> {
        return SharedPreferenceStringLiveData(prefsStarredCurrencies, keyStarsOrder, null)
            .map { _ ->
                readOrderedStarCodes().mapNotNull { code -> Currency.fromString(code) }
            }
    }

    fun setStarredCurrencyOrder(currencies: List<Currency>) {
        writeOrderedStarCodes(currencies.map { it.iso4217Alpha() })
    }

    fun isFilterStarredEnabled(): SharedPreferenceBooleanLiveData {
        return SharedPreferenceBooleanLiveData(prefsStarredCurrencies, keyStarredEnabled, false)
    }

    fun toggleStarredActive() {
        prefsStarredCurrencies.apply {
            edit().putBoolean(keyStarredEnabled,
                prefsStarredCurrencies.getBoolean(keyStarredEnabled, false).not()
            ).apply()
        }
    }

    /*
     * preferences =================================================================================
     */
    private val prefs: SharedPreferences = context.getSharedPreferences("prefs", MODE_PRIVATE)

    private val keyApi = "_api"
    private val keyOpenExchangeratesApiKey = "_api_openExchangeratesApiKey"
    private val keyTheme = "_theme"
    private val keyPureBlackEnabled = "_pureBlackEnabled"
    private val keyFeeEnabled = "_feeEnabled"
    private val keyFeeValue = "_fee_str"
    private val keyPreviewConversionEnabled = "_previewConversionEnabled"
    private val keyKeyboardType = "_keyboardType"
    private val keyHapticFeedback = "_hapticFeedback"
    private val keyDecimalPlaces = "_decimalPlaces"
    private val keyChartGrid = "_chartGrid"
    private val keyChartXAxisLabel = "_chartXAxisLabel"
    private val keyChartYAxisLabel = "_chartYAxisLabel"
    private val keyChartHighlightExtremes = "_chartHighlightExtremes"
    private val keyChartDateFormatDayFirst = "_chartDateFormatDayFirst"

    /* api */

    fun setApiProvider(api: ApiProvider) {
        prefs.apply {
            edit().putInt(keyApi, api.id).apply()
        }
    }

    fun getApiProvider(): ApiProvider {
        return ApiProvider.fromId(prefs.getInt(keyApi, -1))
    }

    fun getApiProviderAsync(): LiveData<ApiProvider> {
        return SharedPreferenceIntLiveData(prefs, keyApi, -1).map {
            ApiProvider.fromId(it)
        }
    }

    fun setOpenExchangeRatesApiKey(id: String?) {
        prefs.apply {
            edit().putString(keyOpenExchangeratesApiKey, id).apply()
        }
    }

    fun getOpenExchangeRatesApiKey(): String? {
        return prefs.getString(keyOpenExchangeratesApiKey, null)
    }

    fun getOpenExchangeRatesApiKeyAsync(): LiveData<String?> {
        return SharedPreferenceStringLiveData(prefs, keyOpenExchangeratesApiKey, null)
    }

    /* theme */

    fun setTheme(theme: Int) {
        prefs.apply {
            edit().putInt(keyTheme, theme).apply()
        }
    }

    /**
     * 0 = MODE_NIGHT_NO
     * 1 = MODE_NIGHT_YES
     * 2 = MODE_NIGHT_FOLLOW_SYSTEM
     */
    fun getTheme(): Int {
        return prefs.getInt("_theme", 2)
    }

    fun setPureBlackEnabled(enabled: Boolean) {
        prefs.apply {
            edit().putBoolean(keyPureBlackEnabled, enabled).apply()
        }
    }

    fun isPureBlackEnabled(): Boolean {
        return prefs.getBoolean(keyPureBlackEnabled, false)
    }

    /* fee */

    fun setFeeEnabled(enabled: Boolean) {
        prefs.apply {
            edit().putBoolean(keyFeeEnabled, enabled).apply()
        }
    }

    fun isFeeEnabled(): LiveData<Boolean> {
        return SharedPreferenceBooleanLiveData(prefs, keyFeeEnabled, false)
    }

    fun setFee(fee: BigDecimal) {
        prefs.apply {
            edit().putString(keyFeeValue, fee.toPlainString()).apply()
        }
    }

    fun getFee(): LiveData<BigDecimal> {
        migrateLegacyFeeIfNeeded()
        return SharedPreferenceStringLiveData(prefs, keyFeeValue, DEFAULT_FEE_PERCENT)
            .map { (it ?: DEFAULT_FEE_PERCENT).toBigDecimal() }
    }

    private fun migrateLegacyFeeIfNeeded() {
        if (prefs.contains(keyFeeValue) || !prefs.contains(LEGACY_FEE_KEY)) return
        val legacy = runCatching { prefs.getFloat(LEGACY_FEE_KEY, Float.NaN) }.getOrNull()
        val migrated = if (legacy != null && !legacy.isNaN()) legacy.toString() else DEFAULT_FEE_PERCENT
        prefs.edit().putString(keyFeeValue, migrated).remove(LEGACY_FEE_KEY).apply()
    }

    /* preview conversion */

    fun setPreviewConversionEnabled(enabled: Boolean) {
        prefs.apply {
            edit().putBoolean(keyPreviewConversionEnabled, enabled).apply()
        }
    }

    fun isPreviewConversionEnabled(): LiveData<Boolean> {
        return SharedPreferenceBooleanLiveData(prefs, keyPreviewConversionEnabled, false)
    }

    /* keyboard type */

    fun setKeyboardType(type: Int) {
        prefs.apply {
            edit().putInt(keyKeyboardType, type).apply()
        }
    }

    fun getKeyboardType(): LiveData<Int> {
        return SharedPreferenceIntLiveData(prefs, keyKeyboardType, 0)
    }

    /* haptic feedback */

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        prefs.apply {
            edit().putBoolean(keyHapticFeedback, enabled).apply()
        }
    }

    fun isHapticFeedbackEnabled(): LiveData<Boolean> {
        return SharedPreferenceBooleanLiveData(prefs, keyHapticFeedback, true)
    }

    /* decimal places */

    fun setDecimalPlaces(places: Int) {
        prefs.apply {
            edit().putString(keyDecimalPlaces, places.toString()).apply()
        }
    }

    fun getDecimalPlaces(): LiveData<Int> {
        return SharedPreferenceStringLiveData(prefs, keyDecimalPlaces, "2")
            .map { (it ?: "2").toIntOrNull()?.coerceIn(0, 6) ?: 2 }
    }

    /* graph options */

    fun setChartGridEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(keyChartGrid, enabled).apply()
    }

    fun isChartGridEnabled(): LiveData<Boolean> {
        return SharedPreferenceBooleanLiveData(prefs, keyChartGrid, true)
    }

    fun isChartGridEnabledBlocking(): Boolean {
        return prefs.getBoolean(keyChartGrid, true)
    }

    fun setChartXAxisLabelEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(keyChartXAxisLabel, enabled).apply()
    }

    fun isChartXAxisLabelEnabled(): LiveData<Boolean> {
        return SharedPreferenceBooleanLiveData(prefs, keyChartXAxisLabel, true)
    }

    fun isChartXAxisLabelEnabledBlocking(): Boolean {
        return prefs.getBoolean(keyChartXAxisLabel, true)
    }

    fun setChartYAxisLabelEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(keyChartYAxisLabel, enabled).apply()
    }

    fun isChartYAxisLabelEnabled(): LiveData<Boolean> {
        return SharedPreferenceBooleanLiveData(prefs, keyChartYAxisLabel, true)
    }

    fun isChartYAxisLabelEnabledBlocking(): Boolean {
        return prefs.getBoolean(keyChartYAxisLabel, true)
    }

    fun setChartHighlightExtremesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(keyChartHighlightExtremes, enabled).apply()
    }

    fun isChartHighlightExtremesEnabled(): LiveData<Boolean> {
        return SharedPreferenceBooleanLiveData(prefs, keyChartHighlightExtremes, true)
    }

    fun isChartHighlightExtremesEnabledBlocking(): Boolean {
        return prefs.getBoolean(keyChartHighlightExtremes, true)
    }

    fun setChartDateFormatDayFirst(dayFirst: Boolean) {
        prefs.edit().putBoolean(keyChartDateFormatDayFirst, dayFirst).apply()
    }

    fun isChartDateFormatDayFirst(): LiveData<Boolean> {
        return SharedPreferenceBooleanLiveData(prefs, keyChartDateFormatDayFirst, true)
    }

    fun isChartDateFormatDayFirstBlocking(): Boolean {
        return prefs.getBoolean(keyChartDateFormatDayFirst, true)
    }

}
