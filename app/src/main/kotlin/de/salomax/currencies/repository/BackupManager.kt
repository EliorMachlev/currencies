package de.salomax.currencies.repository

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// Backup schema version. Bump when the on-disk format changes in a
// non-backwards-compatible way; readers must reject unknown versions.
internal const val BACKUP_SCHEMA_VERSION = 1

// Backup file top-level keys. [BACKUP_KEY_VERSION], [BACKUP_KEY_APP], and
// [BACKUP_KEY_CREATED_AT] live in [BackupSchema] so [CartExporter] can share
// the same envelope shape.
private const val KEY_NAMESPACES = "namespaces"
private const val KEY_ENCRYPTION = "encryption"

// Encryption block keys.
private const val ENC_KDF = "kdf"
private const val ENC_ITERATIONS = "iterations"
private const val ENC_SALT = "salt"
private const val ENC_CIPHER = "cipher"
private const val ENC_IV = "iv"
private const val ENC_CIPHERTEXT = "ciphertext"

// Argon2id-only block keys.
private const val ENC_MEMORY_KIB = "memoryKib"
private const val ENC_PARALLELISM = "parallelism"

// KDF & cipher identifiers stored verbatim in the file so a future reader can
// reject algorithms it doesn't understand instead of silently mis-decrypting.
// New exports use ARGON2ID_ID; PBKDF2_ID is retained only as a reader path
// for files exported by an earlier revision of this branch.
private const val ARGON2ID_ID = "ARGON2ID-v1.3"
private const val PBKDF2_ID = "PBKDF2-HMAC-SHA256"
private const val CIPHER_ID = "AES-256-GCM"

private const val KEY_LENGTH_BITS = 256
private const val KEY_LENGTH_BYTES = KEY_LENGTH_BITS / 8

// Argon2id parameters. OWASP 2023 gives several equivalent-strength profiles;
// we pick m=32 MiB, t=3, p=1 as a balance between mid-range Android RAM
// headroom and cost to an offline attacker with GPU/ASIC. Memory-hardness is
// the property that resists quantum speedups (Grover parallelizes compute,
// not memory bandwidth).
private const val ARGON2_MEMORY_KIB = 32 * 1024
private const val ARGON2_ITERATIONS = 3
private const val ARGON2_PARALLELISM = 1

private const val SALT_LENGTH_BYTES = 32
private const val GCM_IV_LENGTH_BYTES = 12
private const val GCM_TAG_LENGTH_BITS = 128

// Per-entry keys inside each namespace: {"type": "int", "value": 42}.
private const val KEY_TYPE = "type"
private const val KEY_VALUE = "value"

private const val TYPE_STRING = "string"
private const val TYPE_INT = "int"
private const val TYPE_LONG = "long"
private const val TYPE_FLOAT = "float"
private const val TYPE_BOOLEAN = "boolean"
private const val TYPE_STRING_SET = "stringSet"

// SharedPreferences namespaces that carry user-authored state worth backing
// up. The "rates" namespace is intentionally excluded — it's a network cache
// that regenerates itself and would bloat backups.
private val BACKUP_NAMESPACES = listOf("prefs", "last_state", "starred_currencies")

sealed class BackupResult {
    data object Success : BackupResult()
    data class Failure(val message: String) : BackupResult()
    // Import saw an encrypted file and needs a password from the user.
    // The manager itself never prompts — the caller drives the UI.
    data object PasswordRequired : BackupResult()
    // Import saw an encrypted file, tried the supplied password, and the
    // GCM tag failed to verify. Distinct from a generic failure so the UI
    // can re-prompt without treating the file as corrupt.
    data object WrongPassword : BackupResult()
}

class BackupManager(private val context: Context) {

    private val secureRandom = SecureRandom()

    /**
     * Write a backup to [uri]. If [password] is non-null and non-empty, the
     * `namespaces` block is encrypted with a PBKDF2-derived AES-256-GCM key;
     * otherwise the file matches the PR-C plaintext format exactly.
     */
    fun export(uri: Uri, password: CharArray? = null): BackupResult {
        return try {
            val root = buildBackupJson(password)
            val payload = root.toString(2).toByteArray(Charsets.UTF_8)
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(payload) }
                ?: return BackupResult.Failure("Could not open output stream")
            BackupResult.Success
        } catch (e: IOException) {
            BackupResult.Failure(e.localizedMessage ?: "I/O error")
        } catch (e: SecurityException) {
            BackupResult.Failure(e.localizedMessage ?: "Permission denied")
        } catch (e: GeneralSecurityException) {
            BackupResult.Failure(e.localizedMessage ?: "Encryption failed")
        } finally {
            password?.fill('\u0000')
        }
    }

    /**
     * Read a backup from [uri] and restore it into SharedPreferences.
     *
     * Returns [BackupResult.PasswordRequired] if the file is encrypted and no
     * password was supplied, or [BackupResult.WrongPassword] if the supplied
     * password fails the GCM tag check. The caller is expected to re-invoke
     * with a password in either case.
     */
    fun import(uri: Uri, password: CharArray? = null): BackupResult {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return BackupResult.Failure("Could not open input stream")
            val root = JSONObject(String(bytes, Charsets.UTF_8))
            val version = root.optInt(BACKUP_KEY_VERSION, -1)
            if (version != BACKUP_SCHEMA_VERSION) {
                return BackupResult.Failure("Unsupported backup version: $version")
            }
            val namespaces = extractNamespaces(root, password) ?: return BackupResult.PasswordRequired
            restoreNamespaces(namespaces)
            BackupResult.Success
        } catch (e: WrongPasswordException) {
            BackupResult.WrongPassword
        } catch (e: IOException) {
            BackupResult.Failure(e.localizedMessage ?: "I/O error")
        } catch (e: JSONException) {
            BackupResult.Failure(e.localizedMessage ?: "Malformed backup file")
        } catch (e: SecurityException) {
            BackupResult.Failure(e.localizedMessage ?: "Permission denied")
        } catch (e: GeneralSecurityException) {
            BackupResult.Failure(e.localizedMessage ?: "Decryption failed")
        } finally {
            password?.fill('\u0000')
        }
    }

    /** True if the file at [uri] is a valid encrypted backup. */
    fun isEncrypted(uri: Uri): Boolean {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return false
            JSONObject(String(bytes, Charsets.UTF_8)).has(KEY_ENCRYPTION)
        } catch (e: IOException) {
            false
        } catch (e: JSONException) {
            false
        }
    }

    private fun buildBackupJson(password: CharArray?): JSONObject {
        val nsObj = JSONObject()
        BACKUP_NAMESPACES.forEach { name ->
            nsObj.put(name, serializeNamespace(context.getSharedPreferences(name, MODE_PRIVATE)))
        }
        val root = JSONObject().apply {
            put(BACKUP_KEY_VERSION, BACKUP_SCHEMA_VERSION)
            put(BACKUP_KEY_CREATED_AT, Instant.now().toString())
            put(BACKUP_KEY_APP, BACKUP_APP_ID)
        }
        if (password != null && password.isNotEmpty()) {
            root.put(KEY_ENCRYPTION, encryptNamespaces(nsObj, password))
        } else {
            root.put(KEY_NAMESPACES, nsObj)
        }
        return root
    }

    /**
     * @return the decrypted `namespaces` object, or `null` if the file is
     * encrypted and [password] is null/empty (caller must prompt).
     */
    private fun extractNamespaces(root: JSONObject, password: CharArray?): JSONObject? {
        val encBlock = root.optJSONObject(KEY_ENCRYPTION)
        if (encBlock != null) {
            if (password == null || password.isEmpty()) return null
            return decryptNamespaces(encBlock, password)
        }
        return root.optJSONObject(KEY_NAMESPACES)
            ?: throw JSONException("Missing 'namespaces' section")
    }

    private fun encryptNamespaces(namespaces: JSONObject, password: CharArray): JSONObject {
        // Key-IV reuse invariant: on every call we draw a fresh 32-byte salt
        // AND a fresh 12-byte IV from SecureRandom. Because the AES key is
        // derived as Argon2id(password, salt), a new salt yields a new key —
        // so even if two exports somehow drew the same IV, they'd still use
        // distinct keys. This is why the Semgrep GCM heuristic is a
        // false-positive here.
        val salt = ByteArray(SALT_LENGTH_BYTES).also(secureRandom::nextBytes)
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also(secureRandom::nextBytes)
        val key = deriveKeyArgon2id(password, salt, ARGON2_MEMORY_KIB, ARGON2_ITERATIONS, ARGON2_PARALLELISM)
        // nosemgrep: kotlin.lang.security.gcm-detection.gcm-detection
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val plaintext = namespaces.toString().toByteArray(Charsets.UTF_8)
        val ciphertext = cipher.doFinal(plaintext)
        return JSONObject().apply {
            put(ENC_KDF, ARGON2ID_ID)
            put(ENC_MEMORY_KIB, ARGON2_MEMORY_KIB)
            put(ENC_ITERATIONS, ARGON2_ITERATIONS)
            put(ENC_PARALLELISM, ARGON2_PARALLELISM)
            put(ENC_SALT, base64(salt))
            put(ENC_CIPHER, CIPHER_ID)
            put(ENC_IV, base64(iv))
            put(ENC_CIPHERTEXT, base64(ciphertext))
        }
    }

    private fun decryptNamespaces(encBlock: JSONObject, password: CharArray): JSONObject {
        val kdf = encBlock.optString(ENC_KDF)
        val cipherId = encBlock.optString(ENC_CIPHER)
        if (cipherId != CIPHER_ID) {
            throw GeneralSecurityException("Unsupported cipher: $cipherId")
        }
        val salt = decodeBase64(encBlock, ENC_SALT)
        val iv = decodeBase64(encBlock, ENC_IV)
        val ciphertext = decodeBase64(encBlock, ENC_CIPHERTEXT)
        val key = when (kdf) {
            ARGON2ID_ID -> {
                val memoryKib = encBlock.optInt(ENC_MEMORY_KIB, -1)
                val iterations = encBlock.optInt(ENC_ITERATIONS, -1)
                val parallelism = encBlock.optInt(ENC_PARALLELISM, -1)
                if (memoryKib <= 0 || iterations <= 0 || parallelism <= 0) {
                    throw GeneralSecurityException("Invalid Argon2 parameters")
                }
                deriveKeyArgon2id(password, salt, memoryKib, iterations, parallelism)
            }
            PBKDF2_ID -> {
                val iterations = encBlock.optInt(ENC_ITERATIONS, -1)
                if (iterations <= 0) throw GeneralSecurityException("Invalid iteration count")
                deriveKeyPbkdf2(password, salt, iterations)
            }
            else -> throw GeneralSecurityException("Unsupported KDF: $kdf")
        }
        // Decrypt path — same GCM Semgrep heuristic; IV comes from the file
        // and is uniquely paired with its key (see encryptNamespaces).
        // nosemgrep: kotlin.lang.security.gcm-detection.gcm-detection
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val plaintext = try {
            cipher.doFinal(ciphertext)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw WrongPasswordException()
        }
        return JSONObject(String(plaintext, Charsets.UTF_8))
    }

    /**
     * Argon2id is memory-hard, which is the property that neutralises the
     * √N speedup Grover's algorithm gives a quantum attacker against a
     * password-guessing loop — parallelism gains from Grover don't help
     * when memory bandwidth dominates the cost of each guess.
     */
    private fun deriveKeyArgon2id(
        password: CharArray,
        salt: ByteArray,
        memoryKib: Int,
        iterations: Int,
        parallelism: Int,
    ): SecretKeySpec {
        val passwordBytes = password.toUtf8Bytes()
        try {
            val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(memoryKib)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .build()
            val generator = Argon2BytesGenerator().apply { init(params) }
            val out = ByteArray(KEY_LENGTH_BYTES)
            generator.generateBytes(passwordBytes, out)
            return SecretKeySpec(out, "AES")
        } finally {
            passwordBytes.fill(0)
        }
    }

    private fun deriveKeyPbkdf2(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
    ): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        try {
            val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            return SecretKeySpec(raw, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * UTF-8 encode without going through String (which would linger in the
     * String pool). CharArray → ByteArray via NIO CharBuffer.
     */
    private fun CharArray.toUtf8Bytes(): ByteArray {
        val charBuffer = java.nio.CharBuffer.wrap(this)
        val byteBuffer = StandardCharsets.UTF_8.encode(charBuffer)
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        return bytes
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

    private fun base64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decodeBase64(obj: JSONObject, key: String): ByteArray {
        val str = obj.optString(key)
        if (str.isEmpty()) throw GeneralSecurityException("Missing $key")
        return Base64.decode(str, Base64.DEFAULT)
    }
}

private class WrongPasswordException : RuntimeException()
