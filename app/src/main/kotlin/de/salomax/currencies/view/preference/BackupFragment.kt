package de.salomax.currencies.view.preference

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.salomax.currencies.R
import de.salomax.currencies.repository.BackupManager
import de.salomax.currencies.repository.BackupResult

// Preference row keys — leading __ marks them as UI-only (not persisted).
private const val PREF_KEY_EXPORT = "__backup_export"
private const val PREF_KEY_IMPORT = "__backup_import"

class BackupFragment : PreferenceFragmentCompat() {

    private lateinit var backupManager: BackupManager
    private lateinit var exportLauncher: ActivityResultLauncher<Intent>
    private lateinit var importLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backupManager = BackupManager(requireContext().applicationContext)
        exportLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            result.data?.data?.let(::runExport)
        }
        importLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            result.data?.data?.let(::confirmAndImport)
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
                onClick = ::launchExport,
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

    private fun runExport(uri: Uri) {
        val result = backupManager.export(uri)
        toast(
            when (result) {
                is BackupResult.Success -> getString(R.string.backup_export_success)
                is BackupResult.Failure -> getString(R.string.backup_export_failed, result.message)
            }
        )
    }

    private fun confirmAndImport(uri: Uri) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.backup_import_confirm_title)
            .setMessage(R.string.backup_import_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.backup_import_confirm_positive) { _, _ -> runImport(uri) }
            .show()
    }

    private fun runImport(uri: Uri) {
        val result = backupManager.import(uri)
        toast(
            when (result) {
                is BackupResult.Success -> getString(R.string.backup_import_success)
                is BackupResult.Failure -> getString(R.string.backup_import_failed, result.message)
            }
        )
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }
}
