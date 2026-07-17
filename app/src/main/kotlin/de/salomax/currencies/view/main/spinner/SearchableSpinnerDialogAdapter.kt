package de.salomax.currencies.view.main.spinner

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import de.salomax.currencies.R
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.Rate
import de.salomax.currencies.util.DECIMAL_PLACES_DEFAULT
import de.salomax.currencies.util.DECIMAL_PLACES_MAX
import de.salomax.currencies.util.DECIMAL_PLACES_MIN
import de.salomax.currencies.util.hasAppendedCurrencySymbol
import de.salomax.currencies.util.stripRtlMark
import de.salomax.currencies.util.toHumanReadableNumber
import java.math.BigDecimal
import java.math.MathContext

private const val VIEW_TYPE_RATE = 0
private const val VIEW_TYPE_API_HINT = 1

@SuppressLint("NotifyDataSetChanged")
class SearchableSpinnerDialogAdapter(private val context: Context) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // listeners
    var onRateClicked: ((Rate, Int) -> Unit)? = null
    var onStarClicked: ((Rate) -> Unit)? = null

    private var rates: List<Rate> = listOf()
    private var ratesFiltered: MutableList<Rate> = mutableListOf()
    private var stars: List<Currency> = listOf()

    private var isPreviewConversionEnabled: Boolean = false
    private var currentBaseRate: Rate? = null
    private var currentBaseSum: BigDecimal = BigDecimal.ONE
    private var decimalPlaces: Int = DECIMAL_PLACES_DEFAULT

    private var filterStarred = false
    private var filterText: String? = null

    private val drawableFav = ContextCompat.getDrawable(context, R.drawable.ic_favorite)
    private val drawableFavEmpty = ContextCompat.getDrawable(context, R.drawable.ic_favorite_empty)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_RATE -> ViewHolder(
                LayoutInflater.from(context).inflate(R.layout.row_currency_dropdown, parent, false)
            )
            VIEW_TYPE_API_HINT -> ViewHolderApiHint(
                LayoutInflater.from(context).inflate(R.layout.row_currency_dropdown_api_hint, parent, false)
            )
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == itemCount - 1) VIEW_TYPE_API_HINT else VIEW_TYPE_RATE
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // api hint
        if (position == itemCount - 1) {
            holder as ViewHolderApiHint
            // here, we just show the hint, that there are more APIs to choose from
            return
        }
        // regular
        holder as ViewHolder

        holder.ivFlag.visibility = View.VISIBLE
        holder.tvCode.visibility = View.VISIBLE
        holder.btnStar.visibility = View.VISIBLE

        val item = ratesFiltered[position]
        // flag
        holder.ivFlag.setImageDrawable(item.currency.flag(context))
        // ISO 4217 currency code ("USD")
        holder.tvCode.text = item.currency.iso4217Alpha()
        // full name ("US Dollar")
        holder.tvName.text = item.currency.fullName(context)
        // conversion preview
        if (isPreviewConversionEnabled && currentBaseRate != null) {
            if (holder.tvRate.visibility == View.GONE)
                holder.tvRate.visibility = View.VISIBLE
            holder.tvRate.text = buildConversionText(item)
        } else {
            if (holder.tvRate.visibility != View.GONE)
                holder.tvRate.visibility = View.GONE
        }
        holder.btnStar.setImageDrawable(
            if (stars.contains(item.currency)) drawableFav
            else drawableFavEmpty
        )
    }

    override fun getItemCount(): Int {
        // + 1: add another row, with a hint that there are different API providers
        return ratesFiltered.size + 1
    }

    fun setRates(rates: List<Rate>?) {
        if (rates == null)
            this.rates = ArrayList()
        else
            this.rates = rates
        update()
    }

    fun setStars(stars: List<Currency>?) {
        this.stars = stars ?: emptyList()
        update()
    }

    fun isStarred(position: Int): Boolean {
        if (position < 0 || position >= ratesFiltered.size) return false
        return stars.contains(ratesFiltered[position].currency)
    }

    fun isDraggable(position: Int): Boolean {
        return filterText.isNullOrEmpty() && isStarred(position)
    }

    fun moveItem(from: Int, to: Int): Boolean {
        if (from == to) return false
        if (from !in ratesFiltered.indices || to !in ratesFiltered.indices) return false
        if (!isStarred(from) || !isStarred(to)) return false
        val item = ratesFiltered.removeAt(from)
        ratesFiltered.add(to, item)
        notifyItemMoved(from, to)
        return true
    }

    fun getCurrentStarredOrder(): List<Currency> {
        val visibleOrder = ratesFiltered.map { it.currency }.filter { stars.contains(it) }
        // preserve any starred currencies that aren't in the current rates (e.g. after api switch)
        val missing = stars.filterNot { visibleOrder.contains(it) }
        return visibleOrder + missing
    }

    fun filterStarred(enabled: Boolean) {
        this.filterStarred = enabled
        update()
    }

    fun filter(constraint: String?) {
        this.filterText = constraint
        update()
    }

    //  conversion preview
    fun setPreviewConversionEnabled(enabled: Boolean) {
        isPreviewConversionEnabled = enabled
        update()
    }
    fun setCurrentRate(currentRate: Rate) {
        currentBaseRate = currentRate
        update()
    }
    fun setCurrentSum(currentSum: BigDecimal) {
        currentBaseSum = currentSum
        update()
    }
    fun setDecimalPlaces(places: Int) {
        decimalPlaces = places.coerceIn(DECIMAL_PLACES_MIN, DECIMAL_PLACES_MAX)
        update()
    }

    private fun buildConversionText(item: Rate): String {
        val sum = if (currentBaseSum.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ONE else currentBaseSum
        val sourceSymbol = currentBaseRate!!.currency.symbol() ?: ""
        val source = sum.toHumanReadableNumber(context, decimalPlaces = decimalPlaces, trim = true)
        val destinationSymbol = item.currency.symbol() ?: ""
        val destination = sum
            .divide(currentBaseRate!!.value, MathContext.DECIMAL128)
            .multiply(item.value)
            .toHumanReadableNumber(context, decimalPlaces = decimalPlaces, trim = true)
        val left = if (sourceSymbol.isEmpty()) source
            else if (hasAppendedCurrencySymbol(context)) "$source $sourceSymbol"
            else "$sourceSymbol $source"
        val right = if (destinationSymbol.isEmpty()) destination
            else if (hasAppendedCurrencySymbol(context)) "$destination $destinationSymbol"
            else "$destinationSymbol $destination"
        return "$left = $right".stripRtlMark().trim()
    }

    private fun update() {
        val filtered = rates
            // find all rates based on both their code name or their full name
            .filter { rate ->
                if (filterText != null)
                    // full name
                    rate.currency.fullName(context).contains(filterText!!, ignoreCase = true)
                    // code name
                    || rate.currency.iso4217Alpha().contains(filterText!!, ignoreCase = true)
                else
                    true
            }
            // starred
            .filter { rate ->
                if (filterStarred)
                    stars.contains(rate.currency)
                else
                    true
            }
        // starred rates come first, in the user's stored order; everything else keeps its order
        val starredRates = stars.mapNotNull { code -> filtered.find { it.currency == code } }
        val rest = filtered.filterNot { stars.contains(it.currency) }
        ratesFiltered = (starredRates + rest).toMutableList()

        notifyDataSetChanged()
    }

    internal fun reset() {
        filterText = null
        ratesFiltered = rates.toMutableList()
    }

    inner class ViewHolderApiHint(itemView: View) : RecyclerView.ViewHolder(itemView)

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val ivFlag: ShapeableImageView = itemView.findViewById(R.id.image)
        val tvCode: TextView = itemView.findViewById(R.id.text2)
        val tvName: TextView = itemView.findViewById(R.id.text)
        val tvRate: TextView = itemView.findViewById(R.id.text3)
        val btnStar: ImageButton = itemView.findViewById(R.id.btn_fav)

        init {
            itemView.setOnClickListener {
                onRateClicked?.invoke(ratesFiltered[layoutPosition], findOriginalPosition(layoutPosition))
            }
            btnStar.setOnClickListener {
                onStarClicked?.invoke(ratesFiltered[layoutPosition])
            }
        }

        private fun findOriginalPosition(filteredPosition: Int): Int {
            return rates.indexOf(ratesFiltered[filteredPosition])
        }

    }

}
