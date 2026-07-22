package de.salomax.currencies.view.cart

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
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
import de.salomax.currencies.util.toHumanReadableNumber
import de.salomax.currencies.view.BaseActivity
import de.salomax.currencies.view.main.spinner.SearchableSpinner
import de.salomax.currencies.view.preference.PreferenceActivity
import de.salomax.currencies.viewmodel.cart.CartSnapshot
import de.salomax.currencies.viewmodel.cart.CartViewModel
import de.salomax.currencies.viewmodel.main.CalculatorInputState
import java.math.BigDecimal
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

class CartActivity : BaseActivity() {

    private lateinit var viewModel: CartViewModel
    private lateinit var exporter: CartExporter

    private lateinit var recycler: RecyclerView
    private lateinit var subtotalLabel: TextView
    private lateinit var feeLine: TextView
    private lateinit var totalLabel: TextView
    private lateinit var spinnerFrom: SearchableSpinner
    private lateinit var spinnerTo: SearchableSpinner
    private lateinit var swapButton: ImageButton
    private lateinit var feeSideButton: AppCompatImageButton
    private lateinit var emptyHint: TextView
    private lateinit var addButton: MaterialButton

    private lateinit var adapter: CartItemAdapter

    // Set while a CartExpressionDialog is showing so the keypad's XML-declared
    // reflection targets (numberEvent / calculationEvent / decimalEvent /
    // deleteEvent / percentEvent) can be forwarded to the dialog's state
    // without CartActivity holding any calculator model itself.
    internal var activeCalculatorState: CalculatorInputState? = null
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
        this.feeLine = findViewById(R.id.cart_fee_line)
        this.totalLabel = findViewById(R.id.cart_total_value)
        this.spinnerFrom = findViewById(R.id.cart_spinner_from)
        this.spinnerTo = findViewById(R.id.cart_spinner_to)
        this.swapButton = findViewById(R.id.cart_swap)
        this.feeSideButton = findViewById(R.id.cart_btn_fee_side)
        this.emptyHint = findViewById(R.id.cart_empty_hint)
        this.addButton = findViewById(R.id.cart_add_item)

        adapter = CartItemAdapter(
            onChange = viewModel::updateItem,
            onDelete = viewModel::removeItem,
            onEditExpression = { initial, apply ->
                CartExpressionDialog.show(this, initial) { apply(it) }
            },
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        addButton.setOnClickListener { viewModel.addItem(name = "", expression = "") }
        spinnerFrom.onItemSelectedListener = rateSpinnerListener(viewModel::setBaseCurrency)
        spinnerTo.onItemSelectedListener = rateSpinnerListener(viewModel::setDestinationCurrency)
        swapButton.setOnClickListener { viewModel.swapCurrencies() }
        feeSideButton.setOnClickListener {
            val current = viewModel.getFeeSide().value ?: FeeSide.ORIGINAL
            viewModel.setFeeSide(current.toggled())
        }
        feeSideButton.setOnLongClickListener {
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
        viewModel.getCurrentCart().observe(this) { cart ->
            adapter.setCurrency(cart.currency)
            adapter.submitList(cart.items.toList())
            emptyHint.visibility = if (cart.items.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.getBaseCurrency().observe(this) { spinnerFrom.setSelection(it) }
        viewModel.getDestinationCurrency().observe(this) { spinnerTo.setSelection(it) }
        viewModel.getSubtotal().observe(this) { value ->
            subtotalLabel.text = formatAmount(value, viewModel.getBaseCurrency().value)
        }
        viewModel.getTotal().observe(this) { value ->
            totalLabel.text = formatAmount(value, viewModel.getDestinationCurrency().value)
        }
        viewModel.getFees().observe(this) { updateFeeLine() }
        viewModel.getCurrentCart().observe(this) { updateFeeLine() }
        viewModel.getFeeSide().observe(this) { side ->
            val effective = side ?: FeeSide.ORIGINAL
            feeSideButton.setImageResource(
                if (effective == FeeSide.CONVERTED) R.drawable.ic_fee_side_converted_horizontal
                else R.drawable.ic_fee_side_original_horizontal
            )
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
        val snapshot = viewModel.snapshotForShare()
        if (snapshot == null || snapshot.feeStack.compareTo(BigDecimal.ONE) == 0) {
            feeLine.visibility = View.GONE
            feeSideButton.visibility = View.GONE
            return
        }
        val deltaPercent = snapshot.feeStack
            .subtract(BigDecimal.ONE)
            .multiply(BigDecimal(100))
            .setScale(CART_DISPLAY_SCALE, RoundingMode.HALF_EVEN)
        feeLine.text = getString(R.string.cart_fee_line, deltaPercent.toPlainString())
        feeLine.visibility = View.VISIBLE
        feeSideButton.visibility = View.VISIBLE
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
    // Keypad reflection targets. main_keypad.xml wires each button's
    // android:onClick to these names — Android's LayoutInflater resolves
    // them against the hosting Activity, so we mirror MainActivity's
    // signatures here and forward to whichever CalculatorInputState is
    // currently open in a CartExpressionDialog.
    // ------------------------------------------------------------------

    @Suppress("UNUSED_PARAMETER")
    fun numberEvent(view: View) {
        activeCalculatorState?.addNumber((view as AppCompatButton).text.toString())
    }

    @Suppress("UNUSED_PARAMETER")
    fun decimalEvent(view: View) {
        activeCalculatorState?.addDecimal()
    }

    @Suppress("UNUSED_PARAMETER")
    fun deleteEvent(view: View) {
        activeCalculatorState?.delete()
    }

    @Suppress("UNUSED_PARAMETER")
    fun percentEvent(view: View) {
        activeCalculatorState?.addPercent()
    }

    @Suppress("UNUSED_PARAMETER")
    fun calculationEvent(view: View) {
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
