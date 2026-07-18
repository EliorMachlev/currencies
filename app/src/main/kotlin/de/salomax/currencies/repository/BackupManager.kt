package de.salomax.currencies.repository

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.Instant

// Backup schema version. Bump when the on-disk format changes in a
// non-backwards-compatible way; readers must reject unknown versions.
internal const val BACKUP_SCHEMA_VERSION = 1

// Backup file top-level keys.
private const val KEY_VERSION = "version"
private const val KEY_CREATED_AT = "createdAt"
private const val KEY_APP = "app"
private const val KEY_NAMESPACES = "namespaces"

// Per-entry keys inside each namespace: {"type": "int", "value": 42}.
private const val KEY_TYPE = "type"
private const val KEY_VALUE = "value"

private const val TYPE_STRING = "string"
private const val TYPE_INT = "int"
private const val TYPE_LONG = "long"
private const val TYPE_FLOAT = "float"
private const val TYPE_BOOLEAN = "boolean"
private const val TYPE_STRING_SET = "stringSet"

private const val APP_ID = "de.salomax.currencies"

// SharedPreferences namespaces that carry user-authored state worth backing
// up. The "rates" namespace is intentionally excluded — it's a network cache
// that regenerates itself and would bloat backups.
private val BACKUP_NAMESPACES = listOf("prefs", "last_state", "starred_currencies")

sealed class BackupResult {
    data object Success : BackupResult()
    data class Failure(val message: String) : BackupResult()
}

class BackupManager(private val context: Context) {

    fun export(uri: Uri): BackupResult {
        return try {
            val payload = buildBackupJson().toString(2).toByteArray(Charsets.UTF_8)
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(payload) }
                ?: return BackupResult.Failure("Could not open output stream")
            BackupResult.Success
        } catch (e: IOException) {
            BackupResult.Failure(e.localizedMessage ?: "I/O error")
        } catch (e: SecurityException) {
            BackupResult.Failure(e.localizedMessage ?: "Permission denied")
        }
    }

    fun import(uri: Uri): BackupResult {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return BackupResult.Failure("Could not open input stream")
            val root = JSONObject(String(bytes, Charsets.UTF_8))
            val version = root.optInt(KEY_VERSION, -1)
            if (version != BACKUP_SCHEMA_VERSION) {
                return BackupResult.Failure("Unsupported backup version: $version")
            }
            val namespaces = root.optJSONObject(KEY_NAMESPACES)
                ?: return BackupResult.Failure("Missing 'namespaces' section")
            restoreNamespaces(namespaces)
            BackupResult.Success
        } catch (e: IOException) {
            BackupResult.Failure(e.localizedMessage ?: "I/O error")
        } catch (e: JSONException) {
            BackupResult.Failure(e.localizedMessage ?: "Malformed backup file")
        } catch (e: SecurityException) {
            BackupResult.Failure(e.localizedMessage ?: "Permission denied")
        }
    }

    private fun buildBackupJson(): JSONObject {
        val nsObj = JSONObject()
        BACKUP_NAMESPACES.forEach { name ->
            nsObj.put(name, serializeNamespace(context.getSharedPreferences(name, MODE_PRIVATE)))
        }
        return JSONObject().apply {
            put(KEY_VERSION, BACKUP_SCHEMA_VERSION)
            put(KEY_CREATED_AT, Instant.now().toString())
            put(KEY_APP, APP_ID)
            put(KEY_NAMESPACES, nsObj)
        }
    }

    private fun serializeNamespace(prefs: SharedPreferences): JSONObject {
        val obj = JSONObject()
        prefs.all.forEach { (key, value) ->
            serializeEntry(value)?.let { obj.put(key, it) }
        }
        return obj
    }

    private fun serializeEntry(value: Any?): JSONObject? {
        val (type, payload) = when (value) {
            is String -> TYPE_STRING to value
            is Int -> TYPE_INT to value
            is Long -> TYPE_LONG to value
            is Float -> TYPE_FLOAT to value.toDouble()
            is Boolean -> TYPE_BOOLEAN to value
            is Set<*> -> TYPE_STRING_SET to JSONArray().apply {
                value.forEach { if (it is String) put(it) }
            }
            else -> return null
        }
        return JSONObject().apply {
            put(KEY_TYPE, type)
            put(KEY_VALUE, payload)
        }
    }

    private fun restoreNamespaces(namespaces: JSONObject) {
        BACKUP_NAMESPACES.forEach { name ->
            val nsData = namespaces.optJSONObject(name) ?: return@forEach
            val prefs = context.getSharedPreferences(name, MODE_PRIVATE)
            val editor = prefs.edit().clear()
            nsData.keys().forEach { key ->
                val entry = nsData.optJSONObject(key) ?: return@forEach
                applyEntry(editor, key, entry)
            }
            editor.apply()
        }
    }

    private fun applyEntry(editor: SharedPreferences.Editor, key: String, entry: JSONObject) {
        when (entry.optString(KEY_TYPE)) {
            TYPE_STRING -> editor.putString(key, entry.optString(KEY_VALUE))
            TYPE_INT -> editor.putInt(key, entry.optInt(KEY_VALUE))
            TYPE_LONG -> editor.putLong(key, entry.optLong(KEY_VALUE))
            TYPE_FLOAT -> editor.putFloat(key, entry.optDouble(KEY_VALUE).toFloat())
            TYPE_BOOLEAN -> editor.putBoolean(key, entry.optBoolean(KEY_VALUE))
            TYPE_STRING_SET -> {
                val arr = entry.optJSONArray(KEY_VALUE) ?: return
                val set = HashSet<String>(arr.length())
                for (i in 0 until arr.length()) arr.optString(i).let(set::add)
                editor.putStringSet(key, set)
            }
        }
    }
}
