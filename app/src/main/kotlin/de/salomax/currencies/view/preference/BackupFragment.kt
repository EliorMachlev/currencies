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
import androidx.documentfile.provider.DocumentFile
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import de.salomax.currencies.R
import de.salomax.currencies.repository.BackupManager
import de.salomax.currencies.repository.BackupResult
import de.salomax.currencies.repository.BackupScheduler
import de.salomax.currencies.repository.FREQUENCY_DAILY
import de.salomax.currencies.repository.FREQUENCY_MONTHLY
import de.salomax.currencies.repository.FREQUENCY_OFF
import de.salomax.currencies.repository.FREQUENCY_WEEKLY

// Preference row keys — leading __ marks them as UI-only (not persisted).
private const val PREF_KEY_EXPORT = "__backup_export"
private const val PREF_KEY_IMPORT = "__backup_import"
private const val PREF_KEY_SCHEDULE_FREQ = "__backup_schedule_frequency"
private const val PREF_KEY_SCHEDULE_FOLDER = "__backup_schedule_folder"
private const val PREF_KEY_SCHEDULE_RETENTION = "__backup_schedule_retention"

// Minimum password length for encrypted export. Chosen to nudge users
// toward something that survives a modest offline attack — PBKDF2 stretches
// still buy time, but a 4-char password is broken in seconds.
private const val MIN_PASSWORD_LENGTH = 8

// Frequency + retention choices. Order matters — the index into these arrays
// maps to the checked row in the single-choice dialog.
private val FREQUENCY_VALUES = arrayOf(
    FREQUENCY_OFF, FREQUENCY_DAILY, FREQUENCY_WEEKLY, FREQUENCY_MONTHLY
)
private val RETENTION_VALUES = intArrayOf(1, 3, 5, 10, 20)

class BackupFragment : PreferenceFragmentCompat() {

    private lateinit var backupManager: BackupManager
    private lateinit var exportLauncher: ActivityResultLauncher<Intent>
    private lateinit var importLauncher: ActivityResultLauncher<Intent>
    private lateinit var folderLauncher: ActivityResultLauncher<Intent>

    // The password chosen in the pre-export dialog, held only long enough to
    // hand off to BackupManager (which zeroes it after use).
    private var pendingExportPassword: CharArray? = null

    private var folderPreference: Preference? = null

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
        folderLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            result.data?.data?.let(::onFolderPicked)
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

        val scheduleCategory = addCategory(screen, ctx, R.string.backup_section_schedule)
        scheduleCategory.addPreference(
            buildActionPreference(
                ctx = ctx,
                key = PREF_KEY_SCHEDULE_FREQ,
                titleRes = R.string.backup_schedule_frequency_title,
                summaryRes = 0,
                onClick = ::promptFrequency,
            ).also { it.summary = frequencySummary(BackupScheduler.getFrequency(ctx)) }
        )
        folderPreference = buildActionPreference(
            ctx = ctx,
            key = PREF_KEY_SCHEDULE_FOLDER,
            titleRes = R.string.backup_schedule_folder_title,
            summaryRes = 0,
            onClick = ::launchFolderPicker,
        ).also { it.summary = folderSummary(BackupScheduler.getTreeUri(ctx)) }
        scheduleCategory.addPreference(folderPreference!!)
        scheduleCategory.addPreference(
            buildActionPreference(
                ctx = ctx,
                key = PREF_KEY_SCHEDULE_RETENTION,
                titleRes = R.string.backup_schedule_retention_title,
                summaryRes = 0,
                onClick = ::promptRetention,
            ).also {
                it.summary = getString(
                    R.string.backup_schedule_retention_summary,
                    BackupScheduler.getRetention(ctx)
                )
            }
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
            // summaryRes == 0 means "set the summary dynamically at the call
            // site" (e.g. schedule rows that show current state).
            if (summaryRes != 0) summary = getString(summaryRes)
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

    private fun promptFrequency() {
        val ctx = requireContext()
        val labels = FREQUENCY_VALUES.map { getString(frequencyLabelRes(it)) }.toTypedArray()
        val current = FREQUENCY_VALUES.indexOf(BackupScheduler.getFrequency(ctx)).coerceAtLeast(0)
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.backup_schedule_frequency_title)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                val chosen = FREQUENCY_VALUES[which]
                if (chosen != FREQUENCY_OFF && BackupScheduler.getTreeUri(ctx) == null) {
                    toast(getString(R.string.backup_schedule_needs_folder))
                    dialog.dismiss()
                    return@setSingleChoiceItems
                }
                BackupScheduler.setFrequency(ctx, chosen)
                findPreference<Preference>(PREF_KEY_SCHEDULE_FREQ)?.summary =
                    frequencySummary(chosen)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun launchFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        folderLauncher.launch(intent)
    }

    private fun onFolderPicked(uri: Uri) {
        // Persist the read/write grant across process death and reboots; without
        // this the URI becomes unusable as soon as the picker Activity is gone.
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        requireContext().contentResolver.takePersistableUriPermission(uri, flags)
        BackupScheduler.setTreeUri(requireContext(), uri)
        folderPreference?.summary = folderSummary(uri)
    }

    private fun promptRetention() {
        val ctx = requireContext()
        val labels = RETENTION_VALUES.map { it.toString() }.toTypedArray()
        val current = RETENTION_VALUES.indexOf(BackupScheduler.getRetention(ctx)).coerceAtLeast(0)
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.backup_schedule_retention_title)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                val chosen = RETENTION_VALUES[which]
                BackupScheduler.setRetention(ctx, chosen)
                findPreference<Preference>(PREF_KEY_SCHEDULE_RETENTION)?.summary =
                    getString(R.string.backup_schedule_retention_summary, chosen)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun frequencySummary(frequency: String): String =
        getString(frequencyLabelRes(frequency))

    private fun frequencyLabelRes(frequency: String): Int = when (frequency) {
        FREQUENCY_DAILY -> R.string.backup_schedule_frequency_daily
        FREQUENCY_WEEKLY -> R.string.backup_schedule_frequency_weekly
        FREQUENCY_MONTHLY -> R.string.backup_schedule_frequency_monthly
        else -> R.string.backup_schedule_frequency_off
    }

    private fun folderSummary(uri: Uri?): String {
        if (uri == null) return getString(R.string.backup_schedule_folder_none)
        val name = DocumentFile.fromTreeUri(requireContext(), uri)?.name
        return name ?: uri.toString()
    }

    private fun extractCharArray(input: EditText): CharArray {
        val editable = input.text ?: return CharArray(0)
        // Copy char-by-char rather than `toString().toCharArray()` so the
        // password never lands in the JVM String pool.
        val chars = CharArray(editable.length)
        for (i in 0 until editable.length) chars[i] = editable[i]
        return chars
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }
}
