package de.salomax.currencies.view.preference

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.salomax.currencies.R
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.Fee
import de.salomax.currencies.model.FeeSide
import de.salomax.currencies.repository.Database
import de.salomax.currencies.util.dpToPx
import de.salomax.currencies.util.toHumanReadableNumber
import de.salomax.currencies.view.main.spinner.SearchableSpinnerDialog
import java.math.BigDecimal
import java.util.UUID

// Preference key for the "fee side" row — the leading __ marks it as
// UI-only state that's ignored by the settings back-up/restore pipeline.
private const val PREF_KEY_FEE_SIDE = "__fee_side"

private const val FEE_SIDE_SUMMARY_ALPHA = 0.7f

// Sign-toggle button geometry (dp) and text size (sp).
private const val SIGN_TOGGLE_BUTTON_HEIGHT_DP = 56f
private const val SIGN_TOGGLE_BUTTON_WIDTH_DP = 80f
private const val SIGN_TOGGLE_TEXT_SIZE_SP = 20f

// Flag glyph height for inline flag spans (sp so it scales with body text).
private const val FLAG_INLINE_HEIGHT_SP = 14f

// Arrows used in the "specific pair" summary.
private const val ARROW_ONE_WAY = "\u2192"
private const val ARROW_BOTH_WAYS = "\u2194"

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

        categoryExchange = addCategory(screen, ctx, R.string.fee_section_global_exchange)
        categoryBank = addCategory(screen, ctx, R.string.fee_section_global_bank)
        categoryPair = addCategory(screen, ctx, R.string.fee_section_specific_pair)

        preferenceScreen = screen

        db.getFees().observe(this) { fees -> renderFees(fees) }
    }

    private fun addCategory(screen: PreferenceScreen, ctx: Context, titleRes: Int): PreferenceCategory =
        PreferenceCategory(ctx).apply {
            title = getString(titleRes)
            isIconSpaceReserved = false
        }.also(screen::addPreference)

    /**
     * A vertical LinearLayout with the standard dialog horizontal padding, used
     * as the `setView` container for the fee-side / percent / pair dialogs so
     * the inner rows sit inside Material dialog gutters.
     */
    private fun paddedDialogContainer(ctx: Context, topPadding: Int = 0): LinearLayout {
        val padH = resources.getDimensionPixelSize(R.dimen.margin3x)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, topPadding, padH, 0)
        }
    }

    private fun feeSideLabels(side: FeeSide): Pair<String, String> {
        return when (side) {
            FeeSide.CONVERTED -> getString(R.string.fee_side_converted) to
                getString(R.string.fee_side_summary_converted)
            else -> getString(R.string.fee_side_original) to
                getString(R.string.fee_side_summary_original)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = getString(R.string.fee_manager_title)
    }

    private fun buildFeeSidePreference(ctx: Context): Preference {
        return Preference(ctx).apply {
            key = PREF_KEY_FEE_SIDE
            title = getString(R.string.fee_side_label)
            isIconSpaceReserved = false
            summary = formatFeeSideSummary(db.getFeeSideBlocking())
            setOnPreferenceClickListener {
                showFeeSideDialog { newSide ->
                    summary = formatFeeSideSummary(newSide)
                }
                true
            }
        }
    }

    private fun formatFeeSideSummary(side: FeeSide): CharSequence {
        val (name, desc) = feeSideLabels(side)
        return "$name\n$desc"
    }

    private fun showFeeSideDialog(onPicked: (FeeSide) -> Unit) {
        val ctx = requireContext()
        val sides = arrayOf(FeeSide.ORIGINAL, FeeSide.CONVERTED)
        val current = db.getFeeSideBlocking()

        val padV = resources.getDimensionPixelSize(R.dimen.margin2x)
        val container = paddedDialogContainer(ctx)

        val radios = mutableListOf<RadioButton>()
        val dialogHolder = arrayOfNulls<AlertDialog>(1)

        sides.forEachIndexed { index, side ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, padV, 0, padV)
                isClickable = true
                val ta = ctx.obtainStyledAttributes(
                    intArrayOf(android.R.attr.selectableItemBackground),
                )
                background = ta.getDrawable(0)
                ta.recycle()
            }
            val radio = RadioButton(ctx).apply {
                isChecked = side == current
                isClickable = false
            }
            radios += radio

            val (titleText, descText) = feeSideLabels(side)
            val titleView = TextView(ctx).apply {
                text = titleText
                setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_TitleMedium,
                )
            }
            val descView = TextView(ctx).apply {
                text = descText
                setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_BodySmall,
                )
                alpha = FEE_SIDE_SUMMARY_ALPHA
            }
            val textCol = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(titleView)
                addView(descView)
            }

            row.addView(radio)
            row.addView(
                textCol,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            row.setOnClickListener {
                radios.forEachIndexed { i, r -> r.isChecked = i == index }
                db.setFeeSide(side)
                onPicked(side)
                dialogHolder[0]?.dismiss()
            }
            container.addView(row)
        }

        dialogHolder[0] = MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.fee_side_label)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
                buildPairSummary(fee.from, fee.to, fee.bothWays, requireContext())
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
        summaryFor: (T) -> CharSequence?,
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
        val padV = resources.getDimensionPixelSize(R.dimen.margin2x)
        val container = paddedDialogContainer(ctx, topPadding = padV)
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
                // Extra clearance below the EditText so its text-selection
                // handle doesn't drop onto the sign toggle.
                topMargin = resources.getDimensionPixelSize(R.dimen.margin4x)
            },
        )

        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.fee_edit_percent)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val percent = percentInput.text.toString().toBigDecimalOrNull()
                    ?: BigDecimal.ZERO
                val isMarkup = signGroup.first.checkedButtonId != signGroup.third
                onConfirm(percent.abs(), isMarkup)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .withDeleteButton(onDelete)
            .show()
    }

    private fun showSpecificPairDialog(
        existing: Fee.SpecificPair?,
        onDelete: (() -> Unit)? = null,
        onConfirm: (Fee.SpecificPair) -> Unit,
    ) {
        val ctx = requireContext()
        val padV = resources.getDimensionPixelSize(R.dimen.margin2x)
        val container = paddedDialogContainer(ctx, topPadding = padV)

        var pickedFrom: String? = existing?.from
        var pickedTo: String? = existing?.to
        val placeholder = getString(R.string.fee_pair_pick_currency)

        val fromLabel = TextView(ctx).apply { text = getString(R.string.fee_pair_from) }
        val fromButton = MaterialButton(
            ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ).apply {
            applyCurrencyIcon(this, pickedFrom, placeholder, ctx)
            setOnClickListener {
                openCurrencyPicker { iso ->
                    pickedFrom = iso
                    applyCurrencyIcon(this, iso, placeholder, ctx)
                }
            }
        }
        val toLabel = TextView(ctx).apply { text = getString(R.string.fee_pair_to) }
        val toButton = MaterialButton(
            ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ).apply {
            applyCurrencyIcon(this, pickedTo, placeholder, ctx)
            setOnClickListener {
                openCurrencyPicker { iso ->
                    pickedTo = iso
                    applyCurrencyIcon(this, iso, placeholder, ctx)
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
                // Extra clearance below the EditText so its text-selection
                // handle doesn't drop onto the sign toggle.
                topMargin = resources.getDimensionPixelSize(R.dimen.margin4x)
            },
        )

        MaterialAlertDialogBuilder(ctx)
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
            .withDeleteButton(onDelete)
            .show()
    }

    private fun MaterialAlertDialogBuilder.withDeleteButton(onDelete: (() -> Unit)?): MaterialAlertDialogBuilder {
        if (onDelete != null) {
            setNeutralButton(R.string.fee_delete) { _, _ -> onDelete() }
        }
        return this
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
        val btnHeight = SIGN_TOGGLE_BUTTON_HEIGHT_DP.dpToPx().toInt()
        val btnWidth = SIGN_TOGGLE_BUTTON_WIDTH_DP.dpToPx().toInt()
        fun makeButton(labelRes: Int): MaterialButton {
            return MaterialButton(
                ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle,
            ).apply {
                id = View.generateViewId()
                text = getString(labelRes)
                textSize = SIGN_TOGGLE_TEXT_SIZE_SP
                insetTop = 0
                insetBottom = 0
            }
        }
        val btnPlus = makeButton(R.string.fee_edit_sign_positive)
        val btnMinus = makeButton(R.string.fee_edit_sign_negative)
        group.addView(btnPlus, LinearLayout.LayoutParams(btnWidth, btnHeight))
        group.addView(btnMinus, LinearLayout.LayoutParams(btnWidth, btnHeight))
        group.check(if (initialMarkup == false) btnMinus.id else btnPlus.id)
        return Triple(group, btnPlus.id, btnMinus.id)
    }

    private fun openCurrencyPicker(onPicked: (String) -> Unit) {
        val dialog = SearchableSpinnerDialog(requireContext())
        dialog.onRateClicked = { rate, _ -> onPicked(rate.currency.iso4217Alpha()) }
        dialog.show(parentFragmentManager, null)
    }

    private fun applyCurrencyIcon(
        button: MaterialButton,
        iso: String?,
        placeholder: String,
        ctx: Context,
    ) {
        button.text = iso ?: placeholder
        button.icon = iso?.let { Currency.fromString(it)?.flag(ctx) }
        // Preserve the flag's own colors; the default MaterialButton icon tint
        // would flatten it to a solid rectangle.
        button.iconTint = null
    }

    private fun buildPairSummary(
        from: String,
        to: String,
        bothWays: Boolean,
        ctx: Context,
    ): CharSequence {
        val arrow = if (bothWays) ARROW_BOTH_WAYS else ARROW_ONE_WAY
        val sb = SpannableStringBuilder()
        appendFlag(sb, from, ctx)
        sb.append(' ').append(from).append("  ").append(arrow).append("  ")
        appendFlag(sb, to, ctx)
        sb.append(' ').append(to)
        return sb
    }

    private fun appendFlag(sb: SpannableStringBuilder, iso: String, ctx: Context) {
        val currency = Currency.fromString(iso) ?: return
        val drawable = currency.flag(ctx)
        val heightPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, FLAG_INLINE_HEIGHT_SP, ctx.resources.displayMetrics,
        ).toInt()
        val intrinsicW = drawable.intrinsicWidth.coerceAtLeast(1)
        val intrinsicH = drawable.intrinsicHeight.coerceAtLeast(1)
        val widthPx = (heightPx.toFloat() * intrinsicW / intrinsicH).toInt()
        drawable.setBounds(0, 0, widthPx, heightPx)
        val start = sb.length
        sb.append(' ')
        sb.setSpan(
            ImageSpan(drawable, ImageSpan.ALIGN_BASELINE),
            start,
            sb.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
}
