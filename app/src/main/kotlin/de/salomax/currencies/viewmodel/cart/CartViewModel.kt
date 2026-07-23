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
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Fee
import de.salomax.currencies.model.FeeCalculator
import de.salomax.currencies.model.FeeSide
import de.salomax.currencies.model.SavedCart
import de.salomax.currencies.repository.Database
import de.salomax.currencies.util.evaluateCalculatorExpression
import java.math.BigDecimal
import java.math.MathContext
import java.util.UUID

private const val KEYBOARD_TYPE_BASIC = 0

class CartViewModel(app: Application) : AndroidViewModel(app) {

    private val db = Database(app)

    // The session cart. Backed by the `_cart_current_json` pref, so it
    // survives process death but can be cleared explicitly.
    private val current: MutableLiveData<SavedCart> = MutableLiveData(loadCurrentOrEmpty())

    private val fees: LiveData<List<Fee>> = db.getFees()
    private val rates: LiveData<ExchangeRates?> = db.getExchangeRates()
    private val feeSide: LiveData<FeeSide> = db.getFeeSide()

    // Cache the last-known fee list, rates, and fee side so synchronous
    // callers (share text, fee line rendering) can reflect the same totals
    // the UI shows without a suspend hop.
    private var lastFees: List<Fee> = emptyList()
    private var lastRates: ExchangeRates? = null
    private var lastFeeSide: FeeSide = FeeSide.ORIGINAL
    private val feesObserver = Observer<List<Fee>> { lastFees = it }
    private val ratesObserver = Observer<ExchangeRates?> { lastRates = it }
    private val feeSideObserver = Observer<FeeSide> { lastFeeSide = it }

    init {
        fees.observeForever(feesObserver)
        rates.observeForever(ratesObserver)
        feeSide.observeForever(feeSideObserver)
    }

    override fun onCleared() {
        fees.removeObserver(feesObserver)
        rates.removeObserver(ratesObserver)
        feeSide.removeObserver(feeSideObserver)
        super.onCleared()
    }

    fun getCurrentCart(): LiveData<SavedCart> = current

    fun getSavedCarts(): LiveData<List<SavedCart>> = db.getSavedCarts()

    /**
     * Synchronous snapshot for one-shot menu flows (Load / Manage) — the
     * LiveData accessor is a fresh instance per call and never gets observed
     * from those flows, so its `.value` is always null.
     */
    fun getSavedCartsSnapshot(): List<SavedCart> = db.getSavedCartsBlocking()

    fun getFees(): LiveData<List<Fee>> = fees

    fun getExchangeRates(): LiveData<ExchangeRates?> = rates

    fun getFeeSide(): LiveData<FeeSide> = feeSide

    /**
     * Whether the extended calculator keypad is enabled in settings. Same
     * source of truth as the main screen so the cart's slide-up keypad
     * shows the same layout the user picked.
     */
    val isExtendedKeypadEnabled: LiveData<Boolean> =
        db.getKeyboardType().map { it != KEYBOARD_TYPE_BASIC }

    /** Shared with the main screen — same preference gates haptics everywhere. */
    val isHapticFeedbackEnabled: LiveData<Boolean> = db.isHapticFeedbackEnabled()

    fun setFeeSide(side: FeeSide) = db.setFeeSide(side)

    fun getBaseCurrency(): LiveData<Currency> = current.map { resolveCurrency(it.currency) }

    /** Destination for the running total. Falls back to base when unset. */
    fun getDestinationCurrency(): LiveData<Currency> = current.map {
        resolveCurrency(it.destinationCurrency ?: it.currency)
    }

    /** Subtotal from summing every item's evaluated expression in the base currency. */
    fun getSubtotal(): LiveData<BigDecimal> = current.map { subtotalOf(it) }

    /**
     * Total in the destination currency: subtotal → converted at cached rates
     * → adjusted by the fee stack according to the selected [FeeSide].
     */
    fun getTotal(): LiveData<BigDecimal> = MediatorLiveData<BigDecimal>().apply {
        val recompute = {
            value = totalOf(
                current.value,
                fees.value.orEmpty(),
                rates.value,
                feeSide.value ?: FeeSide.ORIGINAL,
            )
        }
        addSource(current) { recompute() }
        addSource(fees) { recompute() }
        addSource(rates) { recompute() }
        addSource(feeSide) { recompute() }
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

    fun setBaseCurrency(currency: Currency) {
        mutate { it.copy(currency = currency.iso4217Alpha()) }
    }

    fun setDestinationCurrency(currency: Currency) {
        mutate { it.copy(destinationCurrency = currency.iso4217Alpha()) }
    }

    /** Swap base and destination; if destination was unset, mirror the base first. */
    fun swapCurrencies() {
        mutate {
            val currentDest = it.destinationCurrency ?: it.currency
            it.copy(currency = currentDest, destinationCurrency = it.currency)
        }
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

    /** Rename a saved cart in place. No-op if the id isn't found. */
    fun renameSaved(id: String, name: String) {
        val existing = db.getSavedCartsBlocking().firstOrNull { it.id == id } ?: return
        db.saveCart(existing.copy(name = name))
        // Keep the current cart's displayed name in sync if it's the same one.
        if (current.value?.id == id) {
            setCurrent((current.value ?: return).copy(name = name))
        }
    }

    /**
     * Replace the current cart wholesale (used by "Load" and by the file
     * importer). Persists immediately so the change survives process death.
     */
    fun setCurrent(cart: SavedCart) {
        current.value = cart
        db.setCurrentCart(cart)
    }

    /** Snapshot used by the "Share" flow — computed against the latest fees & rates. */
    fun snapshotForShare(): CartSnapshot? {
        val cart = current.value ?: return null
        if (cart.items.isEmpty()) return null
        val evaluated = cart.items.map { it to evaluateItem(it) }
        val subtotal = evaluated.fold(BigDecimal.ZERO) { acc, (_, value) -> acc + value }
        val base = resolveCurrency(cart.currency)
        val dest = cart.destinationCurrency?.let { resolveCurrency(it) } ?: base
        val stack = FeeCalculator.totalStack(lastFees, base, dest)
        val converted = convertAmount(subtotal, base, dest, lastRates)
        val total = applyFeeSide(converted, stack, lastFeeSide)
        return CartSnapshot(
            cart, evaluated, subtotal, converted, stack, total,
            lastFees, base, dest, lastFeeSide,
        )
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
        // Prefer the app-wide "last base / destination currency" so a fresh
        // cart lands on the pair the user is already thinking in.
        val base = db.getLastBaseCurrency().value?.iso4217Alpha() ?: Currency.USD.iso4217Alpha()
        val dest = db.getLastDestinationCurrency().value?.iso4217Alpha()
        return SavedCart(
            id = "",
            name = "",
            currency = base,
            destinationCurrency = dest.takeIf { it != null && it != base },
            items = emptyList(),
            createdAt = System.currentTimeMillis(),
        )
    }

    private fun subtotalOf(cart: SavedCart?): BigDecimal {
        cart ?: return BigDecimal.ZERO
        return cart.items.fold(BigDecimal.ZERO) { acc, item -> acc + evaluateItem(item) }
    }

    private fun totalOf(
        cart: SavedCart?,
        feeList: List<Fee>,
        rates: ExchangeRates?,
        side: FeeSide,
    ): BigDecimal {
        cart ?: return BigDecimal.ZERO
        val base = resolveCurrency(cart.currency)
        val dest = cart.destinationCurrency?.let { resolveCurrency(it) } ?: base
        val subtotal = subtotalOf(cart)
        val converted = convertAmount(subtotal, base, dest, rates)
        val stack = FeeCalculator.totalStack(feeList, base, dest)
        return applyFeeSide(converted, stack, side)
    }

    /**
     * Fold [stack] into [converted] according to [side]. Mirrors main-screen
     * semantics: ORIGINAL shows fees as an outflow markup (multiply), CONVERTED
     * bakes them into the destination amount (divide, i.e. what you'd receive).
     */
    private fun applyFeeSide(
        converted: BigDecimal,
        stack: BigDecimal,
        side: FeeSide,
    ): BigDecimal {
        if (stack.compareTo(BigDecimal.ZERO) == 0) return converted
        return when (side) {
            FeeSide.ORIGINAL -> converted.multiply(stack, MathContext.DECIMAL128)
            FeeSide.CONVERTED -> converted.divide(stack, MathContext.DECIMAL128)
        }
    }

    /**
     * Persisted ISO codes are strings, so unknown values (legacy carts,
     * imported files) can slip through and return `null` from
     * [Currency.fromString]. Fall back to USD in that case so downstream
     * math and UI stay non-null instead of exploding on an edge case.
     */
    private fun resolveCurrency(iso: String): Currency =
        Currency.fromString(iso) ?: Currency.USD

    /**
     * Convert [amount] from [base] to [dest] using cached rates. Returns
     * [amount] unchanged when base == dest or when the pair's rate is
     * missing — the latter avoids showing "0" while rates trickle in.
     */
    private fun convertAmount(
        amount: BigDecimal,
        base: Currency,
        dest: Currency,
        rates: ExchangeRates?,
    ): BigDecimal {
        if (base == dest) return amount
        val baseRate = rates?.rates?.find { it.currency == base }?.value ?: return amount
        val destRate = rates.rates.find { it.currency == dest }?.value ?: return amount
        return amount.divide(baseRate, MathContext.DECIMAL128).multiply(destRate)
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
    /** Sum of evaluated items in the base currency. */
    val subtotal: BigDecimal,
    /** Subtotal after currency conversion, before fees. Equals [subtotal] when base == dest. */
    val convertedSubtotal: BigDecimal,
    val feeStack: BigDecimal,
    /** Final displayed total in the destination currency. */
    val total: BigDecimal,
    val fees: List<Fee>,
    val baseCurrency: Currency,
    val destinationCurrency: Currency,
    val feeSide: FeeSide,
) {
    val isConverting: Boolean get() = baseCurrency != destinationCurrency
}
