package de.salomax.currencies.view.cart

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.salomax.currencies.R
import de.salomax.currencies.model.CartItem
import de.salomax.currencies.util.toHumanReadableNumber
import de.salomax.currencies.viewmodel.cart.evaluateItem
import java.math.RoundingMode

// Debounce so the persistence pump doesn't fire on every keystroke — the
// user typing "12.34" would otherwise write four times to prefs.
private const val EDIT_DEBOUNCE_MS = 300L

// Item values are only shown as a preview; scale is capped so a valid
// long-scale intermediate doesn't overwhelm the tiny inline slot.
private const val ROW_PREVIEW_SCALE = 2

/**
 * Adapter for the shopping-cart line-item list. Each row edits its own
 * [CartItem] in place — commits back to the view model on a debounced
 * text-changed signal so intermediate typing doesn't hammer disk.
 */
class CartItemAdapter(
    private val onChange: (id: String, name: String, expression: String) -> Unit,
    private val onDelete: (id: String) -> Unit,
    // Signature mirrors CartActivity.openKeypadFor: the activity keeps the
    // active EditText reference so it can mirror keypad taps back into it.
    // Second param is intentionally unused for now; kept so the row can
    // supply extra context (e.g., cursor position) without a signature
    // churn later.
    private val onEditExpression: (field: EditText, item: CartItem) -> Unit,
    // Fired when the row's name field gains focus so the activity can
    // dismiss its own slide-up keypad — the system IME is about to open.
    private val onNameFocused: () -> Unit,
) : ListAdapter<CartItem, CartItemAdapter.VH>(DIFF) {

    // The cart's ISO code. Held on the adapter so a currency change can be
    // propagated to every visible row via a lightweight `notifyDataSetChanged`
    // without touching the debounced text watchers on each holder.
    private var currency: String = ""

    fun setCurrency(iso: String) {
        if (iso == currency) return
        currency = iso
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cart_row, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), currency, onChange, onDelete, onEditExpression, onNameFocused)
    }

    class VH(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val nameField: EditText = itemView.findViewById(R.id.cart_row_name)
        private val exprField: EditText = itemView.findViewById(R.id.cart_row_expression)
        private val valueLabel: TextView = itemView.findViewById(R.id.cart_row_value)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.cart_row_delete)

        // Held so we can detach the old listener when bind() rebinds this
        // holder to a new item — otherwise text-changes leak into other rows.
        private var nameWatcher: TextWatcher? = null
        private var exprWatcher: TextWatcher? = null

        // Deferred commit: text-changed fires per keystroke but we only want
        // to persist once the user has paused. postDelayed on the view's
        // handler is fine here — cancelled if another change comes in.
        private val commitRunnable: Runnable = Runnable { commit() }
        private var currentItem: CartItem? = null
        private var onChangeHook: ((String, String, String) -> Unit)? = null

        private var currency: String = ""

        fun bind(
            item: CartItem,
            currency: String,
            onChange: (id: String, name: String, expression: String) -> Unit,
            onDelete: (id: String) -> Unit,
            onEditExpression: (field: EditText, item: CartItem) -> Unit,
            onNameFocused: () -> Unit,
        ) {
            currentItem = item
            onChangeHook = onChange
            this.currency = currency

            nameWatcher?.let { nameField.removeTextChangedListener(it) }
            exprWatcher?.let { exprField.removeTextChangedListener(it) }

            if (nameField.text?.toString() != item.name) nameField.setText(item.name)
            if (exprField.text?.toString() != item.expression) exprField.setText(item.expression)
            renderValue(item)

            nameWatcher = watcher { scheduleCommit(item.copy(name = it)) }
            exprWatcher = watcher {
                val updated = item.copy(expression = it)
                renderValue(updated)
                scheduleCommit(updated)
            }
            nameField.addTextChangedListener(nameWatcher)
            exprField.addTextChangedListener(exprWatcher)

            // Native IME will pop up next — tell the activity so it can slide
            // its own calculator keypad away first.
            nameField.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) onNameFocused()
            }

            // Route expression edits through the app's calculator dialog
            // instead of the system IME. Keep the EditText for its styling
            // and text buffer but suppress focus/keyboard/cursor.
            exprField.showSoftInputOnFocus = false
            exprField.isFocusable = false
            exprField.isClickable = true
            exprField.isCursorVisible = false
            exprField.setOnClickListener { onEditExpression(exprField, item) }

            deleteButton.setOnClickListener { onDelete(item.id) }
        }

        private fun scheduleCommit(next: CartItem) {
            currentItem = next
            itemView.removeCallbacks(commitRunnable)
            itemView.postDelayed(commitRunnable, EDIT_DEBOUNCE_MS)
        }

        private fun commit() {
            val item = currentItem ?: return
            onChangeHook?.invoke(item.id, item.name, item.expression)
        }

        private fun renderValue(item: CartItem) {
            if (item.expression.isBlank()) {
                valueLabel.text = ""
                return
            }
            val value = evaluateItem(item).setScale(ROW_PREVIEW_SCALE, RoundingMode.HALF_EVEN)
            val formatted = value.toHumanReadableNumber(
                valueLabel.context,
                decimalPlaces = ROW_PREVIEW_SCALE,
            )
            valueLabel.text = itemView.context.getString(
                R.string.cart_row_value_format,
                formatted,
                currency,
            )
        }

        private fun watcher(onChanged: (String) -> Unit): TextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                onChanged(s?.toString().orEmpty())
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CartItem>() {
            override fun areItemsTheSame(oldItem: CartItem, newItem: CartItem): Boolean =
                oldItem.id == newItem.id

            // Rows are individually stateful (they hold their own EditText
            // buffers) — payload equality is intentionally shallow: id alone.
            // A "same id" row is *not* rebound if content changed under it,
            // which keeps the cursor from jumping while the user types.
            override fun areContentsTheSame(oldItem: CartItem, newItem: CartItem): Boolean =
                oldItem.id == newItem.id
        }
    }
}
