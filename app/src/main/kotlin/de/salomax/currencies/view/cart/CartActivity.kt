package de.salomax.currencies.view.cart

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import de.salomax.currencies.R
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.SavedCart
import de.salomax.currencies.repository.CartExporter
import de.salomax.currencies.repository.CartFileResult
import de.salomax.currencies.util.toHumanReadableNumber
import de.salomax.currencies.view.BaseActivity
import de.salomax.currencies.viewmodel.cart.CartSnapshot
import de.salomax.currencies.viewmodel.cart.CartViewModel
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
    private lateinit var currencyButton: MaterialButton
    private lateinit var emptyHint: TextView
    private lateinit var addButton: MaterialButton

    private lateinit var adapter: CartItemAdapter
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
        this.currencyButton = findViewById(R.id.cart_currency_button)
        this.emptyHint = findViewById(R.id.cart_empty_hint)
        this.addButton = findViewById(R.id.cart_add_item)

        adapter = CartItemAdapter(
            onChange = viewModel::updateItem,
            onDelete = viewModel::removeItem,
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        addButton.setOnClickListener { viewModel.addItem(name = "", expression = "") }
        currencyButton.setOnClickListener { showCurrencyPicker() }

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
            currencyButton.text = getString(R.string.cart_currency_button, cart.currency)
        }
        viewModel.getSubtotal().observe(this) { value ->
            subtotalLabel.text = formatAmount(value, viewModel.getCurrentCart().value?.currency)
        }
        viewModel.getTotal().observe(this) { value ->
            totalLabel.text = formatAmount(value, viewModel.getCurrentCart().value?.currency)
        }
        viewModel.getFees().observe(this) { updateFeeLine() }
        // Also refresh the fee line whenever the cart's currency changes,
        // since which fees apply is currency-dependent.
        viewModel.getCurrentCart().observe(this) { updateFeeLine() }
    }

    private fun updateFeeLine() {
        val snapshot = viewModel.snapshotForShare()
        if (snapshot == null || snapshot.feeStack.compareTo(BigDecimal.ONE) == 0) {
            feeLine.visibility = View.GONE
            return
        }
        val deltaPercent = snapshot.feeStack
            .subtract(BigDecimal.ONE)
            .multiply(BigDecimal(100))
            .setScale(CART_DISPLAY_SCALE, RoundingMode.HALF_EVEN)
        feeLine.text = getString(R.string.cart_fee_line, deltaPercent.toPlainString())
        feeLine.visibility = View.VISIBLE
    }

    private fun formatAmount(value: BigDecimal?, currency: String?): String {
        val amount = (value ?: BigDecimal.ZERO)
            .setScale(CART_DISPLAY_SCALE, RoundingMode.HALF_EVEN)
            .toHumanReadableNumber(this, decimalPlaces = CART_DISPLAY_SCALE)
        return if (currency.isNullOrEmpty()) amount else "$amount $currency"
    }

    private fun showCurrencyPicker() {
        val currencies = Currency.entries.sortedBy { it.iso4217Alpha() }
        val labels = currencies.map { it.iso4217Alpha() }.toTypedArray()
        val currentIso = viewModel.getCurrentCart().value?.currency
        val checked = currencies.indexOfFirst { it.iso4217Alpha() == currentIso }
        AlertDialog.Builder(this)
            .setTitle(R.string.cart_currency_picker_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                viewModel.setCurrency(currencies[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
        val saved = viewModel.getSavedCarts().value.orEmpty()
        if (saved.isEmpty()) {
            showSnackbar(getString(R.string.cart_no_saved))
            return
        }
        val labels = saved.map { it.name.ifBlank { it.id.take(8) } }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.cart_menu_load)
            .setItems(labels) { _, which -> viewModel.loadSaved(saved[which].id) }
            .setNeutralButton(R.string.cart_load_manage) { _, _ -> showManageSavedDialog(saved) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showManageSavedDialog(saved: List<SavedCart>) {
        val labels = saved.map { it.name.ifBlank { it.id.take(8) } }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.cart_manage_title)
            .setItems(labels) { _, which ->
                val cart = saved[which]
                AlertDialog.Builder(this)
                    .setTitle(cart.name)
                    .setMessage(getString(R.string.cart_delete_confirm, cart.name))
                    .setPositiveButton(R.string.cart_delete_confirm_button) { _, _ ->
                        viewModel.deleteSaved(cart.id)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
        val ccy = snapshot.cart.currency
        val name = snapshot.cart.name.ifBlank { getString(R.string.cart_share_default_title) }
        appendLine(getString(R.string.cart_share_header, name, ccy))
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
                ccy,
            )
        )
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
                ccy,
            )
        )
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(findViewById(R.id.cart_root), message, Snackbar.LENGTH_SHORT).show()
    }
}
