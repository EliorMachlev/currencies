package de.salomax.currencies.model

/**
 * One line in a shopping cart: a display [name] and a raw calculator
 * [expression] like `"1.99"`, `"2 × 3.50"`, or `"10 + 5%"`. The expression
 * is stored verbatim so the user can re-edit it — evaluation happens at
 * display time via `String.evaluateCalculatorExpression()`.
 */
data class CartItem(
    val id: String,
    val name: String,
    val expression: String,
)

/**
 * A named, persisted cart. The "current" (session) cart is stored under its
 * own preferences key with an empty [id]/[name] — only carts the user has
 * explicitly saved get a real id and name. [currency] is stored as an
 * ISO-4217 alpha code (not [Currency]) so a legacy or unknown code round-trips
 * through disk without dropping the cart.
 */
data class SavedCart(
    val id: String,
    val name: String,
    val currency: String,
    val items: List<CartItem>,
    val createdAt: Long,
)
