package de.salomax.currencies.model

import android.content.Context
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result
import com.squareup.moshi.JsonClass
import de.salomax.currencies.model.provider.BankOfCanada
import de.salomax.currencies.model.provider.BankRossii
import de.salomax.currencies.model.provider.FerEe
import de.salomax.currencies.model.provider.FrankfurterApp
import de.salomax.currencies.model.provider.InforEuro
import de.salomax.currencies.model.provider.NorgesBank
import de.salomax.currencies.model.provider.OpenExchangerates
import java.net.URI
import java.time.LocalDate

private const val ID_FRANKFURTER_APP = 1
private const val ID_INFOR_EURO = 3
private const val ID_NORGES_BANK = 4
private const val ID_BANK_ROSSII = 5
private const val ID_BANK_OF_CANADA = 6
private const val ID_OPEN_EXCHANGERATES = 7

@JsonClass(generateAdapter = false) // see https://stackoverflow.com/a/64085370/421140
enum class ApiProvider(
    val id: Int, // safer ordinal; DON'T CHANGE!
    private val implementation: Api
) {
    // EXCHANGERATE_HOST(0, "https://api.exchangerate.host"), // removed, as API was shut down
    FRANKFURTER_APP(ID_FRANKFURTER_APP, FrankfurterApp()),
    // FER_EE(2, FerEe()), // deactivated: API returns HTTP 422 most of the time with no response
    //   from developers — see https://github.com/narorolib/fer/issues/6
    INFOR_EURO(ID_INFOR_EURO, InforEuro()),
    NORGES_BANK(ID_NORGES_BANK, NorgesBank()),
    BANK_ROSSII(ID_BANK_ROSSII, BankRossii()),
    BANK_OF_CANADA(ID_BANK_OF_CANADA, BankOfCanada()),
    OPEN_EXCHANGERATES(ID_OPEN_EXCHANGERATES, OpenExchangerates());

    companion object {
        fun fromId(value: Int): ApiProvider = entries.firstOrNull { it.id == value }
            // this is our fallback, e.g. if an API is removed from the app
            ?: BANK_ROSSII
    }

    fun getName(): CharSequence =
        this.implementation.name

    fun getDescriptionShort(context: Context): CharSequence =
        this.implementation.descriptionShort(context)

    fun getDescriptionLong(context: Context): CharSequence =
        this.implementation.getDescriptionLong(context)

    fun getDescriptionUpdateInterval(context: Context): CharSequence =
        this.implementation.descriptionUpdateInterval(context)

    fun getHint(context: Context): CharSequence? =
        this.implementation.descriptionHint(context)

    // Host portion of [baseUrl], used to prewarm DNS at app start.
    fun getHost(): String? =
        runCatching { URI(this.implementation.baseUrl).host }.getOrNull()

    suspend fun getRates(context: Context?, date: LocalDate?): Result<ExchangeRates, FuelError> =
        this.implementation.getRates(context, date)

    suspend fun getTimeline(
        context: Context?,
        base: Currency,
        symbol: Currency,
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<Timeline, FuelError> {
        return this.implementation.getTimeline(context, base, symbol, startDate, endDate)
    }

    abstract class Api {
        abstract val name: String
        abstract fun descriptionShort(context: Context): CharSequence
        abstract fun getDescriptionLong(context: Context): CharSequence
        abstract fun descriptionUpdateInterval(context: Context): CharSequence
        abstract fun descriptionHint(context: Context): CharSequence?

        abstract val baseUrl: String
        abstract suspend fun getRates(context: Context?, date: LocalDate?): Result<ExchangeRates, FuelError>
        abstract suspend fun getTimeline(
            context: Context?,
            base: Currency,
            symbol: Currency,
            startDate: LocalDate,
            endDate: LocalDate
        ): Result<Timeline, FuelError>
    }

}
