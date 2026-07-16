package de.salomax.currencies.view.main

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
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
import de.salomax.currencies.util.stripTimePattern
import de.salomax.currencies.util.toHumanReadableNumber
import de.salomax.currencies.util.toNumber
import de.salomax.currencies.view.BaseActivity
import de.salomax.currencies.view.main.spinner.SearchableSpinner
import de.salomax.currencies.view.preference.PreferenceActivity
import de.salomax.currencies.view.timeline.TimelineActivity
import de.salomax.currencies.viewmodel.main.MainViewModel
import de.salomax.currencies.viewmodel.preference.PreferenceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val HISTORICAL_MIN_YEAR = 2010
private const val STALE_RATES_DAYS = 3L
private const val MAX_ERROR_TEXT_LINES = 20

class MainActivity : BaseActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var preferenceModel: PreferenceViewModel

    private var hapticEnabled = false

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
            R.id.settings -> { startActivity(Intent(this, PreferenceActivity().javaClass)); true }
            R.id.refresh -> { viewModel.forceUpdateExchangeRate(); true }
            R.id.timeline -> openTimelineActivity()
            R.id.date_picker -> { openHistoricalDatePicker(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openTimelineActivity(): Boolean {
        val from = viewModel.getBaseCurrency().value
        val to = viewModel.getDestinationCurrency().value
        if (from == null || to == null) return false
        startActivity(
            Intent(Intent(this, TimelineActivity().javaClass)).apply {
                putExtra("ARG_FROM", from)
                putExtra("ARG_TO", to)
            }
        )
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
                // copy
                menu.add(0, 0, 0, android.R.string.copy)
                // paste
                val paste = menu.add(0, 1, 0, android.R.string.paste)
                // only show "paste" when applicable
                val clipboard: ClipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipboardContent = clipboard.primaryClip?.getItemAt(0)?.text?.toNumber()
                paste.isVisible = (clipboard.hasPrimaryClip()
                        && clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
                        &&  clipboardContent != null)
            }
            R.id.textTo -> {
                menu.add(0, 2, 0, android.R.string.copy)
            }
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            0 -> { // copy "from"
                val copyText = findViewById<TextView>(R.id.textFrom).text
                copyToClipboard(copyText.toString())
            }
            1 -> { // paste "from"
                val clipboard: ClipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                // no need to check if clipboard is filled -> menu item only shown when it is
                clipboard.primaryClip?.getItemAt(0)?.text?.toNumber()?.let {
                    viewModel.paste(it)
                }
            }
            2 -> { // copy "to"
                val copyText = findViewById<TextView>(R.id.textTo).text
                copyToClipboard(copyText.toString())
            }
        }
        return true
    }

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
        spinnerFrom.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (position != -1 && parent?.adapter?.isEmpty != true) {
                    val rate = parent?.adapter?.getItem(position) as Rate?
                    rate?.let { viewModel.setBaseCurrency(it.currency) }
                }
            }
        }
        spinnerTo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (position != -1 && parent?.adapter?.isEmpty != true) {
                    val rate = parent?.adapter?.getItem(position) as Rate?
                    rate?.let { viewModel.setDestinationCurrency(it.currency) }
                }
            }
        }

        // swipe to refresh
        swipeRefresh.setOnRefreshListener {
            // update
            viewModel.forceUpdateExchangeRate()
            swipeRefresh.isRefreshing = false
        }

        // fee-side toggle
        btnFeeSide.setOnClickListener {
            haptic(it)
            val next = when (viewModel.getFeeSide().value ?: FeeSide.ORIGINAL) {
                FeeSide.ORIGINAL -> FeeSide.CONVERTED
                FeeSide.CONVERTED -> FeeSide.ORIGINAL
            }
            viewModel.setFeeSide(next)
        }
    }

    private fun copyToClipboard(copyText: String) {
        // copy
        val clipboard: ClipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(null, copyText))
        // notify
        HtmlCompat.fromHtml(
            getString(R.string.copied_to_clipboard, copyText),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        ).let {
            Snackbar.make(this, findViewById(R.id.snackbar_top_position), it, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(MaterialColors.getColor(this, R.attr.colorPrimary, null))
                .setTextColor(MaterialColors.getColor(this, R.attr.colorOnPrimary, null))
                .show()
        }
    }

    private fun observe() {
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
        val one = BigDecimal.ONE
        val effective = stack ?: one
        if (effective.compareTo(one) == 0) {
            tvFeeBadge.visibility = View.GONE
            return
        }
        val deltaPercent = effective
            .subtract(one)
            .multiply(BigDecimal(100))
            .setScale(2, java.math.RoundingMode.HALF_EVEN)
        val text = deltaPercent.toHumanReadableNumber(
            this,
            showPositiveSign = true,
            suffix = "%",
            trim = true,
        )
        tvFeeBadge.text = text
        tvFeeBadge.visibility = View.VISIBLE
    }

    private fun observeTrueCost(value: BigDecimal?) {
        if (value == null) {
            tvTrueCost.visibility = View.GONE
            return
        }
        val currency = viewModel.getBaseCurrency().value?.iso4217Alpha().orEmpty()
        val amount = value.toHumanReadableNumber(this, decimalPlaces = 2)
        tvTrueCost.text = getString(R.string.fee_true_cost_prefix) + amount + " " + currency
        tvTrueCost.visibility = View.VISIBLE
    }

    private fun observeExchangeRates(rates: ExchangeRates?) {
        rates?.let {
            val date = it.date
            val time = it.time
            val pattern = Database(this).getDateFormatBlocking()
            val effectivePattern = if (time != null) pattern else stripTimePattern(pattern)
            val temporal = when {
                date == null -> null
                time != null -> date.atTime(time)
                else -> date
            }
            val dateString = temporal
                ?.let { DateTimeFormatter.ofPattern(effectivePattern).format(it) }
                ?.replace("\u200F", "")
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

    private fun getTextColorSecondary(): Int {
        val attrs = intArrayOf(android.R.attr.textColorSecondary)
        val a = theme.obtainStyledAttributes(R.style.AppTheme, attrs)
        val color = a.getColor(0, Color.TRANSPARENT)
        a.recycle()
        return color
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
        when ((view as AppCompatButton).text.toString()) {
            "+" -> viewModel.addition()
            "−" -> viewModel.subtraction()
            "×" -> viewModel.multiplication()
            "÷" -> viewModel.division()
        }
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
        when {
            key.isDigit() -> viewModel.addNumber(key.toString())
            key == '.' || key == ',' -> viewModel.addDecimal()
            key == '+' -> viewModel.addition()
            key == '-' -> viewModel.subtraction()
            key == '*' -> viewModel.multiplication()
            key == '/' -> viewModel.division()
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
        lifecycleScope.launch(Dispatchers.Main) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@MainActivity)
                    .windowLayoutInfo(this@MainActivity)
                    .collect { newLayoutInfo ->
                        newLayoutInfo.displayFeatures.filterIsInstance(FoldingFeature::class.java)
                            .firstOrNull()?.let { applyFoldingOrientation(it) }
                    }
            }
        }
    }

    private fun applyFoldingOrientation(foldingFeature: FoldingFeature) {
        val root = findViewById<LinearLayout>(R.id.main_root)
        root.orientation = when {
            foldingFeature.state == FoldingFeature.State.FLAT -> flatOrientation()
            foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL -> LinearLayout.HORIZONTAL
            else -> LinearLayout.VERTICAL
        }
    }

    private fun flatOrientation(): Int {
        val cfg = resources.configuration
        return if (cfg.screenHeightDp >= cfg.screenWidthDp) LinearLayout.VERTICAL
        else LinearLayout.HORIZONTAL
    }

}
