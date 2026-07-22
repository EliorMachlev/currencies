package de.salomax.currencies.repository

import android.content.Context
import android.net.Uri
import de.salomax.currencies.model.SavedCart
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.Instant

// Single-cart file envelope. Shares the header keys (version / app /
// createdAt) with [BackupManager], but the payload is one [SavedCart]
// under `cart` instead of the full-app namespaces block.
internal const val CART_FILE_SCHEMA_VERSION = 1
private const val CART_FILE_KEY_TYPE = "type"
private const val CART_FILE_KEY_PAYLOAD = "cart"
private const val CART_FILE_TYPE = "cart"

sealed class CartFileResult {
    data object Success : CartFileResult()
    data class Loaded(val cart: SavedCart) : CartFileResult()
    data class Failure(val message: String) : CartFileResult()
}

/**
 * Reads and writes a single [SavedCart] to a user-chosen file via SAF.
 * Plaintext JSON — cart data isn't secret, and keeping it readable lets a
 * user peek at the file with any text editor.
 */
class CartExporter(private val context: Context) {

    fun export(uri: Uri, cart: SavedCart): CartFileResult {
        return try {
            val root = JSONObject().apply {
                put(BACKUP_KEY_VERSION, CART_FILE_SCHEMA_VERSION)
                put(BACKUP_KEY_APP, BACKUP_APP_ID)
                put(CART_FILE_KEY_TYPE, CART_FILE_TYPE)
                put(BACKUP_KEY_CREATED_AT, Instant.now().toString())
                put(CART_FILE_KEY_PAYLOAD, serializeCart(cart))
            }
            val bytes = root.toString(2).toByteArray(Charsets.UTF_8)
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                ?: return CartFileResult.Failure("Could not open output stream")
            CartFileResult.Success
        } catch (e: IOException) {
            CartFileResult.Failure(e.localizedMessage ?: "I/O error")
        } catch (e: SecurityException) {
            CartFileResult.Failure(e.localizedMessage ?: "Permission denied")
        }
    }

    fun import(uri: Uri): CartFileResult {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return CartFileResult.Failure("Could not open input stream")
            val root = JSONObject(String(bytes, Charsets.UTF_8))
            val version = root.optInt(BACKUP_KEY_VERSION, -1)
            if (version != CART_FILE_SCHEMA_VERSION) {
                return CartFileResult.Failure("Unsupported cart version: $version")
            }
            // `type` is checked defensively so a full-app backup dropped in
            // by mistake gets rejected before we try to parse it as a cart.
            val type = root.optString(CART_FILE_KEY_TYPE)
            if (type != CART_FILE_TYPE) {
                return CartFileResult.Failure("Not a cart file")
            }
            val cart = parseCart(root.optJSONObject(CART_FILE_KEY_PAYLOAD))
                ?: return CartFileResult.Failure("Malformed cart payload")
            CartFileResult.Loaded(cart)
        } catch (e: IOException) {
            CartFileResult.Failure(e.localizedMessage ?: "I/O error")
        } catch (e: JSONException) {
            CartFileResult.Failure(e.localizedMessage ?: "Malformed cart file")
        } catch (e: SecurityException) {
            CartFileResult.Failure(e.localizedMessage ?: "Permission denied")
        }
    }
}
