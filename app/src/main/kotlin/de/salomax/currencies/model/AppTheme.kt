package de.salomax.currencies.model

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

/**
 * The five theme choices exposed to the user, unified into a single enum so the
 * (id, night mode, pure-black) triple stays in one place instead of being
 * mirrored across the DB, application, view-model and view layers.
 *
 * `id` is the value persisted in SharedPreferences and referenced from
 * `arrays_preference.xml`, so **it must not change once shipped**.
 */
enum class AppTheme(
    val id: Int,
    val nightMode: Int,
    val isPureBlack: Boolean,
) {
    LIGHT      (0, AppCompatDelegate.MODE_NIGHT_NO,            false),
    DARK       (1, AppCompatDelegate.MODE_NIGHT_YES,           false),
    OLED       (2, AppCompatDelegate.MODE_NIGHT_YES,           true),
    SYSTEM     (3, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, false),
    SYSTEM_OLED(4, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, true);

    /** True when this theme currently renders dark under [config]. */
    fun isDarkActive(config: Configuration): Boolean = when (nightMode) {
        AppCompatDelegate.MODE_NIGHT_YES -> true
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM ->
            (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        else -> false
    }

    companion object {
        val DEFAULT = SYSTEM
        fun fromId(id: Int): AppTheme = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
