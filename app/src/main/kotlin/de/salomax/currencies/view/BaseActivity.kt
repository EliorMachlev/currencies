package de.salomax.currencies.view

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import de.salomax.currencies.R
import de.salomax.currencies.repository.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class BaseActivity : AppCompatActivity() {

    // Theme value captured at onCreate; onResume compares against the current
    // DB value so returning here after a Dark ↔ OLED switch made in
    // PreferenceActivity triggers a recreate. Night-mode changes are already
    // handled by AppCompatDelegate.setDefaultNightMode.
    private var appliedThemeAtCreate: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        val db = Database(this)
        appliedThemeAtCreate = db.getTheme()
        // pure black — night mode itself is set once in CurrenciesApplication
        // so this setTheme call resolves against the correct night qualifier.
        setTheme(
            if (db.isPureBlackEnabled())
                R.style.AppTheme_PureBlack
            else
                R.style.AppTheme
        )

        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (Database(this).getTheme() != appliedThemeAtCreate) {
            recreate()
        }
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
