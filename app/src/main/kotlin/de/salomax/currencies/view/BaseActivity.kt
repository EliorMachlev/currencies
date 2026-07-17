package de.salomax.currencies.view

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import de.salomax.currencies.R
import de.salomax.currencies.repository.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val THEME_LIGHT = 0
private const val THEME_DARK = 1

abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // pure black
        setTheme(
            if (Database(this).isPureBlackEnabled())
                R.style.AppTheme_PureBlack
            else
                R.style.AppTheme
        )
        // theme
        AppCompatDelegate.setDefaultNightMode(
            when (Database(this).getTheme()) {
                THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )

        super.onCreate(savedInstanceState)
    }

    /**
     * Resolve `?android:attr/textColorSecondary` against `AppTheme` explicitly —
     * used where the current context's own theme lookup would return the wrong
     * color (e.g. inside snackbars/dialogs styled with a different overlay).
     */
    protected fun getTextColorSecondary(): Int {
        val a = theme.obtainStyledAttributes(R.style.AppTheme, intArrayOf(android.R.attr.textColorSecondary))
        val color = a.getColor(0, Color.TRANSPARENT)
        a.recycle()
        return color
    }

    /**
     * Subscribe to [FoldingFeature] changes for the current window and forward
     * the first feature (if any) to [onFeature] whenever it changes. Handles
     * the lifecycle-aware collection so subclasses only supply the reaction.
     */
    protected fun observeFoldingFeature(onFeature: (FoldingFeature) -> Unit) {
        lifecycleScope.launch(Dispatchers.Main) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@BaseActivity)
                    .windowLayoutInfo(this@BaseActivity)
                    .collect { info ->
                        info.displayFeatures.filterIsInstance(FoldingFeature::class.java)
                            .firstOrNull()?.let(onFeature)
                    }
            }
        }
    }

}
