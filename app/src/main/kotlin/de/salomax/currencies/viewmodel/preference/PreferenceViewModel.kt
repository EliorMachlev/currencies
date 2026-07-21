package de.salomax.currencies.viewmodel.preference

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.repository.Database
import de.salomax.currencies.repository.ExchangeRatesRepository

// Same numeric contract as Database.getTheme() / BaseActivity.
private const val THEME_LIGHT = 0
private const val THEME_DARK = 1
private const val THEME_OLED = 3

// Language.SYSTEM.iso — matches the enum value that means "follow system
// locale" without pulling the enum into this file.
private const val LANGUAGE_SYSTEM = "system"

class PreferenceViewModel(private val app: Application) : AndroidViewModel(app) {

    private val db = Database(app)
    private var apiProvider: LiveData<ApiProvider> = db.getApiProviderAsync()
    private var openExchangeratesApiKey: LiveData<String?> = db.getOpenExchangeRatesApiKeyAsync()
    private var isPreviewConversionEnabled: LiveData<Boolean> = db.isPreviewConversionEnabled()

    fun setApiProvider(api: ApiProvider) {
        persistAndRefreshRates { db.setApiProvider(api) }
    }

    fun getApiProvider(): LiveData<ApiProvider> {
        return apiProvider
    }

    fun setOpenExchangeratesApiKey(id: String) {
        persistAndRefreshRates { db.setOpenExchangeRatesApiKey(id) }
    }

    // Persist a provider-affecting change, then re-fetch rates so the UI
    // doesn't keep showing the previous provider's cached values.
    private fun persistAndRefreshRates(persist: () -> Unit) {
        persist()
        ExchangeRatesRepository(app).getExchangeRates()
    }

    fun getOpenExchangeratesApiKey(): LiveData<String?> {
        return openExchangeratesApiKey
    }

    fun setTheme(theme: Int) {
        db.setTheme(theme)
        AppCompatDelegate.setDefaultNightMode(nightModeFor(theme))
    }

    private fun nightModeFor(theme: Int): Int = when (theme) {
        THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        THEME_DARK, THEME_OLED -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    fun setLanguage(language: String) {
        val appLocale: LocaleListCompat =
            if (language == LANGUAGE_SYSTEM)
                LocaleListCompat.getEmptyLocaleList()
            else
                LocaleListCompat.forLanguageTags(
                    // pt_BR -> pt-BR
                    language.replace('_', '-')
                )
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    /**
     * returns the currently selected language in the following format:
     * "de_DE" or "de", if no country is set
     */
    fun getLanguage(): String? {
        val appLocale = AppCompatDelegate.getApplicationLocales()[0]
        return if (appLocale == null || (appLocale.language.isEmpty() && appLocale.country.isEmpty()))
            null
        else if (appLocale.country.isEmpty())
            appLocale.language
        else if (appLocale.language.isEmpty())
            appLocale.country
        else
            "${appLocale.language}_${appLocale.country}"
    }

    fun isPreviewConversionEnabled(): LiveData<Boolean> {
        return isPreviewConversionEnabled
    }

    fun setPreviewConversionEnabled(enabled: Boolean) {
        db.setPreviewConversionEnabled(enabled)
    }

    fun setKeyboardType(type: Int) {
        db.setKeyboardType(type)
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        db.setHapticFeedbackEnabled(enabled)
    }

    fun setDecimalPlaces(places: Int) {
        db.setDecimalPlaces(places)
    }

}
