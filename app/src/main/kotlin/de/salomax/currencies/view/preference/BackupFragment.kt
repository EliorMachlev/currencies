package de.salomax.currencies.view.preference

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import de.salomax.currencies.R
import de.salomax.currencies.repository.BackupManager
import de.salomax.currencies.repository.BackupResult

// Preference row keys — leading __ marks them as UI-only (not persisted).
private const val PREF_KEY_EXPORT = "__backup_export"
private const val PREF_KEY_IMPORT = "__backup_import"

// Minimum password length for encrypted export. Chosen to nudge users
// toward something that survives a modest offline attack — PBKDF2 stretches
// still buy time, but a 4-char password is broken in seconds.
private const val MIN_PASSWORD_LENGTH = 8

class BackupFragment : PreferenceFragmentCompat() {

    private lateinit var backupManager: BackupManager
    private lateinit var exportLauncher: ActivityResultLauncher<Intent>
    private lateinit var importLauncher: ActivityResultLauncher<Intent>

    // The password chosen in the pre-export dialog, held only long enough to
    // hand off to BackupManager (which zeroes it after use).
    private var pendingExportPassword: CharArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backupManager = BackupManager(requireContext().applicationContext)
        exportLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val uri = result.data?.data
            if (uri != null) runExport(uri, pendingExportPassword)
            pendingExportPassword?.fill('\u0000')
            pendingExportPassword = null
        }
        importLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            result.data?.data?.let(::beginImport)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = preferenceManager.context
        val screen: PreferenceScreen = preferenceManager.createPreferenceScreen(ctx)

        val exportCategory = addCategory(screen, ctx, R.string.backup_section_local)
        exportCategory.addPreference(
            buildActionPreference(
                ctx = ctx,
                key = PREF_KEY_EXPORT,
                titleRes = R.string.backup_export_title,
                summaryRes = R.string.backup_export_summary,
                onClick = ::promptExportPassword,
            )
        )

        val restoreCategory = addCategory(screen, ctx, R.string.backup_section_restore)
        restoreCategory.addPreference(
            buildActionPreference(
                ctx = ctx,
                key = PREF_KEY_IMPORT,
                titleRes = R.string.backup_import_title,
                summaryRes = R.string.backup_import_summary,
                onClick = ::launchImport,
            )
        )

        preferenceScreen = screen
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = getString(R.string.backup_manager_title)
    }

    override fun onDestroy() {
        pendingExportPassword?.fill('\u0000')
        pendingExportPassword = null
        super.onDestroy()
    }

    private fun addCategory(screen: PreferenceScreen, ctx: Context, titleRes: Int): PreferenceCategory =
        PreferenceCategory(ctx).apply {
            title = getString(titleRes)
            isIconSpaceReserved = false
        }.also(screen::addPreference)

    private fun buildActionPreference(
        ctx: Context,
        key: String,
        titleRes: Int,
        summaryRes: Int,
        onClick: () -> Unit,
    ): Preference =
        Preference(ctx).apply {
            this.key = key
            title = getString(titleRes)
            summary = getString(summaryRes)
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                onClick()
                true
            }
        }

    /**
     * Ask whether to encrypt, then launch the SAF picker. The password (if
     * any) is stashed in [pendingExportPassword] and picked up when the
     * picker returns — SAF doesn't let us pass extras through.
     */
    private fun promptExportPassword() {
        val ctx = requireContext()
        val padH = resources.getDimensionPixelSize(R.dimen.margin3x)
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padH, padH, 0)
        }
        val encryptCheck = CheckBox(ctx).apply { text = getString(R.string.backup_encrypt_option) }
        val passwordLayout = TextInputLayout(ctx).apply {
            hint = getString(R.string.backup_password_hint)
            visibility = View.GONE
        }
        val passwordInput = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        passwordLayout.addView(passwordInput)
        container.addView(encryptCheck)
        container.addView(passwordLayout)

        encryptCheck.setOnCheckedChangeListener { _, isChecked ->
            passwordLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.backup_export_title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val password = if (encryptCheck.isChecked) {
                    val chars = extractCharArray(passwordInput)
                    if (chars.size < MIN_PASSWORD_LENGTH) {
                        chars.fill('\u0000')
                        toast(getString(R.string.backup_password_too_short, MIN_PASSWORD_LENGTH))
                        return@setPositiveButton
                    }
                    chars
                } else null
                pendingExportPassword = password
                launchExport()
            }
            .show()
    }

    private fun launchExport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = getString(R.string.backup_mime_json)
            putExtra(Intent.EXTRA_TITLE, getString(R.string.backup_default_filename))
        }
        exportLauncher.launch(intent)
    }

    private fun launchImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = getString(R.string.backup_mime_json)
        }
        importLauncher.launch(intent)
    }

    private fun runExport(uri: Uri, password: CharArray?) {
        val result = backupManager.export(uri, password)
        toast(
            when (result) {
                is BackupResult.Success -> getString(R.string.backup_export_success)
                is BackupResult.Failure -> getString(R.string.backup_export_failed, result.message)
                // export never asks for a password / never sees WrongPassword
                is BackupResult.PasswordRequired,
                is BackupResult.WrongPassword ->
                    getString(R.string.backup_export_failed, "unexpected state")
            }
        )
    }

    private fun beginImport(uri: Uri) {
        if (backupManager.isEncrypted(uri)) {
            promptImportPassword(uri, isRetry = false)
        } else {
            confirmAndImport(uri, password = null)
        }
    }

    private fun promptImportPassword(uri: Uri, isRetry: Boolean) {
        val ctx = requireContext()
        val padH = resources.getDimensionPixelSize(R.dimen.margin3x)
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padH, padH, 0)
        }
        if (isRetry) {
            container.addView(TextView(ctx).apply {
                text = getString(R.string.backup_password_wrong)
                gravity = Gravity.CENTER
            })
        }
        val passwordLayout = TextInputLayout(ctx).apply {
            hint = getString(R.string.backup_password_hint)
        }
        val passwordInput = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        passwordLayout.addView(passwordInput)
        container.addView(passwordLayout)

        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.backup_password_prompt_title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val password = extractCharArray(passwordInput)
                confirmAndImport(uri, password)
            }
            .show()
    }

    private fun confirmAndImport(uri: Uri, password: CharArray?) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.backup_import_confirm_title)
            .setMessage(R.string.backup_import_confirm_message)
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                password?.fill('\u0000')
            }
            .setPositiveButton(R.string.backup_import_confirm_positive) { _, _ ->
                runImport(uri, password)
            }
            .show()
    }

    private fun runImport(uri: Uri, password: CharArray?) {
        when (val result = backupManager.import(uri, password)) {
            is BackupResult.Success -> toast(getString(R.string.backup_import_success))
            is BackupResult.Failure -> toast(getString(R.string.backup_import_failed, result.message))
            is BackupResult.PasswordRequired -> promptImportPassword(uri, isRetry = false)
            is BackupResult.WrongPassword -> promptImportPassword(uri, isRetry = true)
        }
    }

    private fun extractCharArray(input: EditText): CharArray {
        val editable = input.text ?: return CharArray(0)
        val chars = CharArray(editable.length)
        editable.getChars(0, editable.length, chars, 0)
        return chars
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }
}
