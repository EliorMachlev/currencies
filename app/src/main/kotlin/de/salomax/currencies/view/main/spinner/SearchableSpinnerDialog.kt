package de.salomax.currencies.view.main.spinner

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.salomax.currencies.R
import de.salomax.currencies.model.Rate
import de.salomax.currencies.viewmodel.main.MainViewModel
import de.salomax.currencies.viewmodel.preference.PreferenceViewModel
import java.math.BigDecimal

class SearchableSpinnerDialog(context: Context) : AppCompatDialogFragment(), SearchView.OnQueryTextListener {

    private lateinit var mainViewModel: MainViewModel
    private lateinit var prefViewModel: PreferenceViewModel

    private var filterStarredButton: ImageButton? = null
    private var searchView: SearchView? = null
    private var listView: RecyclerView? = null

    var onRateClicked: ((Rate, Int) -> Unit)? = null

    private var adapter: SearchableSpinnerDialogAdapter = SearchableSpinnerDialogAdapter(context)

    fun setCurrentRate(currentRate: Rate) {
        adapter.setCurrentRate(currentRate)
    }
    fun setCurrentSum(currentSum: BigDecimal) {
        adapter.setCurrentSum(currentSum)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = View.inflate(context, R.layout.searchable_spinner_dialog, null)

        this.mainViewModel = ViewModelProvider(
            this, MainViewModel.Factory(requireActivity().application, true)
        )[MainViewModel::class.java]
        this.prefViewModel = ViewModelProvider(this)[PreferenceViewModel::class.java]

        // listView
        listView = view.findViewById(R.id.listView)
        listView?.layoutManager = LinearLayoutManager(context)
        listView?.adapter = adapter
        adapter.onRateClicked = { rate: Rate, position: Int ->
            onRateClicked?.invoke(rate, position)
            dismiss()
        }
        adapter.onStarClicked = {
            mainViewModel.toggleCurrencyStar(it.currency)
        }

        // drag-to-reorder for starred rows (long-press to drag)
        val dragCallback = object : ItemTouchHelper.Callback() {
            override fun isLongPressDragEnabled(): Boolean = true
            override fun isItemViewSwipeEnabled(): Boolean = false

            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val pos = viewHolder.bindingAdapterPosition
                return if (adapter.isDraggable(pos))
                    makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
                else 0
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return adapter.moveItem(
                    viewHolder.bindingAdapterPosition,
                    target.bindingAdapterPosition
                )
            }

            override fun canDropOver(
                recyclerView: RecyclerView,
                current: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return adapter.isDraggable(target.bindingAdapterPosition)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)
                mainViewModel.setStarredCurrencyOrder(adapter.getCurrentStarredOrder())
            }
        }
        ItemTouchHelper(dragCallback).attachToRecyclerView(listView)

        // searchView
        searchView = view.findViewById(R.id.searchView)
        searchView?.setOnQueryTextListener(this)
        searchView?.clearFocus()

        // filter starred
        filterStarredButton = view.findViewById(R.id.btn_toggle_fav)
        filterStarredButton?.setOnClickListener {
            mainViewModel.toggleStarredActive()
        }
        mainViewModel.isFilterStarredEnabled().observe(this) { enabled ->
            filterStarredButton?.setImageDrawable(
                if (enabled) ContextCompat.getDrawable(requireContext(), R.drawable.ic_favorite_on)
                else ContextCompat.getDrawable(requireContext(), R.drawable.ic_favorite_off)
            )
        }
        mainViewModel.isFilterStarredEnabled().observe(this) {
            adapter.filterStarred(it)
        }

        // rates
        mainViewModel.getExchangeRates().observe(this) {
            adapter.setRates(it?.rates)
        }
        // stars
        mainViewModel.getStarredCurrencies().observe(this) {
            adapter.setStars(it)
        }
        //  conversion preview
        prefViewModel.isPreviewConversionEnabled().observe(this) {
            adapter.setPreviewConversionEnabled(it)
        }
        // decimal accuracy
        mainViewModel.getDecimalPlaces().observe(this) {
            adapter.setDecimalPlaces(it)
        }

        // build dialog
        return AlertDialog.Builder(requireContext())
            .setNegativeButton(getString(android.R.string.cancel), null)
            .setView(view)
            .create()
    }

    override fun onQueryTextChange(query: String?): Boolean {
        adapter.filter(query)
        return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        searchView?.clearFocus()
        return true
    }

    // intentionally close dialog on orientation change. else it's a real mess to restore the
    // adapter. really not worth the effort!
    override fun onPause() {
        super.onPause()
        // needs to be done manually, as this fragment/adapter seems to get re-used. the filter won't get reset
        adapter.reset()
        dismiss()
    }

}
