package de.salomax.currencies.view.preference

import android.content.ActivityNotFoundException
import android.util.Log
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.salomax.currencies.BuildConfig
import de.salomax.currencies.R
import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.util.DECIMAL_PLACES_DEFAULT
import de.salomax.currencies.util.DECIMAL_PLACES_MAX
import de.salomax.currencies.util.DECIMAL_PLACES_MIN
import de.salomax.currencies.repository.Database
import de.salomax.currencies.view.RestartActivity
import de.salomax.currencies.viewmodel.preference.PreferenceViewModel
import de.salomax.currencies.viewmodel.preference.applyLauncherAliasState
import de.salomax.currencies.viewmodel.preference.launcherAliasName
import de.salomax.currencies.widget.LongSummaryPreference
import java.util.Calendar

private const val TAG = "PreferenceFragment"

// Sentinel returned by ApiProvider.fromId when the stored value is unknown /
// unset; the pref layer treats this as "use the default provider".
private const val UNKNOWN_PROVIDER_ID = -1

// Build flavor served through Play; other flavors get the donation entry
// instead of the "rate on Play" entry.
private const val FLAVOR_PLAY = "play"

private const val URL_SOURCE_CODE = "https://github.com/sal0max/currencies"
private const val URL_DONATE = "https://www.paypal.com/donate?hosted_button_id=2JCY7E99V9DGC"
private const val URL_PLAY_MARKET = "market://details?id=de.salomax.currencies"
private const val URL_PLAY_WEB = "https://play.google.com/store/apps/details?id=de.salomax.currencies"

@Suppress("unused")
class PreferenceFragment: PreferenceFragmentCompat() {

    private lateinit var viewModel: PreferenceViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.setTitle(R.string.title_preferences)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs, rootKey)
        viewModel = ViewModelProvider(this)[PreferenceViewModel::class.java]
        setupFeePreference()
        setupBackupPreference()
        setupDisplayPreferences()
        setupGraphOptionsPreference()
        setupApiPreferences()
        setupAboutPreferences()
    }

    private fun setupGraphOptionsPreference() {
        findPreference<Preference>(getString(R.string.graph_options_key))?.apply {
            setOnPreferenceClickListener {
                GraphOptionsDialog().show(childFragmentManager, null)
                true
            }
        }
    }

    private fun setupFeePreference() {
        pushFragmentOnClick(R.string.fee_key, ::FeeManagerFragment)
    }

    private fun setupBackupPreference() {
        pushFragmentOnClick(R.string.backup_key, ::BackupFragment)
    }

    private fun pushFragmentOnClick(keyRes: Int, factory: () -> Fragment) {
        findPreference<Preference>(getString(keyRes))?.setOnPreferenceClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.preferences_fragment, factory())
                .addToBackStack(null)
                .commit()
            true
        }
    }

    private fun setupDisplayPreferences() {
        findPreference<SwitchPreferenceCompat>(getString(R.string.previewConversion_key))?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.setPreviewConversionEnabled(newValue.toString().toBoolean())
                true
            }
        }
        findPreference<ListPreference>(getString(R.string.keyboard_key))?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.setKeyboardType(newValue.toString().toIntOrNull() ?: 0)
                true
            }
        }
        findPreference<ListPreference>(getString(R.string.decimal_places_key))?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.setDecimalPlaces(
                    (newValue.toString().toIntOrNull() ?: DECIMAL_PLACES_DEFAULT)
                        .coerceIn(DECIMAL_PLACES_MIN, DECIMAL_PLACES_MAX)
                )
                true
            }
        }
        findPreference<SwitchPreferenceCompat>(getString(R.string.haptic_feedback_key))?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.setHapticFeedbackEnabled(newValue.toString().toBoolean())
                true
            }
        }
        findPreference<ListPreference>(getString(R.string.theme_key))?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                // In-place recreate for Dark ↔ OLED is fragile (the launcher-
                // alias swap can tear down the current task). Prompt the user
                // to restart instead; night-mode changes still recreate
                // automatically via AppCompatDelegate.setDefaultNightMode.
                val needsRestart = viewModel.setTheme(newValue.toString().toInt())
                if (needsRestart) promptRestart()
                true
            }
        }
        findPreference<LanguagePickerPreference>(getString(R.string.language_key))?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.setLanguage(newValue.toString())
                true
            }
        }
        findPreference<ListPreference>(getString(R.string.date_format_key))?.apply {
            summaryProvider = Preference.SummaryProvider<ListPreference> { pref -> pref.value }
        }
    }

    private fun setupApiPreferences() {
        val apiKeyPref = findPreference<EditTextPreference>(getString(R.string.api_open_exchangerates_id_key))
        apiKeyPref?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.setOpenExchangeratesApiKey(newValue.toString().trim())
                true
            }
            dialogMessage = getText(R.string.api_open_exchangerates_api_key_message)
        }
        viewModel.getOpenExchangeratesApiKey().observe(this) { id ->
            apiKeyPref?.summaryProvider = Preference.SummaryProvider<EditTextPreference> {
                if (id.isNullOrBlank()) getText(R.string.api_open_exchangerates_api_key_missing) else id
            }
        }
        findPreference<ProviderPickerPreference>(getString(R.string.api_key))?.apply {
            val providers = ApiProvider.entries
            entries = providers.map { it.getName() }.toTypedArray()
            entryValues = providers.map { it.id.toString() }.toTypedArray()
            setOnPreferenceChangeListener { _, newValue ->
                val provider = ApiProvider.fromId(newValue.toString().toIntOrNull() ?: UNKNOWN_PROVIDER_ID)
                viewModel.setApiProvider(provider)
                apiKeyPref?.isVisible = provider == ApiProvider.OPEN_EXCHANGERATES
                true
            }
            if (entry == null) {
                val defaultProvider = ApiProvider.fromId(UNKNOWN_PROVIDER_ID)
                viewModel.setApiProvider(defaultProvider)
                value = defaultProvider.id.toString()
            }
            apiKeyPref?.isVisible = ApiProvider.fromId(value.toIntOrNull() ?: UNKNOWN_PROVIDER_ID) == ApiProvider.OPEN_EXCHANGERATES
        }
        viewModel.getApiProvider().observe(this) {
            findPreference<LongSummaryPreference>(getString(R.string.key_apiProvider))?.apply {
                title = resources.getString(R.string.api_about_title, it.getName())
                summary = it.getDescriptionLong(context)
            }
            findPreference<LongSummaryPreference>(getString(R.string.key_refreshPeriod))?.summary =
                it.getDescriptionUpdateInterval(requireContext())
        }
    }

    private fun setupAboutPreferences() {
        findPreference<Preference>(getString(R.string.sourcecode_key))?.apply {
            setOnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(URL_SOURCE_CODE)))
                true
            }
        }
        findPreference<Preference>(getString(R.string.donate_key))?.apply {
            @Suppress("KotlinConstantConditions")
            isVisible = BuildConfig.FLAVOR != FLAVOR_PLAY
            setOnPreferenceClickListener {
                startActivity(createIntent(URL_DONATE))
                true
            }
        }
        findPreference<Preference>(getString(R.string.rate_key))?.apply {
            @Suppress("KotlinConstantConditions")
            isVisible = BuildConfig.FLAVOR == FLAVOR_PLAY
            setOnPreferenceClickListener {
                try {
                    startActivity(createIntent(URL_PLAY_MARKET))
                } catch (e: ActivityNotFoundException) {
                    Log.d(TAG, "Play Store not available, opening browser", e)
                    startActivity(createIntent(URL_PLAY_WEB))
                }
                true
            }
        }
        findPreference<Preference>(getString(R.string.changelog_key))?.apply {
            setOnPreferenceClickListener {
                ChangelogDialog().show(childFragmentManager, null)
                true
            }
        }
        findPreference<Preference>(getString(R.string.version_key))?.apply {
            title = BuildConfig.VERSION_NAME
            summary = getString(R.string.version_summary, Calendar.getInstance().get(Calendar.YEAR).toString())
        }
    }

    private fun promptRestart() {
        val ctx = context ?: return
        // Cancelable so back / outside-tap dismisses — those paths are
        // treated as "Later" (no listener, nothing happens). Only the
        // positive button actually restarts.
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.theme_restart_title)
            .setMessage(R.string.theme_restart_message)
            .setCancelable(true)
            .setNegativeButton(R.string.theme_restart_negative, null)
            .setOnCancelListener(null)
            .setPositiveButton(R.string.theme_restart_positive) { _, _ -> restartApp() }
            .show()
    }

    // Relaunch via RestartActivity — a headless helper that lives in its
    // own ":restart" process. AlarmManager-based relaunches are unreliable
    // on Android 12+ due to background-activity-start restrictions, so we
    // hand off to a process that outlives ours and then fires the launcher
    // intent as a foreground activity start.
    //
    // The alias swap happens here (not deferred to Application.onCreate on
    // the next launch) for two reasons:
    //  1. The relaunch intent needs to resolve to the alias whose static
    //     theme matches the new setting, so we target it explicitly.
    //  2. If the user reopens manually before the relaunch completes, the
    //     launcher must already point at the correct alias; otherwise
    //     Application.onCreate would disable the alias that just rooted
    //     the fresh task and some Android versions kill it despite
    //     DONT_KILL_APP.
    // We're about to kill our own process, so the alias swap's own
    // "may need restart" side effect is a non-issue here.
    private fun restartApp() {
        val ctx = context?.applicationContext ?: return
        val pureBlack = Database(ctx).isPureBlackEnabled()
        applyLauncherAliasState(ctx, pureBlack)
        val target = Intent().apply {
            setClassName(ctx, launcherAliasName(pureBlack))
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val restart = Intent(ctx, RestartActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(RestartActivity.EXTRA_TARGET_INTENT, target)
            putExtra(RestartActivity.EXTRA_MAIN_PID, Process.myPid())
        }
        ctx.startActivity(restart)
    }

    private fun createIntent(url: String): Intent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NO_HISTORY
                    or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                    or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
        )
        return intent
    }

}
