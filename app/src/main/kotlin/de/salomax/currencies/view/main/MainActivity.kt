package de.salomax.currencies.view.main

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.os.Bundle
import android.view.ContextMenu
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.DatePicker
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModelProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.window.layout.FoldingFeature
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import de.salomax.currencies.R
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Rate
import de.salomax.currencies.repository.Database
import de.salomax.currencies.model.FeeSide
import de.salomax.currencies.util.getDecimalSeparator
import de.salomax.currencies.util.ltrIsolate
import de.salomax.currencies.util.stripRtlMark
import de.salomax.currencies.util.stripTimePattern
import de.salomax.currencies.util.toHumanReadableNumber
import de.salomax.currencies.util.toNumber
import de.salomax.currencies.view.BaseActivity
import de.salomax.currencies.view.main.spinner.SearchableSpinner
import de.salomax.currencies.view.preference.PreferenceActivity
import de.salomax.currencies.view.preference.showProviderPickerDialog
import de.salomax.currencies.view.timeline.TimelineActivity
import de.salomax.currencies.viewmodel.main.MainViewModel
import de.salomax.currencies.viewmodel.main.Operator
import de.salomax.currencies.viewmodel.preference.PreferenceViewModel
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val HISTORICAL_MIN_YEAR = 2010
private const val STALE_RATES_DAYS = 3L
private const val MAX_ERROR_TEXT_LINES = 20

// context-menu item ids for the from/to text views
private const val CTX_MENU_COPY_FROM = 0
private const val CTX_MENU_PASTE_FROM = 1
private const val CTX_MENU_COPY_TO = 2

// fee badge / true-cost formatting
private const val PERCENT_MULTIPLIER = 100
private const val FEE_BADGE_DECIMAL_PLACES = 2
private const val AMOUNT_DECIMAL_PLACES = 2

class MainActivity : BaseActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var preferenceModel: PreferenceViewModel

    private var hapticEnabled = false
    // Cached date pattern so the frequently-fired `observeExchangeRates`
    // handler doesn't hit SharedPreferences on the main thread on every rate
    // emission. Kept in sync via the `getDateFormat()` LiveData below.
    private var dateFormatPattern: String = "dd/MM/yy HH:mm"

    private lateinit var refreshIndicator: LinearProgressIndicator
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private var menuItemRefresh: MenuItem? = null

    private lateinit var tvCalculations: TextView
    private lateinit var tvFrom: TextView
    private lateinit var tvTo: TextView
    private lateinit var spinnerFrom: SearchableSpinner
    private lateinit var spinnerTo: SearchableSpinner
    private lateinit var tvInfoConversion: TextView
    private lateinit var tvInfoDate: TextView
    private lateinit var tvTrueCost: TextView
    private lateinit var tvOriginalValue: TextView
    private lateinit var tvFeeBadge: TextView
    private lateinit var btnFeeSide: AppCompatImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // general layout
        setContentView(R.layout.activity_main)
        title = null

        // model
        this.viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        this.preferenceModel = ViewModelProvider(this)[PreferenceViewModel::class.java]

        // views
        this.refreshIndicator = findViewById(R.id.refreshIndicator)
        this.swipeRefresh = findViewById(R.id.swipeRefresh)
        this.tvCalculations = findViewById(R.id.textCalculations)
        this.tvFrom = findViewById(R.id.textFrom)
        this.tvTo = findViewById(R.id.textTo)
        this.spinnerFrom = findViewById(R.id.spinnerFrom)
        this.spinnerTo = findViewById(R.id.spinnerTo)
        this.tvInfoConversion = findViewById(R.id.textInfoConversion)
        this.tvInfoDate = findViewById(R.id.textInfoDate)
        this.tvTrueCost = findViewById(R.id.textTrueCost)
        this.tvOriginalValue = findViewById(R.id.textOriginalValue)
        this.tvFeeBadge = findViewById(R.id.textFeeBadge)
        this.btnFeeSide = findViewById(R.id.btn_fee_side)

        // swipe-to-refresh: color scheme (not accessible in xml)
        swipeRefresh.setColorSchemeColors(MaterialColors.getColor(this, R.attr.colorOnPrimary, null))
        swipeRefresh.setProgressBackgroundColorSchemeColor(MaterialColors.getColor(this, R.attr.colorPrimary, null))

        // listeners & stuff
        setListeners()

        // heavy lifting
        observe()

        // foldable devices
        prepareFoldableLayoutChanges()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        this.menuItemRefresh = menu.findItem(R.id.refresh)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> { startActivity(Intent(this, PreferenceActivity::class.java)); true }
            R.id.fees -> { startActivity(PreferenceActivity.feesIntent(this)); true }
            R.id.change_api -> { showApiProviderPicker(); true }
            R.id.refresh -> { viewModel.forceUpdateExchangeRate(); true }
            R.id.share -> { shareCurrentConversion(); true }
            R.id.timeline -> openTimelineActivity()
            R.id.quick_conversions -> { openQuickConversionsDialog(); true }
            R.id.date_picker -> { openHistoricalDatePicker(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showApiProviderPicker() {
        showProviderPickerDialog(
            context = this,
            current = Database(this).getApiProvider(),
        ) { provider -> preferenceModel.setApiProvider(provider) }
    }

    private fun shareCurrentConversion() {
        val conversion = tvInfoConversion.text?.toString().orEmpty()
        if (conversion.isBlank()) return
        val footer = buildShareFooter(viewModel.getExchangeRates().value)
        val text = if (footer != null) "$conversion\n-- $footer" else conversion
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, null))
    }

    private fun buildShareFooter(rates: ExchangeRates?): String? {
        if (rates == null) return null
        val providerName = rates.provider?.getName() ?: return null
        val dateString = formatRatesTimestamp(rates.date, rates.time) ?: return null
        return getString(R.string.share_footer, providerName, dateString)
    }

    // Combine [date] and optional [time] into a single formatted string using
    // the user's configured pattern. When [time] is null the time portion is
    // stripped from the pattern first so users on "date-only" don't see a
    // trailing "00:00". RTL marks injected by some locale formatters are
    // stripped so a right-side timestamp stays flush with the label.
    private fun formatRatesTimestamp(date: LocalDate?, time: LocalTime?): String? {
        if (date == null) return null
        val pattern = if (time != null) dateFormatPattern else stripTimePattern(dateFormatPattern)
        val temporal = if (time != null) date.atTime(time) else date
        return DateTimeFormatter.ofPattern(pattern).format(temporal).stripRtlMark()
    }

    private fun openQuickConversionsDialog() {
        QuickConversionsDialog().show(supportFragmentManager, null)
    }

    private fun openTimelineActivity(): Boolean {
        val from = viewModel.getBaseCurrency().value ?: return false
        val to = viewModel.getDestinationCurrency().value ?: return false
        startActivity(TimelineActivity.newIntent(this, from, to))
        return true
    }

    private fun openHistoricalDatePicker() {
        val startDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            .apply { this.set(HISTORICAL_MIN_YEAR, Calendar.JANUARY, 1) }
            .timeInMillis
        val layout = layoutInflater.inflate(R.layout.main_dialog_historical_rates, null)
        val toggle: SwitchMaterial = layout.findViewById(R.id.toggle)
        val datePicker: DatePicker = layout.findViewById(R.id.date_picker)
        val border: View = layout.findViewById(R.id.border)
        val historicalDate = viewModel.getHistoricalDate()

        fun showDatePicker(show: Boolean) {
            datePicker.visibility = if (show) View.VISIBLE else View.GONE
            border.visibility = if (show) View.VISIBLE else View.GONE
        }
        showDatePicker(historicalDate != null)
        datePicker.apply {
            minDate = startDate
            maxDate = Calendar.getInstance().timeInMillis
            firstDayOfWeek = Calendar.getInstance().firstDayOfWeek
            historicalDate?.let { updateDate(it.year, it.monthValue - 1, it.dayOfMonth) }
        }
        toggle.apply {
            setOnCheckedChangeListener { _, enabled -> showDatePicker(enabled) }
            isChecked = historicalDate != null
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.historical_rates_dialog_title)
            .setView(layout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.setHistoricalDate(
                    if (toggle.isChecked) LocalDate.of(
                        datePicker.year, datePicker.month + 1, datePicker.dayOfMonth
                    ) else null
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .show()
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        when (v.id) {
            R.id.textFrom -> {
                menu.add(0, CTX_MENU_COPY_FROM, 0, android.R.string.copy)
                val paste = menu.add(0, CTX_MENU_PASTE_FROM, 0, android.R.string.paste)
                // only show "paste" when applicable
                paste.isVisible = clipboardHasNumber()
            }
            R.id.textTo -> {
                menu.add(0, CTX_MENU_COPY_TO, 0, android.R.string.copy)
            }
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            CTX_MENU_COPY_FROM -> copyToClipboard(findViewById<TextView>(R.id.textFrom).text.toString())
            CTX_MENU_PASTE_FROM -> clipboardNumber()?.let { viewModel.paste(it) }
            CTX_MENU_COPY_TO -> copyToClipboard(findViewById<TextView>(R.id.textTo).text.toString())
        }
        return true
    }

    private fun clipboardManager(): ClipboardManager =
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private fun clipboardHasNumber(): Boolean {
        val clipboard = clipboardManager()
        return clipboard.hasPrimaryClip()
            && clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
            && clipboardNumber() != null
    }

    private fun clipboardNumber(): Number? =
        clipboardManager().primaryClip?.getItemAt(0)?.text?.toNumber()

    private fun setListeners() {
        // long click on delete
        arrayOf<View>(findViewById(R.id.keypad), findViewById(R.id.keypad_extended)).forEach {
            it.findViewById<AppCompatImageButton>(R.id.btn_delete).setOnLongClickListener {
                viewModel.clear()
                true
            }
        }

        // long click on input "from"
        registerForContextMenu(findViewById<LinearLayout>(R.id.textFrom))
        // long click on input "to"
        registerForContextMenu(findViewById<LinearLayout>(R.id.textTo))

        // spinners: listen for changes
        spinnerFrom.onItemSelectedListener = rateSpinnerListener(viewModel::setBaseCurrency)
        spinnerTo.onItemSelectedListener = rateSpinnerListener(viewModel::setDestinationCurrency)

        // swipe to refresh
        swipeRefresh.setOnRefreshListener {
            // update
            viewModel.forceUpdateExchangeRate()
            swipeRefresh.isRefreshing = false
        }

        // fee-side toggle
        btnFeeSide.setOnClickListener {
            haptic(it)
            val current = viewModel.getFeeSide().value ?: FeeSide.ORIGINAL
            viewModel.setFeeSide(current.toggled())
        }
        btnFeeSide.setOnLongClickListener { openFeesSettings(it) }

        // long-press on the main swap arrow also opens the Fees settings,
        // mirroring the swap arrow inside the quick-conversions dialog.
        findViewById<View>(R.id.btn_toggle).setOnLongClickListener { openFeesSettings(it) }
    }

    private fun openFeesSettings(source: View): Boolean {
        haptic(source)
        startActivity(PreferenceActivity.feesIntent(this))
        return true
    }

    // Forward the picked rate's currency to [onCurrencySelected]; both spinners
    // share this exact shape (guard against empty adapter / position -1 first).
    private fun rateSpinnerListener(onCurrencySelected: (Currency) -> Unit) =
        object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                if (position == -1 || parent?.adapter?.isEmpty == true) return
                (parent?.adapter?.getItem(position) as Rate?)?.let { onCurrencySelected(it.currency) }
            }
        }

    private fun copyToClipboard(copyText: String) {
        clipboardManager().setPrimaryClip(ClipData.newPlainText(null, copyText))
        val message = HtmlCompat.fromHtml(
            getString(R.string.copied_to_clipboard, copyText),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        Snackbar.make(this, findViewById(R.id.snackbar_top_position), message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(MaterialColors.getColor(this, R.attr.colorPrimary, null))
            .setTextColor(MaterialColors.getColor(this, R.attr.colorOnPrimary, null))
            .show()
    }

    private fun observe() {
        Database(this).getDateFormat().observe(this) { pattern ->
            dateFormatPattern = pattern
            // Re-render the rates footer so a pattern change from Settings
            // takes effect immediately without waiting for the next refresh.
            observeExchangeRates(viewModel.getExchangeRates().value)
        }
        viewModel.ratesInformationFooter.observe(this) { tvInfoConversion.text = it }
        viewModel.getExchangeRates().observe(this) { observeExchangeRates(it) }
        viewModel.getError().observe(this) { showErrorSnackbar(it) }
        viewModel.isUpdating().observe(this) { isRefreshing ->
            refreshIndicator.visibility = if (isRefreshing) View.VISIBLE else View.GONE
            swipeRefresh.isEnabled = isRefreshing.not()
            menuItemRefresh?.isEnabled = isRefreshing.not()
        }
        viewModel.getCurrentBaseValueFormatted().observe(this) { tvFrom.text = it }
        viewModel.getResultFormatted().observe(this) { tvTo.text = it }
        viewModel.getCalculationInputFormatted().observe(this) { tvCalculations.text = it }
        viewModel.getBaseCurrency().observe(this) { observeBaseCurrency(it) }
        viewModel.getDestinationCurrency().observe(this) { observeDestinationCurrency(it) }
        viewModel.getCurrentBaseValueAsNumber().observe(this) { spinnerTo.setCurrentSum(it) }
        viewModel.getResultAsNumber().observe(this) { spinnerFrom.setCurrentSum(it) }
        viewModel.isExtendedKeypadEnabled.observe(this) { observeKeypadState(it) }
        viewModel.isHapticFeedbackEnabled.observe(this) { hapticEnabled = it }
        viewModel.getFeeSide().observe(this) { observeFeeSide(it) }
        viewModel.getTrueCost().observe(this) { observeTrueCost(it) }
        viewModel.getOriginalValue().observe(this) { observeOriginalValue(it) }
        viewModel.getTotalStack().observe(this) { observeTotalStack(it) }
    }

    private fun observeFeeSide(side: FeeSide?) {
        val effective = side ?: FeeSide.ORIGINAL
        btnFeeSide.setImageResource(
            if (effective == FeeSide.CONVERTED) R.drawable.ic_fee_side_converted
            else R.drawable.ic_fee_side_original
        )
    }

    private fun observeTotalStack(stack: BigDecimal?) {
        val effective = stack ?: BigDecimal.ONE
        if (effective.compareTo(BigDecimal.ONE) == 0) {
            tvFeeBadge.visibility = View.GONE
            btnFeeSide.visibility = View.GONE
            return
        }
        btnFeeSide.visibility = View.VISIBLE
        val deltaPercent = effective
            .subtract(BigDecimal.ONE)
            .multiply(BigDecimal(PERCENT_MULTIPLIER))
            .setScale(FEE_BADGE_DECIMAL_PLACES, java.math.RoundingMode.HALF_EVEN)
        tvFeeBadge.text = deltaPercent.toHumanReadableNumber(
            this,
            showPositiveSign = true,
            suffix = "%",
            trim = true,
        )
        tvFeeBadge.visibility = View.VISIBLE
    }

    private fun observeTrueCost(value: BigDecimal?) {
        renderFeeAmount(tvTrueCost, R.string.fee_true_cost_prefix, value, viewModel.getBaseCurrency().value)
    }

    private fun observeOriginalValue(value: BigDecimal?) {
        renderFeeAmount(tvOriginalValue, R.string.fee_original_value_prefix, value, viewModel.getDestinationCurrency().value)
    }

    private fun renderFeeAmount(target: TextView, prefixRes: Int, value: BigDecimal?, currency: Currency?) {
        if (value == null) {
            target.visibility = View.GONE
            return
        }
        val amount = value.toHumanReadableNumber(this, decimalPlaces = AMOUNT_DECIMAL_PLACES)
        target.text = getString(prefixRes) + ltrIsolate("$amount ${currency?.iso4217Alpha().orEmpty()}")
        target.visibility = View.VISIBLE
    }

    private fun observeExchangeRates(rates: ExchangeRates?) {
        rates?.let {
            val date = it.date
            val dateString = formatRatesTimestamp(date, it.time)
            val providerString = it.provider?.getName()
            tvInfoDate.text =
                if (dateString != null && providerString != null)
                    HtmlCompat.fromHtml(
                        getString(
                            if (viewModel.getHistoricalDate() != null)
                                R.string.info_date_historical
                            else
                                R.string.info_date_latest,
                            dateString,
                            providerString
                        ),
                        HtmlCompat.FROM_HTML_MODE_LEGACY
                    )
                else null
            val isStaleOrHistorical = date?.isBefore(LocalDate.now().minusDays(STALE_RATES_DAYS)) == true
                || viewModel.getHistoricalDate() != null
            val infoColor = if (isStaleOrHistorical)
                MaterialColors.getColor(this, R.attr.colorError, null)
            else
                getTextColorSecondary()
            listOf(tvInfoDate, tvInfoConversion).forEach { tv -> tv.setTextColor(infoColor) }
            findViewById<ImageView>(R.id.iconHistorical).visibility =
                if (viewModel.getHistoricalDate() != null) View.VISIBLE else View.GONE
        }
        spinnerFrom.setRates(rates?.rates)
        spinnerTo.setRates(rates?.rates)
    }

    private fun showErrorSnackbar(message: String?) {
        message ?: return
        Snackbar.make(
            this,
            findViewById(R.id.snackbar_top_position),
            HtmlCompat.fromHtml(message, HtmlCompat.FROM_HTML_MODE_LEGACY),
            Snackbar.LENGTH_INDEFINITE
        )
            .setBackgroundTint(MaterialColors.getColor(this, R.attr.colorError, null))
            .setTextColor(MaterialColors.getColor(this, R.attr.colorOnError, null))
            .setActionTextColor(MaterialColors.getColor(this, R.attr.colorOnError, null))
            .setAction(android.R.string.ok) { }
            .setTextMaxLines(MAX_ERROR_TEXT_LINES)
            .show()
    }

    private fun observeBaseCurrency(currency: Currency?) {
        spinnerFrom.setSelection(currency)
        currency ?: return
        viewModel.getExchangeRates().value?.rates?.find { it.currency == currency }?.value
            ?.let { spinnerTo.setCurrentRate(Rate(currency, it)) }
    }

    private fun observeDestinationCurrency(currency: Currency?) {
        spinnerTo.setSelection(currency)
        currency ?: return
        viewModel.getExchangeRates().value?.rates?.find { it.currency == currency }?.value
            ?.let { spinnerFrom.setCurrentRate(Rate(currency, it)) }
    }

    private fun observeKeypadState(extendedEnabled: Boolean) {
        val keypadRegular = findViewById<View>(R.id.keypad)
        val keypadExtended = findViewById<View>(R.id.keypad_extended)
        keypadRegular.visibility = if (extendedEnabled) View.GONE else View.VISIBLE
        keypadExtended.visibility = if (extendedEnabled) View.VISIBLE else View.GONE
        val separator = getDecimalSeparator(this)
        keypadExtended.findViewById<TextView>(R.id.btn_decimal).text = separator
        keypadRegular.findViewById<TextView>(R.id.btn_decimal).text = separator
    }

    private fun haptic(view: View) {
        if (hapticEnabled)
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /*
     * keyboard: number input
     */
    fun numberEvent(view: View) {
        haptic(view)
        viewModel.addNumber((view as AppCompatButton).text.toString())
    }

    /*
     * keyboard: add decimal point
     */
    fun decimalEvent(view: View) {
        haptic(view)
        viewModel.addDecimal()
    }

    /*
     * keyboard: delete
     */
    fun deleteEvent(view: View) {
        haptic(view)
        viewModel.delete()
    }

    /*
     * keyboard: percentage
     */
    fun percentEvent(view: View) {
        haptic(view)
        viewModel.addPercent()
    }

    /*
     * keyboard: do some calculations
     */
    fun calculationEvent(view: View) {
        haptic(view)
        Operator.fromDisplay((view as AppCompatButton).text.toString())
            ?.apply?.invoke(viewModel)
    }

    // capture hardware keyboard input
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // IMPORTANT: can't work with simple keyCodes here, as depending on the keyboard
        // configuration, wrong values will be returned (e.g. KEYCODE_8 instead of KEYCODE_PLUS).
        val key = event?.keyCharacterMap?.get(keyCode, event.metaState)?.let { Char(it) }
        return handleCharKey(key) || handleControlKey(keyCode)
    }

    private fun handleCharKey(key: Char?): Boolean {
        key ?: return false
        Operator.fromHardware(key)?.let { it.apply(viewModel); return true }
        when {
            key.isDigit() -> viewModel.addNumber(key.toString())
            key == '.' || key == ',' -> viewModel.addDecimal()
            else -> return false
        }
        return true
    }

    private fun handleControlKey(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> viewModel.delete()
            KeyEvent.KEYCODE_BACK -> super.onBackPressedDispatcher.onBackPressed()
            else -> return false
        }
        return true
    }

    /*
     * swap currencies
     */
    fun toggleEvent(@Suppress("UNUSED_PARAMETER") view: View) {
        val from = spinnerFrom.selectedItemPosition
        val to = spinnerTo.selectedItemPosition
        spinnerFrom.setSelection(to)
        spinnerTo.setSelection(from)
    }

    private fun prepareFoldableLayoutChanges() {
        observeFoldingFeature { feature ->
            val root = findViewById<LinearLayout>(R.id.main_root)
            root.orientation = when {
                feature.state == FoldingFeature.State.FLAT -> flatOrientation()
                feature.orientation == FoldingFeature.Orientation.VERTICAL -> LinearLayout.HORIZONTAL
                else -> LinearLayout.VERTICAL
            }
        }
    }

    private fun flatOrientation(): Int {
        val cfg = resources.configuration
        return if (cfg.screenHeightDp >= cfg.screenWidthDp) LinearLayout.VERTICAL
        else LinearLayout.HORIZONTAL
    }

}
