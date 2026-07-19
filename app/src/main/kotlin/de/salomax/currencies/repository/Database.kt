package de.salomax.currencies.repository

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Fee
import de.salomax.currencies.model.FeeSide
import de.salomax.currencies.model.Rate
import de.salomax.currencies.model.Timeline
import de.salomax.currencies.util.KEY_RATES_BASE
import de.salomax.currencies.util.KEY_RATES_DATE
import de.salomax.currencies.util.KEY_RATES_PROVIDER
import de.salomax.currencies.util.KEY_RATES_TIME
import de.salomax.currencies.util.NO_PROVIDER_ID
import de.salomax.currencies.util.SharedPreferenceBooleanLiveData
import de.salomax.currencies.util.SharedPreferenceExchangeRatesLiveData
import de.salomax.currencies.util.SharedPreferenceIntLiveData
import de.salomax.currencies.util.SharedPreferenceLongLiveData
import de.salomax.currencies.util.SharedPreferenceStringLiveData
import de.salomax.currencies.util.toLocalDate
import de.salomax.currencies.util.toMillis
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

private const val LEGACY_FEE_KEY = "_fee"
private const val LEGACY_FEE_STR_KEY = "_fee_str"
private const val LEGACY_FEE_ENABLED_KEY = "_feeEnabled"

private const val FEE_TYPE_GLOBAL_EXCHANGE = "global_exchange"
private const val FEE_TYPE_GLOBAL_BANK = "global_bank"
private const val FEE_TYPE_SPECIFIC_PAIR = "specific_pair"

// Unified theme values (see Database.getTheme). Kept as plain ints so the DB
// layer doesn't need to depend on androidx.appcompat.
private const val THEME_LIGHT = 0
private const val THEME_DARK = 1
private const val THEME_SYSTEM = 2
private const val THEME_OLED = 3
private const val THEME_SYSTEM_OLED = 4
private const val DEFAULT_THEME_MODE = THEME_SYSTEM

// Sentinel for "no historical date stored" in the millis-since-epoch pref.
// -1L is used because it can't collide with any real epoch millis (1970-01-01
// stores as 0L; anything after is positive).
private const val NO_HISTORICAL_DATE = -1L

class Database(context: Context) {

    /*
     * current exchange rates from api =============================================================
     */
    private val prefsRates: SharedPreferences = context.getSharedPreferences("rates", MODE_PRIVATE)

    fun insertExchangeRates(items: ExchangeRates) {
        // don't insert null-values. this would clear the cache
        if (items.date != null)
            prefsRates.apply {
                val editor = edit()
                // clear old values
                editor.clear()
                // apply new ones
                editor.putString(KEY_RATES_DATE, items.date.toString())
                editor.putString(KEY_RATES_TIME, items.time?.toString())
                editor.putString(KEY_RATES_BASE, items.base?.iso4217Alpha())
                editor.putInt(KEY_RATES_PROVIDER, items.provider?.id ?: NO_PROVIDER_ID)
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
        return prefsRates.getString(KEY_RATES_DATE, null)?.let { LocalDate.parse(it) }
    }

    /*
     * cached timelines ============================================================================
     *
     * Historical rate observations are immutable once a business day closes,
     * so we persist per-pair timelines and refresh only the tail on each open.
     * Keyed by "<providerId>|<baseCode>|<symbolCode>"; the JSON value is a
     * flat {date -> plainString value} map (provider/base/symbol are already
     * in the key).
     */
    private val prefsTimelines: SharedPreferences =
        context.getSharedPreferences("timelines", MODE_PRIVATE)

    private fun timelineKey(providerId: Int, base: Currency, symbol: Currency): String =
        "${providerId}|${base.iso4217Alpha()}|${symbol.iso4217Alpha()}"

    fun getCachedTimeline(provider: ApiProvider, base: Currency, symbol: Currency): Timeline? {
        val json = prefsTimelines.getString(timelineKey(provider.id, base, symbol), null)
            ?: return null
        return try {
            val obj = JSONObject(json)
            val rates = sortedMapOf<LocalDate, Rate>()
            obj.keys().forEach { key ->
                val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: return@forEach
                val value = obj.optString(key).toBigDecimalOrNull() ?: return@forEach
                rates[date] = Rate(symbol, value)
            }
            if (rates.isEmpty()) return null
            Timeline(
                success = true,
                error = null,
                base = base.iso4217Alpha(),
                startDate = rates.keys.first(),
                endDate = rates.keys.last(),
                rates = rates,
                provider = provider,
            )
        } catch (e: JSONException) {
            Log.w("Database", "Malformed cached timeline, dropping", e)
            null
        }
    }

    fun putCachedTimeline(timeline: Timeline, base: Currency, symbol: Currency) {
        val provider = timeline.provider ?: return
        val rates = timeline.rates ?: return
        val obj = JSONObject()
        rates.forEach { (date, rate) ->
            obj.put(date.toString(), rate.value.toPlainString())
        }
        prefsTimelines.edit()
            .putString(timelineKey(provider.id, base, symbol), obj.toString())
            .apply()
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
        prefsLastState.edit().putLong(keyHistoricalDate, date?.toMillis() ?: NO_HISTORICAL_DATE).apply()
    }

    fun getHistoricalLiveDate(): LiveData<LocalDate?> {
        return SharedPreferenceLongLiveData(prefsLastState, keyHistoricalDate, NO_HISTORICAL_DATE).map {
            if (it == NO_HISTORICAL_DATE) null
            else it.toLocalDate()
        }
    }

    fun getHistoricalDate(): LocalDate? {
        return when (val date = prefsLastState.getLong(keyHistoricalDate, NO_HISTORICAL_DATE)) {
            NO_HISTORICAL_DATE -> null
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
    private val keyFeesJson = "_fees_json"
    private val keyFeeSide = "_fee_side"
    private val keyPreviewConversionEnabled = "_previewConversionEnabled"
    private val keyKeyboardType = "_keyboardType"
    private val keyHapticFeedback = "_hapticFeedback"
    private val keyDecimalPlaces = "_decimalPlaces"
    private val keyChartGrid = "_chartGrid"
    private val keyChartXAxisLabel = "_chartXAxisLabel"
    private val keyChartYAxisLabel = "_chartYAxisLabel"
    private val keyChartHighlightExtremes = "_chartHighlightExtremes"
    private val keyDateFormat = "_dateFormat"
    private val defaultDateFormat = "dd/MM/yy HH:mm"

    /* api */

    fun setApiProvider(api: ApiProvider) {
        prefs.apply {
            edit().putInt(keyApi, api.id).apply()
        }
    }

    fun getApiProvider(): ApiProvider {
        return ApiProvider.fromId(prefs.getInt(keyApi, NO_PROVIDER_ID))
    }

    fun getApiProviderAsync(): LiveData<ApiProvider> {
        return SharedPreferenceIntLiveData(prefs, keyApi, NO_PROVIDER_ID).map {
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
     * 0 = Light           (MODE_NIGHT_NO)
     * 1 = Dark            (MODE_NIGHT_YES)
     * 2 = System default  (MODE_NIGHT_FOLLOW_SYSTEM)
     * 3 = OLED            (MODE_NIGHT_YES, pure-black)
     * 4 = System (OLED)   (MODE_NIGHT_FOLLOW_SYSTEM, pure-black)
     *
     * Migrates the legacy separate pure-black boolean into the new unified
     * value on first read after upgrade, then deletes the legacy key.
     */
    fun getTheme(): Int {
        migrateLegacyPureBlackIfNeeded()
        return prefs.getInt(keyTheme, DEFAULT_THEME_MODE)
    }

    fun isPureBlackEnabled(): Boolean {
        val theme = getTheme()
        return theme == THEME_OLED || theme == THEME_SYSTEM_OLED
    }

    private fun migrateLegacyPureBlackIfNeeded() {
        if (!prefs.contains(keyPureBlackEnabled)) return
        val wasPureBlack = prefs.getBoolean(keyPureBlackEnabled, false)
        val editor = prefs.edit().remove(keyPureBlackEnabled)
        if (wasPureBlack) {
            val current = prefs.getInt(keyTheme, DEFAULT_THEME_MODE)
            val migrated = when (current) {
                THEME_DARK -> THEME_OLED
                THEME_SYSTEM -> THEME_SYSTEM_OLED
                else -> current // Light + OLED is meaningless — keep as Light.
            }
            editor.putInt(keyTheme, migrated)
        }
        editor.apply()
    }

    /* fees */

    fun getFees(): LiveData<List<Fee>> {
        migrateLegacyFeeIfNeeded()
        return SharedPreferenceStringLiveData(prefs, keyFeesJson, "[]")
            .map { parseFeeList(it ?: "[]") }
    }

    fun getFeesBlocking(): List<Fee> {
        migrateLegacyFeeIfNeeded()
        return parseFeeList(prefs.getString(keyFeesJson, "[]") ?: "[]")
    }

    fun addFee(fee: Fee) {
        migrateLegacyFeeIfNeeded()
        val next = getFeesBlocking() + fee
        writeFees(next)
    }

    fun updateFee(fee: Fee) {
        migrateLegacyFeeIfNeeded()
        val next = getFeesBlocking().map { if (it.id == fee.id) fee else it }
        writeFees(next)
    }

    fun deleteFee(id: String) {
        migrateLegacyFeeIfNeeded()
        val next = getFeesBlocking().filter { it.id != id }
        writeFees(next)
    }

    fun getFeeSide(): LiveData<FeeSide> {
        return SharedPreferenceStringLiveData(prefs, keyFeeSide, FeeSide.ORIGINAL.name)
            .map { raw -> parseFeeSide(raw) }
    }

    fun getFeeSideBlocking(): FeeSide {
        return parseFeeSide(prefs.getString(keyFeeSide, FeeSide.ORIGINAL.name))
    }

    fun setFeeSide(side: FeeSide) {
        prefs.edit().putString(keyFeeSide, side.name).apply()
    }

    private fun parseFeeSide(raw: String?): FeeSide {
        return runCatching { FeeSide.valueOf(raw ?: FeeSide.ORIGINAL.name) }
            .getOrDefault(FeeSide.ORIGINAL)
    }

    private fun writeFees(list: List<Fee>) {
        prefs.edit().putString(keyFeesJson, serializeFeeList(list)).apply()
    }

    /**
     * Migrate the legacy single-fee representation into the new [Fee] list.
     * Runs at most once (the presence of the [keyFeesJson] key marks completion).
     * The old markup direction is preserved: an enabled fee becomes a
     * [Fee.GlobalExchange] with `isMarkup=true`; a disabled fee migrates to
     * an empty list.
     */
    private fun migrateLegacyFeeIfNeeded() {
        if (prefs.contains(keyFeesJson)) return
        if (!prefs.contains(LEGACY_FEE_STR_KEY) && !prefs.contains(LEGACY_FEE_KEY)) {
            // no legacy data at all: initialize with empty list
            prefs.edit()
                .putString(keyFeesJson, "[]")
                .remove(LEGACY_FEE_ENABLED_KEY)
                .remove(LEGACY_FEE_STR_KEY)
                .remove(LEGACY_FEE_KEY)
                .apply()
            return
        }
        val enabled = prefs.getBoolean(LEGACY_FEE_ENABLED_KEY, false)
        val migrated = if (enabled) {
            val legacyStr = prefs.getString(LEGACY_FEE_STR_KEY, null)
            val legacyFloat = runCatching { prefs.getFloat(LEGACY_FEE_KEY, Float.NaN) }.getOrNull()
            val percent = legacyStr?.toBigDecimalOrNull()
                ?: legacyFloat?.takeIf { !it.isNaN() }?.toString()?.toBigDecimalOrNull()
            if (percent != null) {
                listOf(
                    Fee.GlobalExchange(
                        id = UUID.randomUUID().toString(),
                        percent = percent,
                        isMarkup = true,
                    )
                )
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }
        prefs.edit()
            .putString(keyFeesJson, serializeFeeList(migrated))
            .remove(LEGACY_FEE_ENABLED_KEY)
            .remove(LEGACY_FEE_STR_KEY)
            .remove(LEGACY_FEE_KEY)
            .apply()
    }

    private fun serializeFeeList(list: List<Fee>): String {
        val arr = JSONArray()
        list.forEach { fee ->
            val obj = JSONObject()
            obj.put("id", fee.id)
            obj.put("percent", fee.percent.toPlainString())
            obj.put("isMarkup", fee.isMarkup)
            when (fee) {
                is Fee.GlobalExchange -> obj.put("type", FEE_TYPE_GLOBAL_EXCHANGE)
                is Fee.GlobalBank -> obj.put("type", FEE_TYPE_GLOBAL_BANK)
                is Fee.SpecificPair -> {
                    obj.put("type", FEE_TYPE_SPECIFIC_PAIR)
                    obj.put("from", fee.from)
                    obj.put("to", fee.to)
                    obj.put("bothWays", fee.bothWays)
                }
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun parseFeeList(json: String): List<Fee> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i -> parseFeeEntry(arr.optJSONObject(i)) }
        } catch (e: JSONException) {
            Log.w("Database", "Malformed fee JSON, resetting", e)
            emptyList()
        }
    }

    private fun parseFeeEntry(obj: JSONObject?): Fee? {
        obj ?: return null
        val id = obj.optString("id", "").ifEmpty { UUID.randomUUID().toString() }
        val percent = obj.optString("percent", "0").toBigDecimalOrNull() ?: return null
        val isMarkup = obj.optBoolean("isMarkup", true)
        return when (obj.optString("type")) {
            FEE_TYPE_GLOBAL_EXCHANGE -> Fee.GlobalExchange(id, percent, isMarkup)
            FEE_TYPE_GLOBAL_BANK -> Fee.GlobalBank(id, percent, isMarkup)
            FEE_TYPE_SPECIFIC_PAIR -> Fee.SpecificPair(
                id = id,
                percent = percent,
                isMarkup = isMarkup,
                from = obj.optString("from", ""),
                to = obj.optString("to", ""),
                bothWays = obj.optBoolean("bothWays", false),
            )
            else -> null
        }
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

    fun setDateFormat(pattern: String) {
        prefs.edit().putString(keyDateFormat, pattern).apply()
    }

    fun getDateFormat(): LiveData<String> {
        return SharedPreferenceStringLiveData(prefs, keyDateFormat, defaultDateFormat)
            .map { it ?: defaultDateFormat }
    }

    fun getDateFormatBlocking(): String {
        return prefs.getString(keyDateFormat, defaultDateFormat) ?: defaultDateFormat
    }

}
