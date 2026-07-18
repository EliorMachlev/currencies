package de.salomax.currencies.model.adapter

import com.squareup.moshi.JsonReader
import okio.buffer
import okio.source
import java.io.InputStream
import java.math.BigDecimal
import java.time.LocalDate

// One "official fixing" observation from the Bank of Israel SDMX-JSON feed.
// `rawValue` is quoted as ILS per [BankOfIsrael.UNIT_PER_CURRENCY]-many foreign
// currency units (matching the PublicApi convention); the provider converts it
// into ILS-based rates.
internal data class BankOfIsraelObservation(
    val currency: String,
    val date: LocalDate,
    val rawValue: BigDecimal,
)

private const val DATA_TYPE_OFFICIAL_FIXING = "OF00"
private const val DIM_BASE_CURRENCY = "BASE_CURRENCY"
private const val DIM_DATA_TYPE = "DATA_TYPE"
private const val DIM_TIME_PERIOD = "TIME_PERIOD"

/**
 * Parses the SDMX-JSON response from
 * `edge.boi.gov.il/FusionEdgeServer/sdmx/v2/data/dataflow/BOI.STATISTICS/EXR/1.0`.
 *
 * The feed keys each time series by dimension-index tuples (e.g. `"0:0:6:0:0:0"`
 * — SERIES_CODE:FREQ:BASE_CURRENCY:COUNTER_CURRENCY:UNIT_MEASURE:DATA_TYPE).
 * The parser walks the `structures` block to build code-lookup tables per
 * dimension, then filters the `series` block to `DATA_TYPE = OF00` (Official
 * Fixing — the same series exposed by the simpler PublicApi endpoint).
 */
internal class BankOfIsraelSdmxParser {

    fun parse(inputStream: InputStream): List<BankOfIsraelObservation> {
        JsonReader.of(inputStream.source().buffer()).use { reader ->
            val root = reader.readJsonValue() as? Map<*, *> ?: return emptyList()
            val data = root["data"] as? Map<*, *> ?: return emptyList()

            val (seriesDimNames, seriesDimCodes) = readDimensions(data, "series")
            val timeCodes = readTimeCodes(data)

            val baseCurrencyPos = seriesDimNames.indexOf(DIM_BASE_CURRENCY)
            val dataTypePos = seriesDimNames.indexOf(DIM_DATA_TYPE)
            if (baseCurrencyPos < 0 || dataTypePos < 0 || timeCodes.isEmpty()) return emptyList()

            val dataSets = data["dataSets"] as? List<*> ?: return emptyList()
            val firstDataSet = dataSets.firstOrNull() as? Map<*, *> ?: return emptyList()
            val series = firstDataSet["series"] as? Map<*, *> ?: return emptyList()

            return buildList {
                for ((seriesKey, seriesValue) in series) {
                    val indices = (seriesKey as? String)?.split(':')?.mapNotNull { it.toIntOrNull() }
                        ?: continue
                    if (indices.size <= maxOf(baseCurrencyPos, dataTypePos)) continue

                    val dataType = seriesDimCodes.getOrNull(dataTypePos)?.getOrNull(indices[dataTypePos])
                    if (dataType != DATA_TYPE_OFFICIAL_FIXING) continue

                    val currency = seriesDimCodes.getOrNull(baseCurrencyPos)
                        ?.getOrNull(indices[baseCurrencyPos]) ?: continue
                    val observations = (seriesValue as? Map<*, *>)?.get("observations") as? Map<*, *>
                        ?: continue

                    for ((obsKey, obsValue) in observations) {
                        val dateIndex = (obsKey as? String)?.toIntOrNull() ?: continue
                        val date = timeCodes.getOrNull(dateIndex) ?: continue
                        val rawValue = extractObservationValue(obsValue) ?: continue
                        add(BankOfIsraelObservation(currency, date, rawValue))
                    }
                }
            }
        }
    }

    private fun readDimensions(data: Map<*, *>, kind: String): Pair<List<String>, List<List<String>>> {
        val structures = data["structures"] as? List<*>
            ?: (data["structure"]?.let { listOf(it) }) ?: return emptyList<String>() to emptyList()
        val first = structures.firstOrNull() as? Map<*, *> ?: return emptyList<String>() to emptyList()
        val dimensions = first["dimensions"] as? Map<*, *> ?: return emptyList<String>() to emptyList()
        val dims = dimensions[kind] as? List<*> ?: return emptyList<String>() to emptyList()

        val names = mutableListOf<String>()
        val codes = mutableListOf<List<String>>()
        for (dim in dims) {
            val dimMap = dim as? Map<*, *> ?: continue
            names.add(dimMap["id"] as? String ?: "")
            val values = dimMap["values"] as? List<*> ?: emptyList<Any>()
            codes.add(values.mapNotNull { (it as? Map<*, *>)?.get("id") as? String })
        }
        return names to codes
    }

    private fun readTimeCodes(data: Map<*, *>): List<LocalDate> {
        val (names, codes) = readDimensions(data, "observation")
        val timePos = names.indexOf(DIM_TIME_PERIOD)
        if (timePos < 0) return emptyList()
        return codes[timePos].mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    private fun extractObservationValue(obsValue: Any?): BigDecimal? {
        // observations are arrays: [value, ...attributes]
        val list = obsValue as? List<*> ?: return null
        val first = list.firstOrNull() ?: return null
        return runCatching { BigDecimal(first.toString()) }.getOrNull()
    }
}
