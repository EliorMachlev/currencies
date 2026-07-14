package de.salomax.currencies.view.preference

import android.content.ActivityNotFoundException
import android.util.Log
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import de.salomax.currencies.BuildConfig
import de.salomax.currencies.R
import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.util.toHumanReadableNumber
import de.salomax.currencies.viewmodel.preference.PreferenceViewModel
import de.salomax.currencies.widget.EditTextSwitchPreference
import de.salomax.currencies.widget.LongSummaryPreference
import java.math.BigDecimal
import java.util.Calendar

@Suppress("unused")
class PreferenceFragment: PreferenceFragmentCompat() {

    private lateinit var viewModel: PreferenceViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.fitsSystemWindows = true
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs, rootKey)
        viewModel = ViewModelProvider(this)[PreferenceViewModel::class.java]
        setupFeePreference()
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
        val feePreference = findPreference<EditTextSwitchPreference>(getString(R.string.fee_key))
        feePreference?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue is String)
                viewModel.setFee(newValue.toBigDecimalOrNull() ?: BigDecimal.ZERO)
            else if (newValue is Boolean)
                viewModel.setFeeEnabled(newValue)
            true
        }
        viewModel.getFee().observe(this) {
            feePreference?.summary = it.toHumanReadableNumber(requireContext(), showPositiveSign = true, suffix = "%")
            feePreference?.text = it.toPlainString()
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
                    (newValue.toString().toIntOrNull() ?: 2).coerceIn(0, 6)
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
                viewModel.setTheme(newValue.toString().toInt())
                true
            }
        }
        findPreference<SwitchPreferenceCompat>(getString(R.string.pure_black_key))?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.setPureBlackEnabled(newValue.toString().toBoolean())
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
                val provider = ApiProvider.fromId(newValue.toString().toIntOrNull() ?: -1)
                viewModel.setApiProvider(provider)
                apiKeyPref?.isVisible = provider == ApiProvider.OPEN_EXCHANGERATES
                true
            }
            if (entry == null) {
                val defaultProvider = ApiProvider.fromId(-1)
                viewModel.setApiProvider(defaultProvider)
                value = defaultProvider.id.toString()
            }
            apiKeyPref?.isVisible = ApiProvider.fromId(value.toIntOrNull() ?: -1) == ApiProvider.OPEN_EXCHANGERATES
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
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sal0max/currencies")))
                true
            }
        }
        findPreference<Preference>(getString(R.string.donate_key))?.apply {
            @Suppress("KotlinConstantConditions")
            isVisible = when (BuildConfig.FLAVOR) {
                "play" -> false
                else -> true
            }
            setOnPreferenceClickListener {
                startActivity(createIntent("https://www.paypal.com/donate?hosted_button_id=2JCY7E99V9DGC"))
                true
            }
        }
        findPreference<Preference>(getString(R.string.rate_key))?.apply {
            @Suppress("KotlinConstantConditions")
            isVisible = when (BuildConfig.FLAVOR) {
                "play" -> true
                else -> false
            }
            setOnPreferenceClickListener {
                try {
                    startActivity(createIntent("market://details?id=de.salomax.currencies"))
                } catch (e: ActivityNotFoundException) {
                    Log.d("PreferenceFragment", "Play Store not available, opening browser", e)
                    startActivity(createIntent("https://play.google.com/store/apps/details?id=de.salomax.currencies"))
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
