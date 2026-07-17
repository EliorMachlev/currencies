package de.salomax.currencies.view.main

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.lifecycle.ViewModelProvider
import de.salomax.currencies.R
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.FeeSide
import de.salomax.currencies.util.ltrIsolate
import de.salomax.currencies.util.toHumanReadableNumber
import de.salomax.currencies.view.preference.PreferenceActivity
import de.salomax.currencies.viewmodel.main.MainViewModel
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

private val QUICK_AMOUNTS = listOf("1", "5", "10", "20", "50", "100", "500", "1000")

private const val PERCENT_MULTIPLIER = 100
private const val FEE_PERCENT_DECIMAL_PLACES = 2
private const val ROW_DEFAULT_DECIMALS = 2
private const val ROW_SMALL_AMOUNT_DECIMALS = 4
private val ROW_SMALL_AMOUNT_THRESHOLD: BigDecimal = BigDecimal.ONE

class QuickConversionsDialog : AppCompatDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val view = View.inflate(ctx, R.layout.dialog_quick_conversions, null)
        val viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        val flagFrom = view.findViewById<ImageView>(R.id.flag_from)
        val labelFrom = view.findViewById<TextView>(R.id.label_from)
        val flagTo = view.findViewById<ImageView>(R.id.flag_to)
        val labelTo = view.findViewById<TextView>(R.id.label_to)
        val btnSwap = view.findViewById<ImageButton>(R.id.btn_swap)
        val btnFeeSide = view.findViewById<ImageButton>(R.id.btn_fee_side)
        val container = view.findViewById<LinearLayout>(R.id.container_rows)
        val feeInfo = view.findViewById<TextView>(R.id.text_fee_info)

        fun rebuild() {
            val from = viewModel.getBaseCurrency().value
            val to = viewModel.getDestinationCurrency().value
            val rates = viewModel.getExchangeRates().value
            val side = viewModel.getFeeSide().value ?: FeeSide.ORIGINAL
            renderHeader(ctx, flagFrom, labelFrom, from)
            renderHeader(ctx, flagTo, labelTo, to)
            btnFeeSide.setImageResource(
                if (side == FeeSide.CONVERTED) R.drawable.ic_fee_side_converted_horizontal
                else R.drawable.ic_fee_side_original_horizontal
            )
            btnFeeSide.visibility =
                if (hasFeesFor(viewModel, from, to)) View.VISIBLE else View.GONE
            renderRows(container, feeInfo, viewModel, from, to, rates, side)
        }

        viewModel.getBaseCurrency().observe(this) { rebuild() }
        viewModel.getDestinationCurrency().observe(this) { rebuild() }
        viewModel.getExchangeRates().observe(this) { rebuild() }
        viewModel.getFees().observe(this) { rebuild() }
        viewModel.getFeeSide().observe(this) { rebuild() }

        btnSwap.setOnClickListener {
            (activity as? MainActivity)?.toggleEvent(btnSwap)
        }
        btnSwap.setOnLongClickListener { openFeesSettings(ctx) }

        btnFeeSide.setOnClickListener {
            val current = viewModel.getFeeSide().value ?: FeeSide.ORIGINAL
            viewModel.setFeeSide(current.toggled())
        }
        btnFeeSide.setOnLongClickListener { openFeesSettings(ctx) }

        return AlertDialog.Builder(ctx)
            .setTitle(R.string.quick_conversions_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }

    private fun renderHeader(
        ctx: android.content.Context,
        flag: ImageView,
        label: TextView,
        currency: Currency?,
    ) {
        if (currency != null) {
            flag.setImageDrawable(currency.flag(ctx))
            label.text = currency.iso4217Alpha()
        } else {
            flag.setImageDrawable(null)
            label.text = ""
        }
    }

    private fun renderRows(
        container: LinearLayout,
        feeInfo: TextView,
        viewModel: MainViewModel,
        from: Currency?,
        to: Currency?,
        rates: ExchangeRates?,
        side: FeeSide,
    ) {
        container.removeAllViews()
        val ctx = container.context
        val baseRate = rates?.rates?.find { it.currency == from }?.value
        val destRate = rates?.rates?.find { it.currency == to }?.value

        if (from == null || to == null || baseRate == null || destRate == null) {
            val tv = TextView(ctx).apply {
                text = getString(R.string.quick_conversions_no_rates)
                gravity = android.view.Gravity.CENTER
                setPadding(0, resources.getDimensionPixelSize(R.dimen.margin2x), 0, 0)
            }
            container.addView(tv)
            feeInfo.visibility = View.GONE
            return
        }

        val stack = viewModel.feeStackFor(from, to)
        val hasFees = stack.isFeeStack()
        val inflater = android.view.LayoutInflater.from(ctx)

        for (amountStr in QUICK_AMOUNTS) {
            val amt = BigDecimal(amountStr)
            val fair = amt.divide(baseRate, MathContext.DECIMAL128).multiply(destRate)
            val displayed = if (hasFees && side == FeeSide.CONVERTED)
                fair.divide(stack, MathContext.DECIMAL128)
            else
                fair

            val row = inflater.inflate(R.layout.dialog_quick_conversions_row, container, false)
            row.findViewById<TextView>(R.id.text_amount_from).text =
                "$amountStr ${from.iso4217Alpha()}"
            row.findViewById<TextView>(R.id.text_amount_to).text =
                "${displayed.formatForRow(ctx)} ${to.iso4217Alpha()}"

            setFeeAnnotation(
                row.findViewById(R.id.text_true_cost),
                visible = hasFees && side == FeeSide.ORIGINAL,
            ) {
                val actual = amt.multiply(stack, MathContext.DECIMAL128)
                getString(R.string.fee_true_cost_prefix) +
                    ltrIsolate("${actual.formatForRow(ctx)} ${from.iso4217Alpha()}")
            }
            setFeeAnnotation(
                row.findViewById(R.id.text_original_value),
                visible = hasFees && side == FeeSide.CONVERTED,
            ) {
                getString(R.string.fee_original_value_prefix) +
                    ltrIsolate("${fair.formatForRow(ctx)} ${to.iso4217Alpha()}")
            }

            container.addView(row)
        }

        if (hasFees) {
            val percent = (stack.subtract(BigDecimal.ONE))
                .multiply(BigDecimal(PERCENT_MULTIPLIER), MathContext.DECIMAL128)
                .setScale(FEE_PERCENT_DECIMAL_PLACES, RoundingMode.HALF_UP)
            val sign = if (percent.signum() >= 0) "+" else ""
            feeInfo.text = getString(R.string.quick_conversions_fees_applied, "$sign$percent%")
            feeInfo.visibility = View.VISIBLE
        } else {
            feeInfo.visibility = View.GONE
        }
    }

    private fun openFeesSettings(ctx: Context): Boolean {
        startActivity(PreferenceActivity.feesIntent(ctx))
        return true
    }

    private fun hasFeesFor(viewModel: MainViewModel, from: Currency?, to: Currency?): Boolean {
        if (from == null || to == null) return false
        return viewModel.feeStackFor(from, to).isFeeStack()
    }

    private fun BigDecimal.isFeeStack(): Boolean =
        compareTo(BigDecimal.ZERO) != 0 && compareTo(BigDecimal.ONE) != 0

    private inline fun setFeeAnnotation(view: TextView, visible: Boolean, text: () -> String) {
        if (visible) {
            view.text = text()
            view.visibility = View.VISIBLE
        } else {
            view.visibility = View.GONE
        }
    }

    private fun BigDecimal.formatForRow(ctx: Context): String {
        val decimals =
            if (this.abs() >= ROW_SMALL_AMOUNT_THRESHOLD) ROW_DEFAULT_DECIMALS
            else ROW_SMALL_AMOUNT_DECIMALS
        return this.setScale(decimals, RoundingMode.HALF_UP).toHumanReadableNumber(ctx)
    }
}
