package de.salomax.currencies.view.main

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
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
import de.salomax.currencies.util.toHumanReadableNumber
import de.salomax.currencies.view.preference.PreferenceActivity
import de.salomax.currencies.viewmodel.main.MainViewModel
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class QuickConversionsDialog : AppCompatDialogFragment() {

    private val amounts = listOf("1", "5", "10", "20", "50", "100", "500", "1000")

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
        btnSwap.setOnLongClickListener {
            startActivity(
                Intent(ctx, PreferenceActivity::class.java)
                    .putExtra(PreferenceActivity.EXTRA_OPEN_FEES, true)
            )
            true
        }

        btnFeeSide.setOnClickListener {
            val next = when (viewModel.getFeeSide().value ?: FeeSide.ORIGINAL) {
                FeeSide.ORIGINAL -> FeeSide.CONVERTED
                FeeSide.CONVERTED -> FeeSide.ORIGINAL
            }
            viewModel.setFeeSide(next)
        }
        btnFeeSide.setOnLongClickListener {
            startActivity(
                Intent(ctx, PreferenceActivity::class.java)
                    .putExtra(PreferenceActivity.EXTRA_OPEN_FEES, true)
            )
            true
        }

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
        val hasFees = stack.compareTo(BigDecimal.ZERO) != 0 &&
            stack.compareTo(BigDecimal.ONE) != 0
        val inflater = android.view.LayoutInflater.from(ctx)

        for (amountStr in amounts) {
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

            val trueCostView = row.findViewById<TextView>(R.id.text_true_cost)
            if (hasFees && side == FeeSide.ORIGINAL) {
                val actual = amt.multiply(stack, MathContext.DECIMAL128)
                trueCostView.text = getString(
                    R.string.fee_true_cost_prefix
                ) + "${actual.formatForRow(ctx)} ${from.iso4217Alpha()}"
                trueCostView.visibility = View.VISIBLE
            } else {
                trueCostView.visibility = View.GONE
            }

            container.addView(row)
        }

        if (hasFees) {
            val percent = (stack.subtract(BigDecimal.ONE))
                .multiply(BigDecimal(100), MathContext.DECIMAL128)
                .setScale(2, RoundingMode.HALF_UP)
            val sign = if (percent.signum() >= 0) "+" else ""
            feeInfo.text = getString(R.string.quick_conversions_fees_applied, "$sign$percent%")
            feeInfo.visibility = View.VISIBLE
        } else {
            feeInfo.visibility = View.GONE
        }
    }

    private fun BigDecimal.formatForRow(ctx: android.content.Context): String {
        val abs = this.abs()
        val decimals = when {
            abs >= BigDecimal(1000) -> 2
            abs >= BigDecimal(1) -> 2
            else -> 4
        }
        return this.setScale(decimals, RoundingMode.HALF_UP).toHumanReadableNumber(ctx)
    }
}
