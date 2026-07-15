package de.salomax.currencies.view.preference

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.salomax.currencies.R
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.Fee
import de.salomax.currencies.model.FeeSide
import de.salomax.currencies.repository.Database
import de.salomax.currencies.util.toHumanReadableNumber
import java.math.BigDecimal
import java.util.UUID

class FeeManagerFragment : Fragment(R.layout.fragment_fee_manager) {

    private lateinit var db: Database

    private lateinit var toggleFeeSide: MaterialButtonToggleGroup
    private lateinit var listGlobalExchange: LinearLayout
    private lateinit var listGlobalBank: LinearLayout
    private lateinit var listSpecificPair: LinearLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = Database(requireContext())

        activity?.title = getString(R.string.fee_manager_title)

        toggleFeeSide = view.findViewById(R.id.toggleFeeSide)
        listGlobalExchange = view.findViewById(R.id.listGlobalExchange)
        listGlobalBank = view.findViewById(R.id.listGlobalBank)
        listSpecificPair = view.findViewById(R.id.listSpecificPair)

        bindFeeSide(view)
        bindAddButtons(view)

        db.getFees().observe(viewLifecycleOwner) { fees ->
            renderFees(fees)
        }
    }

    private fun bindFeeSide(@Suppress("UNUSED_PARAMETER") view: View) {
        val originalId = R.id.btnSideOriginal
        val convertedId = R.id.btnSideConverted
        val current = db.getFeeSideBlocking()
        toggleFeeSide.check(if (current == FeeSide.ORIGINAL) originalId else convertedId)
        toggleFeeSide.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val side = if (checkedId == convertedId) FeeSide.CONVERTED else FeeSide.ORIGINAL
            db.setFeeSide(side)
        }
    }

    private fun bindAddButtons(view: View) {
        view.findViewById<MaterialButton>(R.id.btnAddGlobalExchange).setOnClickListener {
            showPercentSignDialog(existing = null) { percent, isMarkup ->
                db.addFee(
                    Fee.GlobalExchange(UUID.randomUUID().toString(), percent, isMarkup)
                )
            }
        }
        view.findViewById<MaterialButton>(R.id.btnAddGlobalBank).setOnClickListener {
            showPercentSignDialog(existing = null) { percent, isMarkup ->
                db.addFee(
                    Fee.GlobalBank(UUID.randomUUID().toString(), percent, isMarkup)
                )
            }
        }
        view.findViewById<MaterialButton>(R.id.btnAddSpecificPair).setOnClickListener {
            showSpecificPairDialog(existing = null) { fee -> db.addFee(fee) }
        }
    }

    private fun renderFees(fees: List<Fee>) {
        renderGlobalList(
            container = listGlobalExchange,
            entries = fees.filterIsInstance<Fee.GlobalExchange>(),
        )
        renderGlobalList(
            container = listGlobalBank,
            entries = fees.filterIsInstance<Fee.GlobalBank>(),
        )
        renderSpecificList(fees.filterIsInstance<Fee.SpecificPair>())
    }

    private fun renderGlobalList(container: LinearLayout, entries: List<Fee>) {
        container.removeAllViews()
        entries.forEach { fee ->
            container.addView(
                buildRow(
                    label = formatPercent(fee.percent, fee.isMarkup),
                    onClick = {
                        showPercentSignDialog(existing = fee) { percent, isMarkup ->
                            db.updateFee(rebuildGlobal(fee, percent, isMarkup))
                        }
                    },
                    onDelete = { db.deleteFee(fee.id) },
                )
            )
        }
    }

    private fun renderSpecificList(entries: List<Fee.SpecificPair>) {
        listSpecificPair.removeAllViews()
        entries.forEach { fee ->
            val arrow = if (fee.bothWays) "↔" else "→"
            val label = "${fee.from} $arrow ${fee.to}   ${formatPercent(fee.percent, fee.isMarkup)}"
            listSpecificPair.addView(
                buildRow(
                    label = label,
                    onClick = {
                        showSpecificPairDialog(existing = fee) { updated ->
                            db.updateFee(updated.copy(id = fee.id))
                        }
                    },
                    onDelete = { db.deleteFee(fee.id) },
                )
            )
        }
    }

    private fun rebuildGlobal(original: Fee, percent: BigDecimal, isMarkup: Boolean): Fee {
        return when (original) {
            is Fee.GlobalExchange -> original.copy(percent = percent, isMarkup = isMarkup)
            is Fee.GlobalBank -> original.copy(percent = percent, isMarkup = isMarkup)
            is Fee.SpecificPair -> original.copy(percent = percent, isMarkup = isMarkup)
        }
    }

    private fun buildRow(
        label: String,
        onClick: () -> Unit,
        onDelete: () -> Unit,
    ): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            val padV = resources.getDimensionPixelSize(R.dimen.margin1x)
            setPadding(0, padV, 0, padV)
            isClickable = true
            setOnClickListener { onClick() }
        }
        val labelView = TextView(ctx).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            )
            textDirection = View.TEXT_DIRECTION_LTR
        }
        val deleteBtn = AppCompatImageButton(ctx).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            background = null
            contentDescription = getString(R.string.fee_delete)
            setOnClickListener { onDelete() }
        }
        row.addView(labelView)
        row.addView(deleteBtn)
        return row
    }

    private fun formatPercent(percent: BigDecimal, isMarkup: Boolean): String {
        val sign = if (isMarkup) getString(R.string.fee_edit_sign_positive)
        else getString(R.string.fee_edit_sign_negative)
        return "$sign ${percent.toHumanReadableNumber(requireContext(), suffix = "%")}"
    }

    /**
     * Dialog to enter a percent + sign. Used for GlobalExchange, GlobalBank
     * and the percent/sign portion of SpecificPair.
     */
    private fun showPercentSignDialog(
        existing: Fee?,
        onConfirm: (BigDecimal, Boolean) -> Unit,
    ) {
        val ctx = requireContext()
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val padH = resources.getDimensionPixelSize(R.dimen.margin3x)
            val padV = resources.getDimensionPixelSize(R.dimen.margin2x)
            setPadding(padH, padV, padH, 0)
        }
        val percentInput = EditText(ctx).apply {
            hint = getString(R.string.fee_edit_percent)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (existing != null) setText(existing.percent.toPlainString())
        }
        val signGroup = MaterialButtonToggleGroup(ctx).apply {
            isSingleSelection = true
        }
        val btnPlus = MaterialButton(
            ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            id = View.generateViewId()
            text = getString(R.string.fee_edit_sign_positive)
        }
        val btnMinus = MaterialButton(
            ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            id = View.generateViewId()
            text = getString(R.string.fee_edit_sign_negative)
        }
        signGroup.addView(btnPlus)
        signGroup.addView(btnMinus)
        signGroup.check(if (existing?.isMarkup == false) btnMinus.id else btnPlus.id)

        container.addView(percentInput)
        container.addView(signGroup)

        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.fee_edit_percent)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val percent = percentInput.text.toString().toBigDecimalOrNull()
                    ?: BigDecimal.ZERO
                val isMarkup = signGroup.checkedButtonId != btnMinus.id
                onConfirm(percent.abs(), isMarkup)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSpecificPairDialog(
        existing: Fee.SpecificPair?,
        onConfirm: (Fee.SpecificPair) -> Unit,
    ) {
        val ctx = requireContext()

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val padH = resources.getDimensionPixelSize(R.dimen.margin3x)
            val padV = resources.getDimensionPixelSize(R.dimen.margin2x)
            setPadding(padH, padV, padH, 0)
        }

        val currencyCodes = Currency.entries.map { it.iso4217Alpha() }.sorted()
        val adapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, currencyCodes)

        val fromLabel = TextView(ctx).apply { text = getString(R.string.fee_pair_from) }
        val fromInput = AutoCompleteTextView(ctx).apply {
            setAdapter(adapter)
            threshold = 1
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            if (existing != null) setText(existing.from)
        }
        val toLabel = TextView(ctx).apply { text = getString(R.string.fee_pair_to) }
        val toInput = AutoCompleteTextView(ctx).apply {
            setAdapter(adapter)
            threshold = 1
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            if (existing != null) setText(existing.to)
        }
        val bothWays = CheckBox(ctx).apply {
            text = getString(R.string.fee_pair_both_ways)
            isChecked = existing?.bothWays == true
        }
        val percentLabel = TextView(ctx).apply { text = getString(R.string.fee_edit_percent) }
        val percentInput = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (existing != null) setText(existing.percent.toPlainString())
        }
        val signGroup = MaterialButtonToggleGroup(ctx).apply {
            isSingleSelection = true
        }
        val btnPlus = MaterialButton(
            ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            id = View.generateViewId()
            text = getString(R.string.fee_edit_sign_positive)
        }
        val btnMinus = MaterialButton(
            ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            id = View.generateViewId()
            text = getString(R.string.fee_edit_sign_negative)
        }
        signGroup.addView(btnPlus)
        signGroup.addView(btnMinus)
        signGroup.check(if (existing?.isMarkup == false) btnMinus.id else btnPlus.id)

        listOf(fromLabel, fromInput, toLabel, toInput, bothWays, percentLabel, percentInput, signGroup)
            .forEach { container.addView(it) }

        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.fee_section_specific_pair)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val from = fromInput.text.toString().trim().uppercase()
                val to = toInput.text.toString().trim().uppercase()
                if (from.length != 3 || to.length != 3) return@setPositiveButton
                val percent = percentInput.text.toString().toBigDecimalOrNull()
                    ?: BigDecimal.ZERO
                val isMarkup = signGroup.checkedButtonId != btnMinus.id
                onConfirm(
                    Fee.SpecificPair(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        percent = percent.abs(),
                        isMarkup = isMarkup,
                        from = from,
                        to = to,
                        bothWays = bothWays.isChecked,
                    )
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

}
