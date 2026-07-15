package de.salomax.currencies.view.preference

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import com.google.android.material.materialswitch.MaterialSwitch
import de.salomax.currencies.R
import de.salomax.currencies.repository.Database

class GraphOptionsDialog : AppCompatDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = View.inflate(context, R.layout.dialog_graph_options, null)
        val db = Database(requireContext())

        val gridSwitch = view.findViewById<MaterialSwitch>(R.id.switch_grid)
        val xAxisSwitch = view.findViewById<MaterialSwitch>(R.id.switch_x_axis)
        val yAxisSwitch = view.findViewById<MaterialSwitch>(R.id.switch_y_axis)
        val highlightSwitch = view.findViewById<MaterialSwitch>(R.id.switch_highlight_extremes)

        gridSwitch.isChecked = db.isChartGridEnabledBlocking()
        xAxisSwitch.isChecked = db.isChartXAxisLabelEnabledBlocking()
        yAxisSwitch.isChecked = db.isChartYAxisLabelEnabledBlocking()
        highlightSwitch.isChecked = db.isChartHighlightExtremesEnabledBlocking()

        gridSwitch.setOnCheckedChangeListener { _, checked ->
            db.setChartGridEnabled(checked)
        }
        xAxisSwitch.setOnCheckedChangeListener { _, checked ->
            db.setChartXAxisLabelEnabled(checked)
        }
        yAxisSwitch.setOnCheckedChangeListener { _, checked ->
            db.setChartYAxisLabelEnabled(checked)
        }
        highlightSwitch.setOnCheckedChangeListener { _, checked ->
            db.setChartHighlightExtremesEnabled(checked)
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.category_graph_options)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }

}
