package de.salomax.currencies.viewmodel.cart

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import de.salomax.currencies.model.CartItem
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.Fee
import de.salomax.currencies.model.FeeCalculator
import de.salomax.currencies.model.SavedCart
import de.salomax.currencies.repository.Database
import de.salomax.currencies.util.evaluateCalculatorExpression
import java.math.BigDecimal
import java.math.MathContext
import java.util.UUID

class CartViewModel(app: Application) : AndroidViewModel(app) {

    private val db = Database(app)

    // The session cart. Backed by the `_cart_current_json` pref, so it
    // survives process death but can be cleared explicitly.
    private val current: MutableLiveData<SavedCart> = MutableLiveData(loadCurrentOrEmpty())

    private val fees: LiveData<List<Fee>> = db.getFees()

    // Cache the last-known fee list so synchronous callers (share text) can
    // reflect the same total the UI is showing without a suspend hop.
    private var lastFees: List<Fee> = emptyList()
    private val feesObserver = Observer<List<Fee>> { lastFees = it }

    init {
        fees.observeForever(feesObserver)
    }

    override fun onCleared() {
        fees.removeObserver(feesObserver)
        super.onCleared()
    }

    fun getCurrentCart(): LiveData<SavedCart> = current

    fun getSavedCarts(): LiveData<List<SavedCart>> = db.getSavedCarts()

    fun getFees(): LiveData<List<Fee>> = fees

    /** Subtotal from summing every item's evaluated expression, ignoring fees. */
    fun getSubtotal(): LiveData<BigDecimal> = current.map { subtotalOf(it) }

    /** Subtotal * fee stack (global fees only — base == dest for a cart). */
    fun getTotal(): LiveData<BigDecimal> = MediatorLiveData<BigDecimal>().apply {
        val recompute = {
            value = totalOf(current.value, fees.value ?: emptyList())
        }
        addSource(current) { recompute() }
        addSource(fees) { recompute() }
    }

    fun addItem(name: String, expression: String) {
        mutate { cart ->
            cart.copy(
                items = cart.items + CartItem(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    expression = expression,
                )
            )
        }
    }

    fun updateItem(id: String, name: String, expression: String) {
        mutate { cart ->
            cart.copy(items = cart.items.map {
                if (it.id == id) it.copy(name = name, expression = expression) else it
            })
        }
    }

    fun removeItem(id: String) {
        mutate { cart -> cart.copy(items = cart.items.filter { it.id != id }) }
    }

    fun setCurrency(currency: Currency) {
        mutate { it.copy(currency = currency.iso4217Alpha()) }
    }

    /** Clear the cart's items but keep the currency the user picked. */
    fun clearItems() {
        mutate { it.copy(items = emptyList()) }
    }

    /**
     * Persist the current cart as a named entry. If [id] matches an existing
     * saved cart, that entry is replaced (rename / update semantics).
     */
    fun saveCurrentAs(name: String, id: String? = null) {
        val cart = current.value ?: return
        val saved = cart.copy(
            id = id ?: UUID.randomUUID().toString(),
            name = name,
            createdAt = System.currentTimeMillis(),
        )
        db.saveCart(saved)
        // Keep the current cart in sync with what was just persisted so a
        // subsequent "Save as" reuses the same id (overwrite semantics).
        setCurrent(saved)
    }

    fun loadSaved(id: String) {
        val saved = db.getSavedCartsBlocking().firstOrNull { it.id == id } ?: return
        // Treat "loaded" as a fresh session — the loaded cart becomes the
        // current cart, but its stored id/name are kept so a subsequent
        // "Save" overwrites the same entry.
        setCurrent(saved)
    }

    fun deleteSaved(id: String) = db.deleteSavedCart(id)

    /**
     * Replace the current cart wholesale (used by "Load" and by the file
     * importer). Persists immediately so the change survives process death.
     */
    fun setCurrent(cart: SavedCart) {
        current.value = cart
        db.setCurrentCart(cart)
    }

    /** Snapshot used by the "Share" flow — computed against the latest fees. */
    fun snapshotForShare(): CartSnapshot? {
        val cart = current.value ?: return null
        if (cart.items.isEmpty()) return null
        val evaluated = cart.items.map { it to evaluateItem(it) }
        val subtotal = evaluated.fold(BigDecimal.ZERO) { acc, (_, value) -> acc + value }
        val stack = feeStackFor(cart, lastFees)
        val total = subtotal.multiply(stack, MathContext.DECIMAL128)
        return CartSnapshot(cart, evaluated, subtotal, stack, total, lastFees)
    }

    private fun mutate(transform: (SavedCart) -> SavedCart) {
        val next = transform(current.value ?: emptyCart())
        current.value = next
        db.setCurrentCart(next)
    }

    private fun loadCurrentOrEmpty(): SavedCart {
        return db.getCurrentCartBlocking() ?: emptyCart()
    }

    private fun emptyCart(): SavedCart {
        // Prefer the app-wide "last base currency" so a fresh cart lands on
        // the currency the user is already thinking in.
        val ccy = db.getLastBaseCurrency().value?.iso4217Alpha() ?: Currency.USD.iso4217Alpha()
        return SavedCart(
            id = "",
            name = "",
            currency = ccy,
            items = emptyList(),
            createdAt = System.currentTimeMillis(),
        )
    }

    private fun subtotalOf(cart: SavedCart?): BigDecimal {
        cart ?: return BigDecimal.ZERO
        return cart.items.fold(BigDecimal.ZERO) { acc, item -> acc + evaluateItem(item) }
    }

    private fun totalOf(cart: SavedCart?, feeList: List<Fee>): BigDecimal {
        cart ?: return BigDecimal.ZERO
        return subtotalOf(cart).multiply(feeStackFor(cart, feeList), MathContext.DECIMAL128)
    }

    private fun feeStackFor(cart: SavedCart, feeList: List<Fee>): BigDecimal {
        val ccy = Currency.fromString(cart.currency)
        // A cart is single-currency, so base == dest. This collapses the
        // stack to just the global fees; pair-specific fees filter out.
        return FeeCalculator.totalStack(feeList, ccy, ccy)
    }
}

fun evaluateItem(item: CartItem): BigDecimal {
    val raw = item.expression.trim()
    if (raw.isEmpty()) return BigDecimal.ZERO
    return runCatching { raw.evaluateCalculatorExpression().toBigDecimal() }
        .getOrDefault(BigDecimal.ZERO)
}

data class CartSnapshot(
    val cart: SavedCart,
    val evaluatedItems: List<Pair<CartItem, BigDecimal>>,
    val subtotal: BigDecimal,
    val feeStack: BigDecimal,
    val total: BigDecimal,
    val fees: List<Fee>,
)
