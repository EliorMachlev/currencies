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

        bindSwitch(view, R.id.switch_grid,
            db::isChartGridEnabledBlocking, db::setChartGridEnabled)
        bindSwitch(view, R.id.switch_x_axis,
            db::isChartXAxisLabelEnabledBlocking, db::setChartXAxisLabelEnabled)
        bindSwitch(view, R.id.switch_y_axis,
            db::isChartYAxisLabelEnabledBlocking, db::setChartYAxisLabelEnabled)
        bindSwitch(view, R.id.switch_highlight_extremes,
            db::isChartHighlightExtremesEnabledBlocking, db::setChartHighlightExtremesEnabled)

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.category_graph_options)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }

    private fun bindSwitch(root: View, id: Int, getter: () -> Boolean, setter: (Boolean) -> Unit) {
        root.findViewById<MaterialSwitch>(id).apply {
            isChecked = getter()
            setOnCheckedChangeListener { _, checked -> setter(checked) }
        }
    }

}
