package de.salomax.currencies.view.preference

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.salomax.currencies.R
import de.salomax.currencies.model.Fee
import de.salomax.currencies.model.FeeSide
import de.salomax.currencies.repository.Database
import de.salomax.currencies.util.toHumanReadableNumber
import de.salomax.currencies.view.main.spinner.SearchableSpinnerDialog
import java.math.BigDecimal
import java.util.UUID

class FeeManagerFragment : PreferenceFragmentCompat() {

    private lateinit var db: Database

    private lateinit var categoryExchange: PreferenceCategory
    private lateinit var categoryBank: PreferenceCategory
    private lateinit var categoryPair: PreferenceCategory

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        db = Database(requireContext())
        val ctx = preferenceManager.context
        val screen: PreferenceScreen = preferenceManager.createPreferenceScreen(ctx)

        screen.addPreference(buildFeeSidePreference(ctx))

        categoryExchange = PreferenceCategory(ctx).apply {
            title = getString(R.string.fee_section_global_exchange)
            isIconSpaceReserved = false
        }
        screen.addPreference(categoryExchange)

        categoryBank = PreferenceCategory(ctx).apply {
            title = getString(R.string.fee_section_global_bank)
            isIconSpaceReserved = false
        }
        screen.addPreference(categoryBank)

        categoryPair = PreferenceCategory(ctx).apply {
            title = getString(R.string.fee_section_specific_pair)
            isIconSpaceReserved = false
        }
        screen.addPreference(categoryPair)

        preferenceScreen = screen

        db.getFees().observe(this) { fees -> renderFees(fees) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.fitsSystemWindows = true
        view.requestApplyInsets()
        activity?.title = getString(R.string.fee_manager_title)
    }

    private fun buildFeeSidePreference(ctx: Context): ListPreference {
        return ListPreference(ctx).apply {
            key = "__fee_side"
            title = getString(R.string.fee_side_label)
            entries = resources.getStringArray(R.array.fee_side_names)
            entryValues = resources.getStringArray(R.array.fee_side_values)
            value = db.getFeeSideBlocking().name
            isIconSpaceReserved = false
            summaryProvider = Preference.SummaryProvider<ListPreference> { pref ->
                when (pref.value) {
                    FeeSide.CONVERTED.name -> getString(R.string.fee_side_summary_converted)
                    else -> getString(R.string.fee_side_summary_original)
                }
            }
            setOnPreferenceChangeListener { _, newValue ->
                val side = runCatching { FeeSide.valueOf(newValue.toString()) }
                    .getOrDefault(FeeSide.ORIGINAL)
                db.setFeeSide(side)
                true
            }
        }
    }

    private fun renderFees(fees: List<Fee>) {
        populate(
            category = categoryExchange,
            entries = fees.filterIsInstance<Fee.GlobalExchange>(),
            summaryFor = { null },
            onAdd = {
                showPercentSignDialog(existing = null) { percent, isMarkup ->
                    db.addFee(Fee.GlobalExchange(UUID.randomUUID().toString(), percent, isMarkup))
                }
            },
            onEdit = { fee ->
                showPercentSignDialog(
                    existing = fee,
                    onDelete = { db.deleteFee(fee.id) },
                ) { percent, isMarkup ->
                    db.updateFee(fee.copy(percent = percent, isMarkup = isMarkup))
                }
            },
        )
        populate(
            category = categoryBank,
            entries = fees.filterIsInstance<Fee.GlobalBank>(),
            summaryFor = { null },
            onAdd = {
                showPercentSignDialog(existing = null) { percent, isMarkup ->
                    db.addFee(Fee.GlobalBank(UUID.randomUUID().toString(), percent, isMarkup))
                }
            },
            onEdit = { fee ->
                showPercentSignDialog(
                    existing = fee,
                    onDelete = { db.deleteFee(fee.id) },
                ) { percent, isMarkup ->
                    db.updateFee(fee.copy(percent = percent, isMarkup = isMarkup))
                }
            },
        )
        populate(
            category = categoryPair,
            entries = fees.filterIsInstance<Fee.SpecificPair>(),
            summaryFor = { fee ->
                val arrow = if (fee.bothWays) "\u2194" else "\u2192"
                getString(R.string.fee_specific_pair_summary, fee.from, arrow, fee.to)
            },
            onAdd = {
                showSpecificPairDialog(existing = null) { db.addFee(it) }
            },
            onEdit = { fee ->
                showSpecificPairDialog(
                    existing = fee,
                    onDelete = { db.deleteFee(fee.id) },
                ) { updated ->
                    db.updateFee(updated.copy(id = fee.id))
                }
            },
        )
    }

    private fun <T : Fee> populate(
        category: PreferenceCategory,
        entries: List<T>,
        summaryFor: (T) -> String?,
        onAdd: () -> Unit,
        onEdit: (T) -> Unit,
    ) {
        category.removeAll()
        val ctx = category.context
        if (entries.isEmpty()) {
            category.addPreference(
                Preference(ctx).apply {
                    title = getString(R.string.fee_empty)
                    isSelectable = false
                    isIconSpaceReserved = false
                }
            )
        } else {
            entries.forEach { fee ->
                category.addPreference(
                    Preference(ctx).apply {
                        title = formatPercent(fee.percent, fee.isMarkup)
                        summary = summaryFor(fee)
                        isIconSpaceReserved = false
                        setOnPreferenceClickListener {
                            onEdit(fee)
                            true
                        }
                    }
                )
            }
        }
        category.addPreference(
            Preference(ctx).apply {
                title = getString(R.string.fee_add)
                setIcon(R.drawable.ic_add)
                setOnPreferenceClickListener {
                    onAdd()
                    true
                }
            }
        )
    }

    private fun formatPercent(percent: BigDecimal, isMarkup: Boolean): String {
        val sign = if (isMarkup) getString(R.string.fee_edit_sign_positive)
        else getString(R.string.fee_edit_sign_negative)
        return "$sign ${percent.toHumanReadableNumber(requireContext(), suffix = "%")}"
    }

    private fun showPercentSignDialog(
        existing: Fee?,
        onDelete: (() -> Unit)? = null,
        onConfirm: (BigDecimal, Boolean) -> Unit,
    ) {
        val ctx = requireContext()
        val padH = resources.getDimensionPixelSize(R.dimen.margin3x)
        val padV = resources.getDimensionPixelSize(R.dimen.margin2x)
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, 0)
        }
        val percentInput = EditText(ctx).apply {
            hint = getString(R.string.fee_edit_percent)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (existing != null) setText(existing.percent.toPlainString())
        }
        val signGroup = buildSignToggle(ctx, existing?.isMarkup)
        container.addView(percentInput)
        container.addView(
            signGroup.first,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.margin2x)
            },
        )

        val builder = MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.fee_edit_percent)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val percent = percentInput.text.toString().toBigDecimalOrNull()
                    ?: BigDecimal.ZERO
                val isMarkup = signGroup.first.checkedButtonId != signGroup.third
                onConfirm(percent.abs(), isMarkup)
            }
            .setNegativeButton(android.R.string.cancel, null)
        if (onDelete != null) {
            builder.setNeutralButton(R.string.fee_delete) { _, _ -> onDelete() }
        }
        builder.show()
    }

    private fun showSpecificPairDialog(
        existing: Fee.SpecificPair?,
        onDelete: (() -> Unit)? = null,
        onConfirm: (Fee.SpecificPair) -> Unit,
    ) {
        val ctx = requireContext()
        val padH = resources.getDimensionPixelSize(R.dimen.margin3x)
        val padV = resources.getDimensionPixelSize(R.dimen.margin2x)
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, 0)
        }

        var pickedFrom: String? = existing?.from
        var pickedTo: String? = existing?.to
        val placeholder = getString(R.string.fee_pair_pick_currency)

        val fromLabel = TextView(ctx).apply { text = getString(R.string.fee_pair_from) }
        val fromButton = MaterialButton(
            ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ).apply {
            text = pickedFrom ?: placeholder
            setOnClickListener {
                openCurrencyPicker { iso ->
                    pickedFrom = iso
                    text = iso
                }
            }
        }
        val toLabel = TextView(ctx).apply { text = getString(R.string.fee_pair_to) }
        val toButton = MaterialButton(
            ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ).apply {
            text = pickedTo ?: placeholder
            setOnClickListener {
                openCurrencyPicker { iso ->
                    pickedTo = iso
                    text = iso
                }
            }
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
        val signToggle = buildSignToggle(ctx, existing?.isMarkup)

        listOf(fromLabel, fromButton, toLabel, toButton, bothWays, percentLabel, percentInput)
            .forEach { container.addView(it) }
        container.addView(
            signToggle.first,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.margin2x)
            },
        )

        val builder = MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.fee_section_specific_pair)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val from = pickedFrom
                val to = pickedTo
                if (from == null || to == null) return@setPositiveButton
                val percent = percentInput.text.toString().toBigDecimalOrNull()
                    ?: BigDecimal.ZERO
                val isMarkup = signToggle.first.checkedButtonId != signToggle.third
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
        if (onDelete != null) {
            builder.setNeutralButton(R.string.fee_delete) { _, _ -> onDelete() }
        }
        builder.show()
    }

    /**
     * Returns (toggle group, +id, −id). Selection defaults to + unless
     * [initialMarkup] is explicitly false.
     */
    private fun buildSignToggle(
        ctx: Context,
        initialMarkup: Boolean?,
    ): Triple<MaterialButtonToggleGroup, Int, Int> {
        val group = MaterialButtonToggleGroup(ctx).apply { isSingleSelection = true }
        val btnPlus = MaterialButton(
            ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ).apply {
            id = View.generateViewId()
            text = getString(R.string.fee_edit_sign_positive)
        }
        val btnMinus = MaterialButton(
            ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ).apply {
            id = View.generateViewId()
            text = getString(R.string.fee_edit_sign_negative)
        }
        group.addView(btnPlus)
        group.addView(btnMinus)
        group.check(if (initialMarkup == false) btnMinus.id else btnPlus.id)
        return Triple(group, btnPlus.id, btnMinus.id)
    }

    private fun openCurrencyPicker(onPicked: (String) -> Unit) {
        val dialog = SearchableSpinnerDialog(requireContext())
        dialog.onRateClicked = { rate, _ -> onPicked(rate.currency.iso4217Alpha()) }
        dialog.show(parentFragmentManager, null)
    }
}
