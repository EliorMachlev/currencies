package de.salomax.currencies.view.timeline

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import androidx.window.layout.FoldingFeature
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import de.salomax.currencies.R
import de.salomax.currencies.model.Currency
import de.salomax.currencies.repository.Database
import de.salomax.currencies.util.hasAppendedCurrencySymbol
import de.salomax.currencies.util.stripTimePattern
import de.salomax.currencies.util.toHumanReadableNumber
import de.salomax.currencies.view.BaseActivity
import de.salomax.currencies.view.preference.GraphOptionsDialog
import de.salomax.currencies.viewmodel.timeline.TimelineViewModel
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val TEXT_WIDTH_PADDING_FACTOR = 1.25
private const val RATE_DIFF_DECIMALS = 2
private const val STAT_DEFAULT_DECIMALS = 3

class TimelineActivity : BaseActivity() {

    companion object {
        const val EXTRA_FROM = "ARG_FROM"
        const val EXTRA_TO = "ARG_TO"

        fun newIntent(context: Context, from: Currency, to: Currency): Intent =
            Intent(context, TimelineActivity::class.java)
                .putExtra(EXTRA_FROM, from)
                .putExtra(EXTRA_TO, to)
    }

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

        formatter = DateTimeFormatter.ofPattern(stripTimePattern(Database(this).getDateFormatBlocking()))

        // general layout
        setContentView(R.layout.activity_timeline)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        // what currencies to convert
        val currencyFrom = readCurrencyExtra(EXTRA_FROM, Currency.EUR)
        val currencyTo = readCurrencyExtra(EXTRA_TO, Currency.USD)

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
            R.id.graph_options -> {
                GraphOptionsDialog().show(supportFragmentManager, null)
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
                dateFormatLive = db.getDateFormat(),
                lineColor = lineColor,
                baselineColor = baselineColor,
                axisColor = axisColor,
                onScrub = { date -> timelineModel.setPastDate(date) },
            )
        }
    }

    private fun initStatsView() {
        val labels = listOf(
            findViewById<View>(R.id.stats_row_1).findViewById<TextView>(R.id.text)
                to getString(R.string.rate_max),
            findViewById<View>(R.id.stats_row_2).findViewById<TextView>(R.id.text)
                to getString(R.string.rate_average),
            findViewById<View>(R.id.stats_row_3).findViewById<TextView>(R.id.text)
                to getString(R.string.rate_min),
        )
        labels.forEach { (view, text) -> view.text = text }
        // equalize label column width across max/avg/min
        val maxWidth = (labels.maxOf { (view, text) -> view.paint.measureText(text) } *
            TEXT_WIDTH_PADDING_FACTOR).toInt()
        labels.forEach { (view, _) -> view.width = maxWidth }
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
            textRateDifference.text = it?.toHumanReadableNumber(this, RATE_DIFF_DECIMALS, true, "%")
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
        timelineModel.getRatesMax().observe(this) {
            val rate = it.first
            populateStat(
                findViewById(R.id.stats_row_1), rate?.currency?.symbol(), rate?.value, it.second, it.third
            )
        }
        timelineModel.getRatesAverage().observe(this) {
            populateStat(
                findViewById(R.id.stats_row_2),
                it.first?.currency?.symbol(),
                it.first?.value,
                null,
                it.second
            )
        }
        timelineModel.getRatesMin().observe(this) {
            val rate = it.first
            populateStat(
                findViewById(R.id.stats_row_3), rate?.currency?.symbol(), rate?.value, it.second, it.third
            )
        }
    }

    private fun populateStat(parent: View, symbol: String?, value: BigDecimal?, date: LocalDate?, places: Int = STAT_DEFAULT_DECIMALS) {
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
        observeFoldingFeature { feature ->
            val root = findViewById<LinearLayout>(R.id.timeline_root)
            root.orientation = orientationFor(feature)
        }
    }

    private fun orientationFor(feature: FoldingFeature): Int {
        val isPortrait = feature.orientation == FoldingFeature.Orientation.VERTICAL
        val isBent = feature.state == FoldingFeature.State.HALF_OPENED ||
            (!isPortrait && feature.state == FoldingFeature.State.FLAT)
        return if (isPortrait) {
            if (isBent) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        } else {
            if (isBent) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        }
    }

    private fun readCurrencyExtra(key: String, default: Currency): Currency =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent.getSerializableExtra(key, Currency::class.java) ?: default
        else
            @Suppress("DEPRECATION")
            (intent.getSerializableExtra(key) as? Currency) ?: default

}
