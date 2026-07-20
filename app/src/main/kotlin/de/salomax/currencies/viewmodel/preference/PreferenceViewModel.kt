package de.salomax.currencies.viewmodel.preference

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
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
private const val THEME_SYSTEM = 2
private const val THEME_OLED = 3
private const val THEME_SYSTEM_OLED = 4

// Activity-alias entries in the manifest — must be kept in sync with
// AndroidManifest.xml. The launcher targets one of these depending on whether
// the user picked an OLED theme option, so the pre-onCreate window paints the
// correct background on cold start.
private const val LAUNCHER_ALIAS_REGULAR = "de.salomax.currencies.view.main.MainActivityLauncher"
private const val LAUNCHER_ALIAS_PURE_BLACK = "de.salomax.currencies.view.main.MainActivityLauncherPureBlack"

// Enable exactly one launcher alias so the launcher intent resolves to the
// activity whose static theme matches the user's chosen background. Idempotent —
// safe to invoke on every startup to reconcile after migration or install.
internal fun applyLauncherAliasState(context: Context, pureBlack: Boolean) {
    val pm = context.packageManager
    val (enable, disable) = if (pureBlack)
        LAUNCHER_ALIAS_PURE_BLACK to LAUNCHER_ALIAS_REGULAR
    else
        LAUNCHER_ALIAS_REGULAR to LAUNCHER_ALIAS_PURE_BLACK
    pm.setComponentEnabledSetting(
        ComponentName(context, enable),
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        PackageManager.DONT_KILL_APP,
    )
    pm.setComponentEnabledSetting(
        ComponentName(context, disable),
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP,
    )
}

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

    /**
     * Returns true when the caller should recreate the current activity to pick
     * up the new theme. Only happens when night mode stays the same but the
     * pure-black flag flips (Dark ↔ OLED, or System ↔ System OLED while the
     * system is currently in dark). For any night-mode change, AppCompatDelegate
     * handles the recreate automatically and this returns false. MainActivity
     * below the current one is refreshed by BaseActivity.onResume.
     */
    fun setTheme(theme: Int): Boolean {
        val previousNightMode = nightModeFor(db.getTheme())
        val previousPureBlack = db.isPureBlackEnabled()
        db.setTheme(theme)
        val newNightMode = nightModeFor(theme)
        AppCompatDelegate.setDefaultNightMode(newNightMode)
        // Point the launcher at the correct alias so cold starts don't flash.
        applyLauncherAliasState(app, pureBlackFor(theme))
        val nightModeChanged = previousNightMode != newNightMode
        val pureBlackChanged = previousPureBlack != pureBlackFor(theme)
        return !nightModeChanged && pureBlackChanged && isDarkThemeActive()
    }

    private fun nightModeFor(theme: Int): Int = when (theme) {
        THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        THEME_DARK, THEME_OLED -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    private fun pureBlackFor(theme: Int): Boolean =
        theme == THEME_OLED || theme == THEME_SYSTEM_OLED

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

    private fun isDarkThemeActive(): Boolean {
        val theme = db.getTheme()
        val explicitlyDark = theme == THEME_DARK || theme == THEME_OLED
        val nightMode = app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val systemDark = (theme == THEME_SYSTEM || theme == THEME_SYSTEM_OLED) &&
                nightMode == Configuration.UI_MODE_NIGHT_YES
        return explicitlyDark || systemDark
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
