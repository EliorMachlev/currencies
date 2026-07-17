package de.salomax.currencies.viewmodel.main

import android.app.Application
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.core.text.HtmlCompat
import androidx.core.text.bold
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import de.salomax.currencies.R
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Fee
import de.salomax.currencies.model.FeeSide
import de.salomax.currencies.repository.Database
import de.salomax.currencies.repository.ExchangeRatesRepository
import de.salomax.currencies.util.combineWith
import de.salomax.currencies.util.getDecimalSeparator
import de.salomax.currencies.util.getSignificantDecimalPlaces
import de.salomax.currencies.util.hasAppendedCurrencySymbol
import de.salomax.currencies.util.toHumanReadableNumber
import org.mariuszgromada.math.mxparser.Expression
import java.math.BigDecimal
import java.math.MathContext
import java.text.Collator
import java.time.LocalDate
import java.time.ZoneId

private val PERCENTAGE_DIVISOR = BigDecimal("100")

// KeyboardType value used by the basic (non-extended) keypad. Any other value
// (currently only "1" = calculator/extended) unlocks the operators row.
private const val KEYBOARD_TYPE_BASIC = 0

@Suppress("unused", "MemberVisibilityCanBePrivate")
class MainViewModel(val app: Application, onlyCache: Boolean = false) : AndroidViewModel(app) {

    constructor(app: Application) : this(app, false)

    class Factory(val app: Application, val onlyCache: Boolean = false) :
        ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(app, onlyCache) as T
        }
    }

    private var repository: ExchangeRatesRepository = ExchangeRatesRepository(app)
    private val db = db

    // repository data
    private var dbLiveItems: LiveData<ExchangeRates?>
    private var exchangeRates: LiveData<ExchangeRates?>
    private val starredLiveItems: LiveData<List<Currency>>
    private val onlyShowStarred: LiveData<Boolean>
    private val liveError = repository.getError()

    // ui
    private var isUpdating: LiveData<Boolean> = repository.isUpdating()
    val isExtendedKeypadEnabled: LiveData<Boolean> = db.getKeyboardType().map { it != KEYBOARD_TYPE_BASIC }
    val isHapticFeedbackEnabled: LiveData<Boolean> = db.isHapticFeedbackEnabled()
    private val decimalPlaces: LiveData<Int> = db.getDecimalPlaces()


    // number input
    private val currentBaseValueText = MutableLiveData("0")
    private val currentCalculationValueText = MutableLiveData<String?>()

    // currency selection
    private val currentBaseCurrency: LiveData<Currency?>
    private val currentDestinationCurrency: LiveData<Currency?>

    // fees
    private val fees: LiveData<List<Fee>>
    private val feeSide: LiveData<FeeSide>

    /*
     * repository data =============================================================================
     */

    init {
        // only update if data is old: https://github.com/Formicka/exchangerate.host
        // "Rates are updated around midnight UTC every working day."
        val currentDate = LocalDate.now(ZoneId.of("UTC"))
        val cachedDate = db.getDate()
        val historicalDate = db.getHistoricalDate()

        dbLiveItems = when {
            // force-use cache
            onlyCache -> db.getExchangeRates()
            // first run: fetch data
            cachedDate == null -> repository.getExchangeRates()
            // historical rates in use...
            historicalDate != null -> {
                // ...and already cached
                if (historicalDate == cachedDate) db.getExchangeRates()
                // ...and not cached
                else repository.getExchangeRates()
            }
            // fetch if stored date is before the current date
            cachedDate.isBefore(currentDate) -> repository.getExchangeRates()
            // else just use the cached value
            else -> db.getExchangeRates()
        }

        starredLiveItems = db.getStarredCurrencies()
        onlyShowStarred = db.isFilterStarredEnabled()

        fees = db.getFees()
        feeSide = db.getFeeSide()

        //
        exchangeRates = object : MediatorLiveData<ExchangeRates?>() {
            var liveItems: ExchangeRates? = null

            init {
                addSource(dbLiveItems) { liveItems = it; calc() }
                addSource(starredLiveItems) { calc() }
                addSource(onlyShowStarred) { calc() }
            }

            private fun calc() {
                liveItems?.let { rates ->
                    this.value = rates
                        // usa a copy with ...
                        .copy(
                            rates = rates.rates
                                // ... the correct sort order of the rates
                                ?.sortedWith(
                                    // sort by full name (locale-aware)
                                    compareBy(Collator.getInstance()) { rate ->
                                        rate.currency.fullName(app)
                                    }
                                )
                        )
                }
            }
        }

        // update currently selected currencies when rates are updated:
        // sometimes the selected rates aren't available anymore, so reset them
        val baseCurrency = db.getLastBaseCurrency()
        val destinationCurrency = db.getLastDestinationCurrency()
        currentBaseCurrency = object : MediatorLiveData<Currency?>() {
            var base: Currency? = null
            var rates: ExchangeRates? = null

            init {
                addSource(baseCurrency) { base = it; update() }
                addSource(exchangeRates) { rates = it; update() }
            }

            private fun update() {
                this.value =
                    // last used is present in the current currency set
                    rates?.rates?.find { it.currency == base }?.currency
                        // not present, so just return the first of the set
                        ?: rates?.rates?.firstOrNull()?.currency
            }
        }
        currentDestinationCurrency = object : MediatorLiveData<Currency?>() {
            var destination: Currency? = null
            var rates: ExchangeRates? = null

            init {
                addSource(destinationCurrency) { destination = it; update() }
                addSource(exchangeRates) { rates = it; update() }
            }

            private fun update() {
                this.value =
                    // last used is present in the current currency set
                    rates?.rates?.find { it.currency == destination }?.currency
                            // not present, so just return the first of the set
                        ?: rates?.rates?.firstOrNull()?.currency
            }
        }

    }

    /**
     * all the current rates and/or an error message, if present
     */
    internal fun getExchangeRates(): LiveData<ExchangeRates?> {
        return exchangeRates
    }

    /**
     * update the data, without checking the cache
     */
    internal fun forceUpdateExchangeRate() {
        if (isUpdating.value != true)
            dbLiveItems = repository.getExchangeRates()
    }

    /**
     * all the currencies that the user has starred
     */
    internal fun getStarredCurrencies(): LiveData<List<Currency>> {
        return starredLiveItems
    }

    /**
     * persist the user's manual ordering of starred currencies
     */
    internal fun setStarredCurrencyOrder(currencies: List<Currency>) {
        db.setStarredCurrencyOrder(currencies)
    }

    /**
     * whether the currencies should be filtered
     */
    internal fun isFilterStarredEnabled(): LiveData<Boolean> {
        return onlyShowStarred
    }

    /**
     * switch the starred-filter on/off
     */
    internal fun toggleStarredActive() {
        db.toggleStarredActive()
    }

    /**
     * de-/star a currency
     */
    internal fun toggleCurrencyStar(currencyCode: Currency) {
        db.toggleCurrencyStar(currencyCode)
    }

    /**
     * the error message, if present
     */
    internal fun getError(): LiveData<String?> = liveError

    /**
     * if the app is updating the rates
     */
    internal fun isUpdating(): LiveData<Boolean> = isUpdating

    /**
     * all configured fees
     */
    internal fun getFees(): LiveData<List<Fee>> {
        return fees
    }

    /**
     * on which side of the conversion the fee should be applied
     */
    internal fun getFeeSide(): LiveData<FeeSide> {
        return feeSide
    }

    /**
     * persist the fee side.
     */
    internal fun setFeeSide(side: FeeSide) {
        db.setFeeSide(side)
    }

    internal val ratesInformationFooter = object : MediatorLiveData<Spanned?>() {
        var exchangeRates: ExchangeRates? = null
        var baseCurrency: Currency? = null
        var destinationCurrency: Currency? = null

        init {
            addSource(getExchangeRates()) { exchangeRates = it; update() }
            addSource(currentBaseCurrency) { baseCurrency = it; update() }
            addSource(currentDestinationCurrency) { destinationCurrency = it; update() }
        }

        fun update() {
            if (exchangeRates != null && baseCurrency != null && destinationCurrency != null) {
                // base currency
                val baseValue = exchangeRates!!.rates?.find { it.currency == baseCurrency }?.value
                // target currency
                val destinationValue = exchangeRates!!.rates?.find { it.currency == destinationCurrency }?.value
                val destinationValueCalculated = baseValue?.let {
                    destinationValue?.divide(it, MathContext.DECIMAL128)
                }

                // create string
                this.value = HtmlCompat.fromHtml(
                    app.getString(
                        R.string.info_conversion,
                        "1",
                        baseCurrency!!.iso4217Alpha(),
                        destinationValueCalculated?.toHumanReadableNumber(
                            app,
                            decimalPlaces = destinationValueCalculated.getSignificantDecimalPlaces(2)
                        ) ?: "",
                        destinationCurrency!!.iso4217Alpha()
                    ),
                    HtmlCompat.FROM_HTML_MODE_LEGACY
                )
            }
        }
    }

    /*
     * base and destination text ===================================================================
     */

    /**
     * the total base value
     */
    private val currentBaseValue = object : MediatorLiveData<String?>() {
        var baseValueText: String? = null
        var calculationValueText: String? = null

        init {
            addSource(currentBaseValueText) { baseValueText = it; update() }
            addSource(currentCalculationValueText) { calculationValueText= it; update() }
        }

        fun update() {
            if (isInCalculationMode())
                this.value = calculationValueText?.evaluateMathExpression()
            else
                this.value = baseValueText
        }

        // Turns e.g. "1 + 2 × 4" to "9"
        fun String.evaluateMathExpression(): String? {
            // change nice operators to proper computer operators
            var s = this
                .replace(" ", "")
                .replace("\u2212", "-")
                .replace("\u00D7", "*")
                .replace("\u00F7", "/")
            // smart percentage: A+B% = A+(A*B/100), A-B% = A-(A*B/100)
            s = s.replace(Regex("""(\d+(?:\.\d+)?)([+\-])(\d+(?:\.\d+)?)%""")) { m ->
                "${m.groupValues[1]}${m.groupValues[2]}(${m.groupValues[1]}*${m.groupValues[3]}/100)"
            }
            // simple percentage: B% = B/100
            s = s.replace("%", "/100")
            // fill, if last character is an operator
            when (s.trim().last()) {
                '/' -> s += "1"
                '*' -> s += "1"
                '+' -> s += "0"
                '-' -> s += "0"
                '.' -> s += "0"
            }
            // calculate
            val result = Expression(s).calculate()
            return if (result.isNaN())
                "0"
            else
                result.toBigDecimal().toPlainString()
        }
    }

    /**
     * the total base value, as BigDecimal (internal is string)
     */
    internal fun getCurrentBaseValueAsNumber(): LiveData<BigDecimal> {
        return currentBaseValue.map {
            it?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        }
    }

    /**
     * the nicely formatted, total base value including the currency symbol at the right position.
     */
    internal fun getCurrentBaseValueFormatted(): LiveData<SpannableStringBuilder> {
        return currentBaseValue.combineWith(currentBaseCurrency) { value, currency ->
            val number = (value?.toHumanReadableNumber(
                app,
                trim = isInCalculationMode(),
                decimalPlaces = if (isInCalculationMode()) 2 else null
            ) ?: "0")
            val symbol = currency?.symbol()

            if (hasAppendedCurrencySymbol(app))
                SpannableStringBuilder() // 123 $
                    .bold { append(number) }
                    .append(if (symbol != null) " $symbol" else "")
            else
                SpannableStringBuilder() // $ 123
                    .append(if (symbol != null) "$symbol " else "")
                    .bold { append(number) }
        }
    }

    // ===============================

    /**
     * the nicely formatted calculation string: e.g. 4 + 2.2 - 4 / 2
     */
    internal fun getCalculationInputFormatted(): LiveData<String?> {
        return currentCalculationValueText.map {
            it?.replace(".", getDecimalSeparator(app))
        }
    }

    // ===============================

    /**
     * the total destination value
     */
    private val result = object : MediatorLiveData<String>() {
        var rates: ExchangeRates? = null
        var baseValue: String? = null
        var baseCurrency: Currency? = null
        var destinationCurrency: Currency? = null
        var feeList: List<Fee>? = null
        var side: FeeSide? = null

        init {
            // rates changed
            addSource(exchangeRates) { rates = it; calculateResult() }
            // base input changed
            addSource(currentBaseValue) { baseValue = it; calculateResult() }
            // base currency changed
            addSource(currentBaseCurrency) { baseCurrency = it; calculateResult() }
            // destination currency changed
            addSource(currentDestinationCurrency) { destinationCurrency = it; calculateResult() }
            // fees changed
            addSource(fees) { feeList = it; calculateResult() }
            // fee side toggled
            addSource(feeSide) { side = it; calculateResult() }
        }

        private fun calculateResult() {
            val amount: BigDecimal = baseValue?.toBigDecimal() ?: BigDecimal.ZERO
            val baseRate = rates?.rates?.find { it.currency == baseCurrency }
            val destinationRate = rates?.rates?.find { it.currency == destinationCurrency }

            if (baseRate != null && destinationRate != null) {
                val fair = amount
                    .divide(baseRate.value, MathContext.DECIMAL128)
                    .multiply(destinationRate.value)
                val displayed = when (side ?: FeeSide.ORIGINAL) {
                    FeeSide.ORIGINAL -> fair
                    FeeSide.CONVERTED -> {
                        val stack = computeTotalStack(
                            feeList.orEmpty(),
                            baseCurrency,
                            destinationCurrency,
                        )
                        if (stack.compareTo(BigDecimal.ZERO) == 0) fair
                        else fair.divide(stack, MathContext.DECIMAL128)
                    }
                }
                this.value = displayed.toPlainString()
            }
        }
    }

    /**
     * Return only those fees that apply for the given base/destination pair.
     */
    private fun applicableFees(
        all: List<Fee>,
        base: Currency?,
        dest: Currency?,
    ): List<Fee> {
        val baseCode = base?.iso4217Alpha()
        val destCode = dest?.iso4217Alpha()
        return all.filter { fee ->
            when (fee) {
                is Fee.GlobalExchange, is Fee.GlobalBank -> true
                is Fee.SpecificPair -> {
                    if (baseCode == null || destCode == null) false
                    else if (fee.from == baseCode && fee.to == destCode) true
                    else if (fee.bothWays && fee.from == destCode && fee.to == baseCode) true
                    else false
                }
            }
        }
    }

    /**
     * Compute the multiplicative stack factor for a subset of fees:
     * `product over subset of (1 +/- percent/100)`.
     */
    private fun stackFactor(subset: List<Fee>): BigDecimal {
        var acc = BigDecimal.ONE
        subset.forEach { fee ->
            val delta = fee.percent.divide(PERCENTAGE_DIVISOR, MathContext.DECIMAL128)
            val factor = if (fee.isMarkup) BigDecimal.ONE + delta else BigDecimal.ONE - delta
            acc = acc.multiply(factor, MathContext.DECIMAL128)
        }
        return acc
    }

    private fun computeTotalStack(
        all: List<Fee>,
        base: Currency?,
        dest: Currency?,
    ): BigDecimal {
        val applicable = applicableFees(all, base, dest)
        val specific = stackFactor(applicable.filterIsInstance<Fee.SpecificPair>())
        val exchange = stackFactor(applicable.filterIsInstance<Fee.GlobalExchange>())
        val bank = stackFactor(applicable.filterIsInstance<Fee.GlobalBank>())
        return specific.multiply(exchange, MathContext.DECIMAL128)
            .multiply(bank, MathContext.DECIMAL128)
    }

    /**
     * Public accessor for the fee stack factor of an arbitrary pair,
     * used by ad-hoc UIs (e.g. the quick-conversions popup) that need to
     * apply fees outside the main result pipeline.
     */
    internal fun feeStackFor(base: Currency?, dest: Currency?): BigDecimal {
        return computeTotalStack(fees.value.orEmpty(), base, dest)
    }

    /**
     * The combined multiplicative fee factor for the current pair.
     * Exposed so the UI can render a hint (e.g. badge on the side toggle)
     * when at least one fee is active for the current currencies.
     */
    private val totalStack = object : MediatorLiveData<BigDecimal>() {
        var feeList: List<Fee>? = null
        var base: Currency? = null
        var dest: Currency? = null

        init {
            addSource(fees) { feeList = it; update() }
            addSource(currentBaseCurrency) { base = it; update() }
            addSource(currentDestinationCurrency) { dest = it; update() }
        }

        private fun update() {
            this.value = computeTotalStack(feeList.orEmpty(), base, dest)
        }
    }

    /**
     * The additional "true cost" on the input side when [FeeSide.ORIGINAL]
     * is active and at least one fee applies: `input * totalStack`. `null`
     * when the total stack is trivial (== 1) or the side is [FeeSide.CONVERTED].
     */
    private val trueCost = object : MediatorLiveData<BigDecimal?>() {
        var amount: BigDecimal = BigDecimal.ZERO
        var stack: BigDecimal = BigDecimal.ONE
        var side: FeeSide = FeeSide.ORIGINAL
        var feeList: List<Fee> = emptyList()

        init {
            addSource(getCurrentBaseValueAsNumber()) { amount = it ?: BigDecimal.ZERO; update() }
            addSource(totalStack) { stack = it ?: BigDecimal.ONE; update() }
            addSource(feeSide) { side = it ?: FeeSide.ORIGINAL; update() }
            addSource(fees) { feeList = it.orEmpty(); update() }
        }

        private fun update() {
            this.value = when {
                side != FeeSide.ORIGINAL -> null
                feeList.isEmpty() -> null
                stack.compareTo(BigDecimal.ONE) == 0 -> null
                else -> amount.multiply(stack, MathContext.DECIMAL128)
            }
        }
    }

    /**
     * See [trueCost].
     */
    internal fun getTrueCost(): LiveData<BigDecimal?> = trueCost

    /**
     * The undiscounted (fair) destination amount when [FeeSide.CONVERTED]
     * is active and at least one fee applies: `result * totalStack`. `null`
     * when the total stack is trivial (== 1) or the side is [FeeSide.ORIGINAL].
     */
    private val originalValue = object : MediatorLiveData<BigDecimal?>() {
        var resultVal: BigDecimal = BigDecimal.ZERO
        var stack: BigDecimal = BigDecimal.ONE
        var side: FeeSide = FeeSide.ORIGINAL
        var feeList: List<Fee> = emptyList()

        init {
            addSource(getResultAsNumber()) { resultVal = it ?: BigDecimal.ZERO; update() }
            addSource(totalStack) { stack = it ?: BigDecimal.ONE; update() }
            addSource(feeSide) { side = it ?: FeeSide.ORIGINAL; update() }
            addSource(fees) { feeList = it.orEmpty(); update() }
        }

        private fun update() {
            this.value = when {
                side != FeeSide.CONVERTED -> null
                feeList.isEmpty() -> null
                stack.compareTo(BigDecimal.ONE) == 0 -> null
                else -> resultVal.multiply(stack, MathContext.DECIMAL128)
            }
        }
    }

    /**
     * See [originalValue].
     */
    internal fun getOriginalValue(): LiveData<BigDecimal?> = originalValue

    /**
     * The combined multiplicative fee factor for the current pair.
     */
    internal fun getTotalStack(): LiveData<BigDecimal> = totalStack

    /**
     * the total destination value, as BigDecimal (internal is string)
     */
    internal fun getResultAsNumber(): LiveData<BigDecimal> {
        return result.map {
            it?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        }
    }

    /**
     * the nicely formatted, total destination value including the currency symbol at the right position.
     */
    internal fun getResultFormatted(): LiveData<SpannableStringBuilder> {
        return object : MediatorLiveData<SpannableStringBuilder>() {
            var resultText: String? = null
            var currency: Currency? = null
            var places: Int = 2

            init {
                addSource(result) { resultText = it; update() }
                addSource(currentDestinationCurrency) { currency = it; update() }
                addSource(decimalPlaces) { places = it; update() }
            }

            fun update() {
                val number = (resultText?.toHumanReadableNumber(
                    app,
                    trim = true,
                    decimalPlaces = places
                ) ?: "0")
                val symbol = currency?.symbol()

                this.value =
                    if (hasAppendedCurrencySymbol(app))
                        SpannableStringBuilder() // 123 $
                            .bold { append(number) }
                            .append(if (symbol != null) " $symbol" else "")
                    else
                        SpannableStringBuilder() // $ 123
                            .append(if (symbol != null) "$symbol " else "")
                            .bold { append(number) }
            }
        }
    }

    /**
     * the current decimal-places preference, for output-side rounding.
     */
    internal fun getDecimalPlaces(): LiveData<Int> {
        return decimalPlaces
    }

    /*
     * user input **********************************************************************************
     */

    internal fun addNumber(value: String) {
        // in calculation mode: add to upper row
        if (isInCalculationMode()) {
            // last input was "0"
            if (currentCalculationValueText.value!!.split(" ").last().trim() == "0") {
                // replace that "0" with any other number
                if (value != "0" && value != "00" && value != "000")
                    currentCalculationValueText.value =
                        currentCalculationValueText.value?.trim()?.dropLast(1)?.plus(value)
            }
            // last input was an operator: replace "00" and "000" with "0"
            else if (currentCalculationValueText.value!!.split(" ").last().isEmpty()
                && (value == "00" || value == "000"))
                currentCalculationValueText.value += 0
            else
                currentCalculationValueText.value += value
        }
        // else: add to lower row
        else {
            currentBaseValueText.value =
                if (currentBaseValueText.value == "0") {
                    if (value == "00" || value == "000") "0"
                    else value
                } else currentBaseValueText.value.plus(value)
        }
    }

    internal fun paste(value: Number) {
        // clear base value (but not calculation row!)
        currentBaseValueText.value = "0"
        // paste
        value.toString().forEach {
            addNumber(it.toString())
        }
    }

    internal fun addPercent() {
        if (!isInCalculationMode())
            currentCalculationValueText.value = currentBaseValueText.value
        val current = currentCalculationValueText.value?.trim() ?: return
        if (current.isNotEmpty() && (current.last().isDigit() || current.last() == '.'))
            currentCalculationValueText.value =
                if (current.last() == '.') current.dropLast(1) + "%" else current + "%"
    }

    internal fun addDecimal() {
        // in calculation mode: add to upper row
        if (isInCalculationMode()) {
            if (!currentCalculationValueText.value!!.substringAfterLast(" ").contains(".")) {
                // if last char is not a number: add 0
                if (currentCalculationValueText.value!!.trim().last().isDigit().not())
                    currentCalculationValueText.value += "0"
                currentCalculationValueText.value += "."
            }
        }
        // add to lower row
        else
            if (!currentBaseValueText.value!!.contains("."))
                currentBaseValueText.value += "."
    }

    internal fun delete() {
        // in calculation mode: delete from upper row
        if (isInCalculationMode()) {
            currentCalculationValueText.value = currentCalculationValueText.value!!.trim().dropLast(1)
            // if last char is a number: trim!
            if (currentCalculationValueText.value!!.trim().last().isDigit())
                currentCalculationValueText.value = currentCalculationValueText.value!!.trim()
            // if only a number is left without an operator, delete it completely
            if (!currentCalculationValueText.value!!.contains("[\\u002B\\u2212\\u00D7\\u00F7]".toRegex()))
                currentCalculationValueText.value = null
        }
        // delete from lower row
        else {
            if (currentBaseValueText.value!!.length > 1)
                currentBaseValueText.value = currentBaseValueText.value?.dropLast(1)
            else
                clear()
        }
    }

    internal fun clear() {
        currentBaseValueText.value = "0"
        currentCalculationValueText.value = null
    }

    internal fun addition() {
        addOperator("\u002B")
    }

    internal fun subtraction() {
        addOperator("\u2212")
    }

    internal fun multiplication() {
        addOperator("\u00D7")
    }

    internal fun division() {
        addOperator("\u00F7")
    }

    private fun addOperator(operator: String) {

        fun Char.isOperator(): Boolean {
            return when (this) {
                '\u002B' -> true // +
                '\u2212' -> true // -
                '\u00D7' -> true // *
                '\u00F7' -> true // /
                else -> false
            }
        }

        // in calculation mode & already has operator at end position: exchange it!
        if (isInCalculationMode() && currentCalculationValueText.value!!.trim().last().isOperator())
            currentCalculationValueText.value = currentCalculationValueText.value?.trim()?.dropLast(1) + "$operator "
        // in calculation mode & last position is '.' -> remove it and add operator
        else if (isInCalculationMode() && currentCalculationValueText.value!!.trim().last() == '.')
            currentCalculationValueText.value = currentCalculationValueText.value?.trim()?.dropLast(1) + " $operator "
        else {
            // switch to calculation mode if necessary
            if (!isInCalculationMode())
                currentCalculationValueText.value = currentBaseValueText.value
            // add operator
            currentCalculationValueText.value = currentCalculationValueText.value?.trim().plus(" $operator ")
        }
    }

    /*
     * selected currencies *************************************************************************
     */

    internal fun setBaseCurrency(currency: Currency) {
        db.saveLastUsedRates(
            currency,
            currentDestinationCurrency.value
        )
    }

    internal fun setDestinationCurrency(currency: Currency) {
        db.saveLastUsedRates(
            currentBaseCurrency.value,
            currency
        )
    }

    internal fun getBaseCurrency(): LiveData<Currency?> {
        return  currentBaseCurrency
    }

    internal fun getDestinationCurrency(): LiveData<Currency?> {
        return  currentDestinationCurrency
    }

    /*
     * historical rates
     */

    internal fun setHistoricalDate(date: LocalDate?) {
        // check if previous date was "latest" or historical
        val wasLatestActive = db.getHistoricalDate() == null
        // save selected historical date to db
        db.setHistoricalDate(date)
        // refresh, if new date != cached date or if last state was "latest"
        if (date != db.getDate() || wasLatestActive)
            forceUpdateExchangeRate()
    }

    internal fun getHistoricalDate(): LocalDate? {
        return db.getHistoricalDate()
    }

    internal fun getHistoricalLiveDate(): LiveData<LocalDate?> {
        return db.getHistoricalLiveDate()
    }


    /*
     * helpers =====================================================================================
     */

    private fun isInCalculationMode(): Boolean {
        return currentCalculationValueText.value.isNullOrBlank().not()
    }

}
