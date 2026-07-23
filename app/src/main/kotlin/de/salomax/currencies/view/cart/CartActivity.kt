package de.salomax.currencies.view.cart

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import de.salomax.currencies.R
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.FeeSide
import de.salomax.currencies.model.Rate
import de.salomax.currencies.model.SavedCart
import de.salomax.currencies.repository.CartExporter
import de.salomax.currencies.repository.CartFileResult
import de.salomax.currencies.util.hapticTap
import de.salomax.currencies.util.toHumanReadableNumber
import de.salomax.currencies.view.BaseActivity
import de.salomax.currencies.view.main.spinner.SearchableSpinner
import de.salomax.currencies.view.preference.PreferenceActivity
import de.salomax.currencies.util.OPERATOR_REGEX
import de.salomax.currencies.viewmodel.cart.CartSnapshot
import de.salomax.currencies.viewmodel.cart.CartViewModel
import de.salomax.currencies.viewmodel.main.CalculatorInputState
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Decimal places for cart display; matches the main-screen convention where
// "money-facing" values render to two places by default.
private const val CART_DISPLAY_SCALE = 2

// Suffix used when SAF asks for a suggested filename.
private const val EXPORT_FILE_MIME = "application/json"
private const val EXPORT_FILE_EXT = ".json"
private const val EXPORT_FILE_DATE_FORMAT = "yyyyMMdd-HHmmss"

// Duration of the slide-in / slide-out animation for the cart keypad.
private const val KEYPAD_ANIM_MS = 180L

class CartActivity : BaseActivity() {

    private lateinit var viewModel: CartViewModel
    private lateinit var exporter: CartExporter

    private lateinit var recycler: RecyclerView
    private lateinit var subtotalLabel: TextView
    private lateinit var subtotalExtra: TextView
    private lateinit var feeLine: TextView
    private lateinit var totalLabel: TextView
    private lateinit var totalExtra: TextView
    private lateinit var spinnerFrom: SearchableSpinner
    private lateinit var spinnerTo: SearchableSpinner
    private lateinit var swapButton: ImageButton
    private lateinit var feeSideButton: AppCompatImageButton
    private lateinit var emptyHint: TextView
    private lateinit var addButton: MaterialButton

    private lateinit var adapter: CartItemAdapter

    // Cached haptic setting so per-tap handlers don't need to touch prefs.
    private var hapticEnabled = false

    // Slide-up keypad state — behaves like a soft IME. `activeExprField` is
    // the row's price EditText the keypad is currently editing; the taps
    // route through `activeCalculatorState`, and every state change is
    // mirrored back into the field's text.
    private lateinit var keypadContainer: ViewGroup
    private lateinit var keypadRegular: View
    private lateinit var keypadExtended: View
    private var activeCalculatorState: CalculatorInputState? = null
    private var activeExprField: EditText? = null
    private var activeStateObserver: Observer<String?>? = null
    private var keypadBackCallback: OnBackPressedCallback? = null

    private lateinit var exportLauncher: ActivityResultLauncher<String>
    private lateinit var importLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cart)
        supportActionBar?.apply {
            title = getString(R.string.cart_title)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        this.viewModel = ViewModelProvider(this)[CartViewModel::class.java]
        this.exporter = CartExporter(this)

        this.recycler = findViewById(R.id.cart_items)
        this.subtotalLabel = findViewById(R.id.cart_subtotal_value)
        this.subtotalExtra = findViewById(R.id.cart_subtotal_extra)
        this.feeLine = findViewById(R.id.cart_fee_line)
        this.totalLabel = findViewById(R.id.cart_total_value)
        this.totalExtra = findViewById(R.id.cart_total_extra)
        this.spinnerFrom = findViewById(R.id.cart_spinner_from)
        this.spinnerTo = findViewById(R.id.cart_spinner_to)
        this.swapButton = findViewById(R.id.cart_swap)
        this.feeSideButton = findViewById(R.id.cart_btn_fee_side)
        this.emptyHint = findViewById(R.id.cart_empty_hint)
        this.addButton = findViewById(R.id.cart_add_item)
        this.keypadContainer = findViewById(R.id.cart_keypad_container)
        this.keypadRegular = findViewById(R.id.cart_keypad_regular)
        this.keypadExtended = findViewById(R.id.cart_keypad_extended)

        adapter = CartItemAdapter(
            onChange = viewModel::updateItem,
            onDelete = viewModel::removeItem,
            onEditExpression = { field, _ -> openKeypadFor(field) },
        )

        // Back-press dismisses the keypad first; only bubbles up to finish
        // the activity when the keypad is already closed.
        keypadBackCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = closeKeypad()
        }.also { onBackPressedDispatcher.addCallback(this, it) }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        addButton.setOnClickListener {
            it.hapticTap(hapticEnabled)
            viewModel.addItem(name = "", expression = "")
        }
        spinnerFrom.onItemSelectedListener = rateSpinnerListener(viewModel::setBaseCurrency)
        spinnerTo.onItemSelectedListener = rateSpinnerListener(viewModel::setDestinationCurrency)
        swapButton.setOnClickListener {
            it.hapticTap(hapticEnabled)
            viewModel.swapCurrencies()
        }
        feeSideButton.setOnClickListener {
            it.hapticTap(hapticEnabled)
            val current = viewModel.getFeeSide().value ?: FeeSide.ORIGINAL
            viewModel.setFeeSide(current.toggled())
        }
        feeSideButton.setOnLongClickListener {
            it.hapticTap(hapticEnabled)
            startActivity(PreferenceActivity.feesIntent(this))
            true
        }

        exportLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument(EXPORT_FILE_MIME)
        ) { uri -> uri?.let(::doExport) }
        importLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri -> uri?.let(::doImport) }

        observe()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.cart, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.cart_share -> { shareCart(); true }
            R.id.cart_save_as -> { showSaveAsDialog(); true }
            R.id.cart_load -> { showLoadDialog(); true }
            R.id.cart_export -> { launchExport(); true }
            R.id.cart_import -> { launchImport(); true }
            R.id.cart_clear -> { confirmClear(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun observe() {
        viewModel.isHapticFeedbackEnabled.observe(this) {
            hapticEnabled = it
            adapter.setHapticEnabled(it)
        }
        viewModel.isExtendedKeypadEnabled.observe(this) { extended ->
            keypadRegular.visibility = if (extended) View.GONE else View.VISIBLE
            keypadExtended.visibility = if (extended) View.VISIBLE else View.GONE
        }
        viewModel.getCurrentCart().observe(this) { cart ->
            adapter.setCurrency(cart.currency)
            adapter.submitList(cart.items.toList())
            emptyHint.visibility = if (cart.items.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.getBaseCurrency().observe(this) { spinnerFrom.setSelection(it) }
        viewModel.getDestinationCurrency().observe(this) { spinnerTo.setSelection(it) }
        viewModel.getSubtotal().observe(this) { value ->
            subtotalLabel.text = formatAmount(value, viewModel.getBaseCurrency().value)
            updateFeeExtras()
        }
        viewModel.getTotal().observe(this) { value ->
            totalLabel.text = formatAmount(value, viewModel.getDestinationCurrency().value)
            updateFeeExtras()
        }
        viewModel.getFees().observe(this) {
            updateFeeLine()
            updateFeeExtras()
        }
        viewModel.getCurrentCart().observe(this) {
            updateFeeLine()
            updateFeeExtras()
        }
        viewModel.getFeeSide().observe(this) { side ->
            val effective = side ?: FeeSide.ORIGINAL
            feeSideButton.setImageResource(
                if (effective == FeeSide.CONVERTED) R.drawable.ic_fee_side_converted_horizontal
                else R.drawable.ic_fee_side_original_horizontal
            )
            updateFeeExtras()
        }
        viewModel.getExchangeRates().observe(this) { rates ->
            // Feed the same rate list the spinner shows on the main screen so
            // its picker shows flags, ISO codes, and (when enabled) preview
            // conversions.
            spinnerFrom.setRates(rates?.rates)
            spinnerTo.setRates(rates?.rates)
            // Re-apply the saved base/dest selections: setRates rebuilds the
            // adapter, which drops the previous selection unless we re-set it.
            spinnerFrom.setSelection(viewModel.getBaseCurrency().value)
            spinnerTo.setSelection(viewModel.getDestinationCurrency().value)
            updateFeeLine()
        }
    }

    // Same shape as MainActivity's spinner listener: forwards the selected
    // rate's currency to the given setter, guarding against spurious "no
    // selection" callbacks that fire during adapter swaps.
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

    private fun updateFeeLine() {
        // Compute the stack directly from currencies + fees so the arrow /
        // percentage show even when the cart is empty (matches main screen).
        val stack = viewModel.currentFeeStack()
        if (stack.compareTo(BigDecimal.ONE) == 0) {
            feeLine.visibility = View.GONE
            feeSideButton.visibility = View.GONE
            return
        }
        val deltaPercent = stack
            .subtract(BigDecimal.ONE)
            .multiply(BigDecimal(100))
            .setScale(CART_DISPLAY_SCALE, RoundingMode.HALF_EVEN)
        feeLine.text = getString(R.string.cart_fee_line, deltaPercent.toPlainString())
        feeLine.visibility = View.VISIBLE
        feeSideButton.visibility = View.VISIBLE
    }

    /**
     * When fees apply, expose the "other side" of the fee so users can see both:
     * fee-side ORIGINAL keeps subtotal at mid-market → show subtotal-after-fee in
     * the base currency ("True cost"); fee-side CONVERTED bakes fees into total
     * → show total-before-fee in the destination currency ("Original value").
     */
    private fun updateFeeExtras() {
        val stack = viewModel.currentFeeStack()
        if (stack.compareTo(BigDecimal.ONE) == 0) {
            subtotalExtra.visibility = View.GONE
            totalExtra.visibility = View.GONE
            return
        }
        val side = viewModel.getFeeSide().value ?: FeeSide.ORIGINAL
        when (side) {
            FeeSide.ORIGINAL -> {
                val subtotal = viewModel.getSubtotal().value ?: BigDecimal.ZERO
                val withFee = subtotal.multiply(stack, MathContext.DECIMAL128)
                subtotalExtra.text = getString(R.string.fee_true_cost_prefix) +
                    formatAmount(withFee, viewModel.getBaseCurrency().value)
                subtotalExtra.visibility = View.VISIBLE
                totalExtra.visibility = View.GONE
            }
            FeeSide.CONVERTED -> {
                val total = viewModel.getTotal().value ?: BigDecimal.ZERO
                val fair = total.multiply(stack, MathContext.DECIMAL128)
                totalExtra.text = getString(R.string.fee_original_value_prefix) +
                    formatAmount(fair, viewModel.getDestinationCurrency().value)
                totalExtra.visibility = View.VISIBLE
                subtotalExtra.visibility = View.GONE
            }
        }
    }

    private fun formatAmount(value: BigDecimal?, currency: Currency?): String {
        val amount = (value ?: BigDecimal.ZERO)
            .setScale(CART_DISPLAY_SCALE, RoundingMode.HALF_EVEN)
            .toHumanReadableNumber(this, decimalPlaces = CART_DISPLAY_SCALE)
        val iso = currency?.iso4217Alpha()
        return if (iso.isNullOrEmpty()) amount else "$amount $iso"
    }

    private fun showSaveAsDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.cart_save_name_hint)
            setText(viewModel.getCurrentCart().value?.name.orEmpty())
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.cart_menu_save_as)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim().ifBlank {
                    getString(R.string.cart_default_saved_name)
                }
                // If we loaded a saved cart earlier its id is non-empty —
                // reuse it so "Save" overwrites rather than duplicating.
                val existingId = viewModel.getCurrentCart().value?.id?.takeIf { it.isNotEmpty() }
                viewModel.saveCurrentAs(name, existingId)
                showSnackbar(getString(R.string.cart_saved_toast, name))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showLoadDialog() {
        val saved = viewModel.getSavedCartsSnapshot().toMutableList()
        if (saved.isEmpty()) {
            showSnackbar(getString(R.string.cart_no_saved))
            return
        }
        val adapter = SavedCartAdapter(saved)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.cart_menu_load)
            .setAdapter(adapter) { _, which -> viewModel.loadSaved(saved[which].id) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        adapter.onDelete = { position ->
            val cart = saved[position]
            AlertDialog.Builder(this)
                .setTitle(cart.name.ifBlank { cart.id.take(8) })
                .setMessage(getString(R.string.cart_delete_confirm, cart.name))
                .setPositiveButton(R.string.cart_delete_confirm_button) { _, _ ->
                    viewModel.deleteSaved(cart.id)
                    saved.removeAt(position)
                    if (saved.isEmpty()) dialog.dismiss()
                    else adapter.notifyDataSetChanged()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        adapter.onRename = { position ->
            val cart = saved[position]
            val input = EditText(this).apply {
                hint = getString(R.string.cart_save_name_hint)
                setText(cart.name)
            }
            AlertDialog.Builder(this)
                .setTitle(R.string.cart_rename_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val name = input.text.toString().trim().ifBlank {
                        getString(R.string.cart_default_saved_name)
                    }
                    viewModel.renameSaved(cart.id, name)
                    saved[position] = cart.copy(name = name)
                    adapter.notifyDataSetChanged()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        dialog.show()
    }

    private fun launchExport() {
        val name = viewModel.getCurrentCart().value?.name?.ifBlank { null } ?: "cart"
        val stamp = SimpleDateFormat(EXPORT_FILE_DATE_FORMAT, Locale.US).format(Date())
        exportLauncher.launch("$name-$stamp$EXPORT_FILE_EXT")
    }

    private fun launchImport() {
        importLauncher.launch(arrayOf(EXPORT_FILE_MIME))
    }

    private fun doExport(uri: Uri) {
        val cart = viewModel.getCurrentCart().value ?: return
        // Copy so the exported file always has a real name, even if the
        // user hasn't gone through Save-as yet.
        val toExport = cart.copy(
            name = cart.name.ifBlank { getString(R.string.cart_default_saved_name) },
            createdAt = System.currentTimeMillis(),
        )
        when (val res = exporter.export(uri, toExport)) {
            is CartFileResult.Success -> showSnackbar(getString(R.string.cart_export_ok))
            is CartFileResult.Failure -> showSnackbar(
                getString(R.string.cart_export_error, res.message)
            )
            is CartFileResult.Loaded -> Unit
        }
    }

    private fun doImport(uri: Uri) {
        when (val res = exporter.import(uri)) {
            is CartFileResult.Loaded -> {
                viewModel.setCurrent(res.cart)
                showSnackbar(getString(R.string.cart_import_ok))
            }
            is CartFileResult.Failure -> showSnackbar(
                getString(R.string.cart_import_error, res.message)
            )
            is CartFileResult.Success -> Unit
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(R.string.cart_menu_clear)
            .setMessage(R.string.cart_clear_confirm)
            .setPositiveButton(R.string.cart_clear_confirm_button) { _, _ ->
                viewModel.clearItems()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun shareCart() {
        val snapshot = viewModel.snapshotForShare()
        if (snapshot == null) {
            showSnackbar(getString(R.string.cart_share_empty))
            return
        }
        val text = buildShareText(snapshot)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, null))
    }

    private fun buildShareText(snapshot: CartSnapshot): String = buildString {
        val baseIso = snapshot.baseCurrency.iso4217Alpha()
        val destIso = snapshot.destinationCurrency.iso4217Alpha()
        val name = snapshot.cart.name.ifBlank { getString(R.string.cart_share_default_title) }
        appendLine(getString(R.string.cart_share_header, name, baseIso))
        snapshot.evaluatedItems.forEach { (item, value) ->
            val amount = value.setScale(CART_DISPLAY_SCALE, RoundingMode.HALF_EVEN).toPlainString()
            val label = item.name.ifBlank { item.expression }
            appendLine("• $label: $amount")
        }
        appendLine("—")
        appendLine(
            getString(
                R.string.cart_share_subtotal,
                snapshot.subtotal.setScale(CART_DISPLAY_SCALE, RoundingMode.HALF_EVEN).toPlainString(),
                baseIso,
            )
        )
        if (snapshot.isConverting) {
            appendLine(
                getString(
                    R.string.cart_share_converted,
                    snapshot.convertedSubtotal.setScale(CART_DISPLAY_SCALE, RoundingMode.HALF_EVEN).toPlainString(),
                    destIso,
                )
            )
        }
        if (snapshot.feeStack.compareTo(BigDecimal.ONE) != 0) {
            val deltaPercent = snapshot.feeStack
                .subtract(BigDecimal.ONE)
                .multiply(BigDecimal(100))
                .setScale(CART_DISPLAY_SCALE, RoundingMode.HALF_EVEN)
            appendLine(getString(R.string.cart_share_fees, deltaPercent.toPlainString()))
        }
        append(
            getString(
                R.string.cart_share_total,
                snapshot.total.setScale(CART_DISPLAY_SCALE, RoundingMode.HALF_EVEN).toPlainString(),
                destIso,
            )
        )
    }

    // ------------------------------------------------------------------
    // Slide-up keypad — behaves like a soft IME. A value field taps calls
    // `openKeypadFor`; taps on non-value regions (or a back-press) call
    // `closeKeypad`; every keypad button routes through the reflection
    // targets below.
    // ------------------------------------------------------------------

    /**
     * Show the keypad for [field], seeding a fresh [CalculatorInputState]
     * with its current text and mirroring every state change back into the
     * field. Called with the currently-focused row's price EditText.
     */
    fun openKeypadFor(field: EditText) {
        hideSystemIme()
        detachActiveField()
        val state = CalculatorInputState().apply { seedExpression(field.text?.toString().orEmpty()) }
        val observer = Observer<String?> { field.setText(state.toExpressionString()) }
        state.baseValueText.observeForever(observer)
        state.calculationValueText.observeForever(observer)
        activeCalculatorState = state
        activeExprField = field
        activeStateObserver = observer
        showKeypad()
    }

    /** Hide the keypad and unbind whichever field was being edited. */
    fun closeKeypad() {
        if (activeExprField == null && keypadContainer.visibility == View.GONE) return
        detachActiveField()
        hideKeypad()
    }

    private fun showKeypad() {
        keypadBackCallback?.isEnabled = true
        if (keypadContainer.visibility == View.VISIBLE) return
        keypadContainer.visibility = View.VISIBLE
        keypadContainer.translationY = keypadContainer.height.toFloat().takeIf { it > 0f }
            ?: resources.displayMetrics.heightPixels.toFloat()
        keypadContainer.animate()
            .translationY(0f)
            .setDuration(KEYPAD_ANIM_MS)
            .start()
    }

    private fun hideKeypad() {
        keypadBackCallback?.isEnabled = false
        if (keypadContainer.visibility != View.VISIBLE) return
        keypadContainer.animate()
            .translationY(keypadContainer.height.toFloat())
            .setDuration(KEYPAD_ANIM_MS)
            .withEndAction { keypadContainer.visibility = View.GONE }
            .start()
    }

    private fun detachActiveField() {
        val state = activeCalculatorState
        val observer = activeStateObserver
        if (state != null && observer != null) {
            state.baseValueText.removeObserver(observer)
            state.calculationValueText.removeObserver(observer)
        }
        activeCalculatorState = null
        activeExprField = null
        activeStateObserver = null
    }

    /**
     * Route outside-taps to close the keypad. Taps *inside* the keypad
     * (button presses) and taps on the currently-editing field itself pass
     * through unchanged; a tap on any other row's price field will fall
     * through to that field's click handler, which calls [openKeypadFor]
     * again and swaps the active state without a visible close/open flicker.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN && keypadContainer.visibility == View.VISIBLE) {
            val keypadRect = Rect().also(keypadContainer::getGlobalVisibleRect)
            if (!keypadRect.contains(ev.rawX.toInt(), ev.rawY.toInt())
                && !isTouchOnActiveField(ev)
            ) {
                keypadContainer.post {
                    // If the touched view was another expr field, its click
                    // handler has already swapped `activeExprField` by now.
                    // Only close if we're still bound to the previous field.
                    if (activeExprField == null) return@post
                    val stillOn = activeExprField
                    keypadContainer.postDelayed({
                        if (activeExprField === stillOn) closeKeypad()
                    }, 40)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun hideSystemIme() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        val token = currentFocus?.windowToken ?: window.decorView.windowToken ?: return
        imm.hideSoftInputFromWindow(token, 0)
    }

    private fun isTouchOnActiveField(ev: MotionEvent): Boolean {
        val field = activeExprField ?: return false
        val r = Rect().also(field::getGlobalVisibleRect)
        return r.contains(ev.rawX.toInt(), ev.rawY.toInt())
    }

    // ------------------------------------------------------------------
    // Keypad reflection targets. main_keypad.xml wires each button's
    // android:onClick to these names — Android's LayoutInflater resolves
    // them against the hosting Activity, so we mirror MainActivity's
    // signatures here and forward to the currently-active state.
    // ------------------------------------------------------------------

    fun numberEvent(view: View) {
        view.hapticTap(hapticEnabled)
        activeCalculatorState?.addNumber((view as AppCompatButton).text.toString())
    }

    fun decimalEvent(view: View) {
        view.hapticTap(hapticEnabled)
        activeCalculatorState?.addDecimal()
    }

    fun deleteEvent(view: View) {
        view.hapticTap(hapticEnabled)
        activeCalculatorState?.delete()
    }

    fun percentEvent(view: View) {
        view.hapticTap(hapticEnabled)
        activeCalculatorState?.addPercent()
    }

    fun calculationEvent(view: View) {
        view.hapticTap(hapticEnabled)
        activeCalculatorState?.addOperator((view as AppCompatButton).text.toString())
    }

    private fun showSnackbar(message: String) {
        // Pass `this` as the theme context so Snackbar inflates against the
        // activity's Material3 theme. The 2-arg overload walks up from the
        // anchor view and can hit the ActionBar overlay, which crashes on
        // M3 attributes missing from the AppCompat ActionBar theme.
        Snackbar.make(this, findViewById(R.id.snackbar_top_position), message, Snackbar.LENGTH_SHORT).show()
    }
}

/**
 * Rebuild the input state from a previously-saved cart-row expression so the
 * keypad opens where the user left off. Empty input leaves the state at its
 * default "0" seed.
 */
private fun CalculatorInputState.seedExpression(expression: String) {
    val trimmed = expression.trim()
    if (trimmed.isEmpty()) return
    if (!trimmed.contains(OPERATOR_REGEX)) {
        replayDigits(trimmed)
        return
    }
    // Split on operator boundaries while keeping operators as separate
    // tokens — mirrors how the state serialises them back out.
    val tokens = mutableListOf<String>()
    val buf = StringBuilder()
    trimmed.forEach { ch ->
        if (ch.toString().matches(OPERATOR_REGEX)) {
            if (buf.isNotBlank()) tokens += buf.toString().trim()
            tokens += ch.toString()
            buf.clear()
        } else {
            buf.append(ch)
        }
    }
    if (buf.isNotBlank()) tokens += buf.toString().trim()
    // The digit-replay API is state-aware — it targets the base row before
    // the first operator and the calculation row afterwards — so every
    // operand goes through the same path.
    tokens.forEach { token ->
        if (token.matches(OPERATOR_REGEX)) addOperator(token) else replayDigits(token)
    }
}

private fun CalculatorInputState.replayDigits(number: String) {
    number.forEach { ch ->
        when {
            ch.isDigit() -> addNumber(ch.toString())
            ch == '.' -> addDecimal()
        }
    }
}

/**
 * Serialise the current input state back to a string the cart layer can
 * store and later evaluate (matches the expression format cart rows use).
 */
private fun CalculatorInputState.toExpressionString(): String {
    val calc = calculationValueText.value
    return if (calc.isNullOrBlank()) baseValueText.value.orEmpty() else calc.trim()
}

/**
 * ListAdapter for the "Load" dialog: each row shows the saved cart's name and
 * a trailing delete button. Tapping the row itself falls through to the
 * dialog's `OnClickListener` (load); tapping the delete icon calls [onDelete].
 */
private class SavedCartAdapter(
    private val items: List<SavedCart>,
) : BaseAdapter() {
    var onDelete: ((Int) -> Unit)? = null
    var onRename: ((Int) -> Unit)? = null

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): SavedCart = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.dialog_saved_cart_row, parent, false)
        val cart = items[position]
        view.findViewById<TextView>(R.id.saved_cart_row_name).text =
            cart.name.ifBlank { cart.id.take(8) }
        view.findViewById<View>(R.id.saved_cart_row_rename).setOnClickListener {
            onRename?.invoke(position)
        }
        view.findViewById<View>(R.id.saved_cart_row_delete).setOnClickListener {
            onDelete?.invoke(position)
        }
        return view
    }
}
