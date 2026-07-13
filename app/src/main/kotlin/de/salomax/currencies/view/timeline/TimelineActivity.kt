package de.salomax.currencies.view.timeline

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.core.text.HtmlCompat
import androidx.core.text.bold
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.map
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import de.salomax.currencies.R
import de.salomax.currencies.model.Currency
import de.salomax.currencies.repository.Database
import de.salomax.currencies.util.getLocale
import de.salomax.currencies.util.hasAppendedCurrencySymbol
import de.salomax.currencies.util.toHumanReadableNumber
import de.salomax.currencies.view.BaseActivity
import de.salomax.currencies.viewmodel.timeline.TimelineViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.max

private const val TEXT_WIDTH_PADDING_FACTOR = 1.25

class TimelineActivity : BaseActivity() {

    //
    private lateinit var formatter: DateTimeFormatter
    private lateinit var timelineModel: TimelineViewModel

    // views
    private var menuItemToggle: MenuItem? = null

    private lateinit var refreshIndicator: LinearProgressIndicator
    private lateinit var timelineChart: ComposeView
    private lateinit var textProvider: TextView
    private lateinit var textRateDifference: TextView
    private lateinit var divider: View

    private lateinit var textPastRateDate: TextView
    private lateinit var textPastRateValue: TextView

    private lateinit var textCurrentRateDate: TextView
    private lateinit var textCurrentRateValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        formatter = DateTimeFormatter
            .ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(getLocale(this))

        // general layout
        setContentView(R.layout.activity_timeline)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        // what currencies to convert
        val currencyFrom =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                intent.getSerializableExtra("ARG_FROM", Currency::class.java) ?: Currency.EUR
            else
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("ARG_FROM")?.let { it as Currency } ?: Currency.EUR

        val currencyTo =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                intent.getSerializableExtra("ARG_TO", Currency::class.java) ?: Currency.USD
            else
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("ARG_TO")?.let { it as Currency } ?: Currency.USD

        // model
        this.timelineModel = ViewModelProvider(
            this,
            TimelineViewModel.Factory(this.application, currencyFrom, currencyTo)
        )[TimelineViewModel::class.java]

        // views
        findViews()

        // configure timeline view
        initChartView()
        initStatsView()

        // listeners & stuff
        setListeners()

        // heavy lifting
        observe()

        // foldable devices
        prepareFoldableLayoutChanges()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.timeline, menu)
        menuItemToggle = menu.findItem(R.id.toggle)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.toggle -> {
                timelineModel.toggleCurrencies()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun findViews() {
        this.refreshIndicator = findViewById(R.id.refreshIndicator)
        this.timelineChart = findViewById(R.id.timeline_chart)
        this.textProvider = findViewById(R.id.textProvider)
        this.textRateDifference = findViewById(R.id.text_rate_difference_percent)
        this.divider = findViewById(R.id.divider)

        this.textPastRateDate = findViewById(R.id.text_date_past)
        this.textPastRateValue = findViewById(R.id.text_rate_past)

        this.textCurrentRateDate = findViewById(R.id.text_date_current)
        this.textCurrentRateValue = findViewById(R.id.text_rate_current)
    }

    private fun initChartView() {
        val db = Database(this)
        val entriesLive = timelineModel.getRates().map { rates ->
            rates?.entries?.map { entry -> entry.key to entry.value.value.toFloat() }
        }
        val lineColor = Color(MaterialColors.getColor(this, R.attr.colorPrimary, 0))
        val baselineColor = Color(MaterialColors.getColor(this, android.R.attr.textColorSecondary, 0))
        val axisColor = Color(MaterialColors.getColor(this, android.R.attr.textColorSecondary, 0))
        timelineChart.setContent {
            TimelineChart(
                entriesLive = entriesLive,
                showGridLive = db.isChartGridEnabled(),
                showXAxisLive = db.isChartXAxisLabelEnabled(),
                showYAxisLive = db.isChartYAxisLabelEnabled(),
                highlightExtremesLive = db.isChartHighlightExtremesEnabled(),
                lineColor = lineColor,
                baselineColor = baselineColor,
                axisColor = axisColor,
                dateFormatter = formatter,
                onScrub = { date -> timelineModel.setPastDate(date) },
            )
        }
    }

    private fun initStatsView() {
        val view1 = findViewById<View>(R.id.stats_row_1).findViewById<TextView>(R.id.text)
        val view2 = findViewById<View>(R.id.stats_row_2).findViewById<TextView>(R.id.text)
        val view3 = findViewById<View>(R.id.stats_row_3).findViewById<TextView>(R.id.text)

        // set the title of "avg", "min", "max
        val string1 = getString(R.string.rate_average)
        val string2 = getString(R.string.rate_min)
        val string3 = getString(R.string.rate_max)
        view1.text = string1
        view2.text = string2
        view3.text = string3

        // set the width of "avg", "min", "max" to the same value
        val width1 = view1.paint.measureText(string1)
        val width2 = view2.paint.measureText(string2)
        val width3 = view3.paint.measureText(string3)
        val maxWidth = (max(width1, max(width2, width3)) * TEXT_WIDTH_PADDING_FACTOR).toInt()
        view1.width = maxWidth
        view2.width = maxWidth
        view3.width = maxWidth
    }

    private fun setListeners() {
        findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.toggleButton)
            .addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked)
                    when (checkedId) {
                        R.id.button_week -> timelineModel.setTimePeriod(TimelineViewModel.Period.WEEK)
                        R.id.button_month -> timelineModel.setTimePeriod(TimelineViewModel.Period.MONTH)
                        R.id.button_year -> timelineModel.setTimePeriod(TimelineViewModel.Period.YEAR)
                    }
            }
    }

    @SuppressLint("SetTextI18n")
    private fun observe() {
        timelineModel.getTitle().observe(this) { title = it }
        timelineModel.getError().observe(this) {
            findViewById<TextView>(R.id.error).apply {
                visibility = View.VISIBLE
                text = HtmlCompat.fromHtml(it ?: "", HtmlCompat.FROM_HTML_MODE_LEGACY)
            }
            menuItemToggle?.isEnabled = it == null
        }
        timelineModel.isUpdating().observe(this) { isRefreshing ->
            refreshIndicator.visibility = if (isRefreshing) View.VISIBLE else View.GONE
            menuItemToggle?.isEnabled = isRefreshing.not()
        }
        timelineModel.getProvider().observe(this) {
            textProvider.text = if (it != null)
                HtmlCompat.fromHtml(getString(R.string.data_provider, it), HtmlCompat.FROM_HTML_MODE_LEGACY)
            else null
        }
        timelineModel.getRatesDifferencePercent().observe(this) {
            textRateDifference.text = it?.toHumanReadableNumber(this, 2, true, "%")
            if (it != null)
                textRateDifference.setTextColor(
                    if (it < BigDecimal.ZERO) MaterialColors.getColor(this, R.attr.colorError, null)
                    else getColor(R.color.dollarBill)
                )
        }
        observeRateComparison()
        observeStatistics()
    }

    private fun observeRateComparison() {
        timelineModel.getRatePast().observe(this) {
            val rate = it.first?.value
            if (rate != null) {
                textPastRateDate.text = it.first?.key?.format(formatter)
                textPastRateValue.text = combineValueAndSymbol(rate.value, rate.currency.symbol(), it.second)
                divider.visibility = View.VISIBLE
            } else {
                divider.visibility = View.GONE
            }
        }
        timelineModel.getRateCurrent().observe(this) {
            val rate = it.first?.value
            if (rate != null) {
                textCurrentRateDate.text = it.first?.key?.format(formatter)
                textCurrentRateValue.text = combineValueAndSymbol(rate.value, rate.currency.symbol(), it.second)
            }
        }
    }

    private fun observeStatistics() {
        timelineModel.getRatesAverage().observe(this) {
            populateStat(
                findViewById(R.id.stats_row_1),
                it.first?.currency?.symbol(),
                it.first?.value,
                null,
                it.second
            )
        }
        timelineModel.getRatesMin().observe(this) {
            val rate = it.first
            populateStat(
                findViewById(R.id.stats_row_2), rate?.currency?.symbol(), rate?.value, it.second, it.third
            )
        }
        timelineModel.getRatesMax().observe(this) {
            val rate = it.first
            populateStat(
                findViewById(R.id.stats_row_3), rate?.currency?.symbol(), rate?.value, it.second, it.third
            )
        }
    }

    private fun populateStat(parent: View, symbol: String?, value: BigDecimal?, date: LocalDate?, places: Int = 3) {
        // hide entire row when there's no data
        parent.visibility = if (symbol == null) View.GONE else View.VISIBLE
        // hide dotted line when there's no date
        parent.findViewById<View>(R.id.dotted_line).visibility = if (date == null) View.GONE else View.VISIBLE
        if (value != null)
            parent.findViewById<TextView>(R.id.text2).text = combineValueAndSymbol(value, symbol, places)
        parent.findViewById<TextView>(R.id.text3).text = date?.format(formatter)
    }

    private fun combineValueAndSymbol(
        value: BigDecimal,
        symbol: String?,
        decimalPlaces: Int
    ): SpannableStringBuilder {
        return if (hasAppendedCurrencySymbol(this))
            SpannableStringBuilder()
                .bold {
                    append(
                        value.toHumanReadableNumber(
                            this@TimelineActivity,
                            decimalPlaces = decimalPlaces
                        )
                    )
                }
                .append(" " + (symbol ?: ""))
        else
            SpannableStringBuilder()
                .append((symbol ?: "") + " ")
                .bold {
                    append(
                        value.toHumanReadableNumber(
                            this@TimelineActivity,
                            decimalPlaces = decimalPlaces
                        )
                    )
                }
    }

    private fun prepareFoldableLayoutChanges() {
        lifecycleScope.launch(Dispatchers.Main) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@TimelineActivity)
                    .windowLayoutInfo(this@TimelineActivity)
                    .collect { newLayoutInfo ->
                        newLayoutInfo.displayFeatures.filterIsInstance(FoldingFeature::class.java)
                            .firstOrNull ()?.let { foldingFeature ->
                                val root = findViewById<LinearLayout>(R.id.timeline_root)
                                // portrait
                                if (foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL) {
                                    if (foldingFeature.state == FoldingFeature.State.HALF_OPENED)
                                        root.orientation = LinearLayout.HORIZONTAL
                                    else
                                        root.orientation = LinearLayout.VERTICAL
                                }
                                // landscape
                                else {
                                    val flat = FoldingFeature.State.FLAT
                                    val halfOpen = FoldingFeature.State.HALF_OPENED
                                    if (foldingFeature.state == flat || foldingFeature.state == halfOpen)
                                        root.orientation = LinearLayout.VERTICAL
                                    else
                                        root.orientation = LinearLayout.HORIZONTAL
                                }
                            }
                    }
            }
        }
    }

}
