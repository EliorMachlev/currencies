package de.salomax.currencies.view.preference

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.text.HtmlCompat
import de.salomax.currencies.R
import java.lang.reflect.Modifier

class ChangelogDialog : AppCompatDialogFragment() {

    companion object {
        private const val SEMVER_MAJOR_MULTIPLIER = 10_000
        private const val SEMVER_MINOR_MULTIPLIER = 100
        // R.array entries whose name begins with this prefix are treated as
        // per-version changelog bullet lists; the rest of the name is the
        // version string with underscores in place of dots.
        private const val CHANGELOG_ARRAY_PREFIX = "changelog_"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = View.inflate(context, R.layout.fragment_changelog, null)
        val textView = view.findViewById<TextView>(R.id.changelog)

        // HINT: needs proguard rule to work in release config
        for (declaredField in R.array::class.java.declaredFields
            .filter { field -> field.name.startsWith(CHANGELOG_ARRAY_PREFIX) }
            .sortedByDescending { field ->
                val s = field.name.substringAfter('_').split('_')
                try {
                    val major = s[0].toInt()
                    val minor = s[1].toInt()
                    val patch = s[2].toInt()
                    major * SEMVER_MAJOR_MULTIPLIER + minor * SEMVER_MINOR_MULTIPLIER + patch
                } catch (e: NumberFormatException) {
                    0
                } catch (e: IndexOutOfBoundsException) {
                    0
                }
            }) {
            val modifiers = declaredField.modifiers
            if (Modifier.isStatic(modifiers)
                && !Modifier.isPrivate(modifiers)
                && declaredField.type == Int::class.java
            )
                try {
                    val arrayId = declaredField.getInt(null)
                    // version number
                    val versionNumber = "<b>" + declaredField.name
                        .substringAfter('_')
                        .replace('_', '.') +
                            "</b><br>&#11834;"
                    // changes
                    val versionChanges = resources.getTextArray(arrayId)
                        .fold("") { acc, string -> "$acc<li>&nbsp;$string</li>" }
                        .plus("<br>")

                    textView.append(
                        HtmlCompat.fromHtml(
                            versionNumber + versionChanges,
                            HtmlCompat.FROM_HTML_MODE_COMPACT
                        )
                    )
                } catch (ignored: IllegalAccessException) {}
        }

        return AlertDialog.Builder(requireContext())
            .setPositiveButton(android.R.string.ok, null)
            .setTitle(R.string.title_changelog)
            .setView(view)
            .create()
    }

}
