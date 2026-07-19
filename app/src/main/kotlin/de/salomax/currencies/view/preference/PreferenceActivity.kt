package de.salomax.currencies.view.preference

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import de.salomax.currencies.R
import de.salomax.currencies.view.BaseActivity

class PreferenceActivity: BaseActivity() {

    companion object {
        const val EXTRA_OPEN_FEES = "EXTRA_OPEN_FEES"

        fun feesIntent(context: Context): Intent =
            Intent(context, PreferenceActivity::class.java)
                .putExtra(EXTRA_OPEN_FEES, true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_preference)

        // Apply status/nav bar padding once at the container level so it stays
        // correct across fragment swaps (the fragment view's fitsSystemWindows
        // isn't re-dispatched after replace()/pop).
        val container = findViewById<android.view.View>(R.id.preferences_fragment)
        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        // title bar
        setTitle(R.string.title_preferences)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // When opened directly via feesIntent (from Main / Quick conversions),
        // make FeeManager the root fragment with no back-stack entry, so pressing
        // back finishes this activity and returns to the caller — not to Settings.
        // The path from Settings itself uses its own fragment transaction in
        // PreferenceFragment (with addToBackStack), so back there still pops to Settings.
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_OPEN_FEES, false)) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.preferences_fragment, FeeManagerFragment())
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    finish()
                }
                true
            }
            else -> false
        }
    }
}
