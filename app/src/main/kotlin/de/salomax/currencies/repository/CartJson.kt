package de.salomax.currencies.repository

import de.salomax.currencies.model.CartItem
import de.salomax.currencies.model.SavedCart
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

// Wire keys for on-disk cart JSON. Kept top-level (not nested inside
// [Database]) so [CartExporter] can share the same serde for the file
// envelope's `cart` payload.
internal const val CART_KEY_ID = "id"
internal const val CART_KEY_NAME = "name"
internal const val CART_KEY_CURRENCY = "currency"
internal const val CART_KEY_ITEMS = "items"
internal const val CART_KEY_CREATED_AT = "createdAt"
internal const val CART_ITEM_KEY_ID = "id"
internal const val CART_ITEM_KEY_NAME = "name"
internal const val CART_ITEM_KEY_EXPR = "expression"

internal fun serializeCart(cart: SavedCart): JSONObject {
    return JSONObject().apply {
        put(CART_KEY_ID, cart.id)
        put(CART_KEY_NAME, cart.name)
        put(CART_KEY_CURRENCY, cart.currency)
        put(CART_KEY_CREATED_AT, cart.createdAt)
        put(CART_KEY_ITEMS, JSONArray().apply {
            cart.items.forEach { put(serializeCartItem(it)) }
        })
    }
}

private fun serializeCartItem(item: CartItem): JSONObject {
    return JSONObject().apply {
        put(CART_ITEM_KEY_ID, item.id)
        put(CART_ITEM_KEY_NAME, item.name)
        put(CART_ITEM_KEY_EXPR, item.expression)
    }
}

internal fun parseCart(obj: JSONObject?): SavedCart? {
    obj ?: return null
    val itemsArr = obj.optJSONArray(CART_KEY_ITEMS) ?: JSONArray()
    val items = (0 until itemsArr.length()).mapNotNull { i ->
        parseCartItem(itemsArr.optJSONObject(i))
    }
    return SavedCart(
        id = obj.optString(CART_KEY_ID, "").ifEmpty { UUID.randomUUID().toString() },
        name = obj.optString(CART_KEY_NAME, ""),
        currency = obj.optString(CART_KEY_CURRENCY, ""),
        items = items,
        createdAt = obj.optLong(CART_KEY_CREATED_AT, System.currentTimeMillis()),
    )
}

internal fun parseCart(json: String?): SavedCart? {
    if (json.isNullOrBlank()) return null
    return try {
        parseCart(JSONObject(json))
    } catch (e: JSONException) {
        null
    }
}

private fun parseCartItem(obj: JSONObject?): CartItem? {
    obj ?: return null
    return CartItem(
        id = obj.optString(CART_ITEM_KEY_ID, "").ifEmpty { UUID.randomUUID().toString() },
        name = obj.optString(CART_ITEM_KEY_NAME, ""),
        expression = obj.optString(CART_ITEM_KEY_EXPR, ""),
    )
}

internal fun serializeCartList(list: List<SavedCart>): String {
    val arr = JSONArray()
    list.forEach { arr.put(serializeCart(it)) }
    return arr.toString()
}

internal fun parseCartList(json: String?): List<SavedCart> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i -> parseCart(arr.optJSONObject(i)) }
    } catch (e: JSONException) {
        emptyList()
    }
}
