package de.salomax.currencies.view.timeline

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
@Suppress("LongParameterList", "LongMethod")
fun TimelineChart(
    entriesLive: LiveData<List<Pair<LocalDate, Float>>?>,
    showGridLive: LiveData<Boolean>,
    showXAxisLive: LiveData<Boolean>,
    showYAxisLive: LiveData<Boolean>,
    highlightExtremesLive: LiveData<Boolean>,
    lineColor: Color,
    baselineColor: Color,
    axisColor: Color,
    dateFormatter: DateTimeFormatter,
    onScrub: (LocalDate?) -> Unit,
) {
    val entries by entriesLive.observeAsState()
    val showGrid by showGridLive.observeAsState(initial = true)
    val showXAxis by showXAxisLive.observeAsState(initial = true)
    val showYAxis by showYAxisLive.observeAsState(initial = true)
    val highlightExtremes by highlightExtremesLive.observeAsState(initial = true)

    val data = entries.orEmpty()
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(data.size, data.hashCode()) {
        if (data.isNotEmpty()) {
            modelProducer.runTransaction {
                lineModel { series(data.map { it.second }) }
            }
        }
    }

    val minValue = remember(data) { data.minOfOrNull { it.second }?.toDouble() }
    val maxValue = remember(data) { data.maxOfOrNull { it.second }?.toDouble() }
    val baseline = remember(data) { data.lastOrNull()?.second?.toDouble() }

    val bottomAxisValueFormatter = remember(data, dateFormatter) {
        CartesianValueFormatter { _, value, _ ->
            data.getOrNull(value.toInt())?.first?.format(dateFormatter) ?: " "
        }
    }

    val rangeProvider = remember(minValue, maxValue) {
        if (minValue != null && maxValue != null && minValue < maxValue) {
            val pad = (maxValue - minValue) * Y_AXIS_PADDING
            CartesianLayerRangeProvider.fixed(minY = minValue - pad, maxY = maxValue + pad)
        } else {
            CartesianLayerRangeProvider.auto()
        }
    }

    val markerListener = remember(data, onScrub) {
        object : CartesianMarkerVisibilityListener {
            override fun onShown(
                marker: CartesianMarker,
                targets: List<CartesianMarker.Target>,
            ) {
                val idx = targets.firstOrNull()?.x?.toInt() ?: return
                onScrub(data.getOrNull(idx)?.first)
            }

            override fun onHidden(marker: CartesianMarker) {
                onScrub(null)
            }
        }
    }

    val axisLabelStyle = TextStyle(color = axisColor, fontSize = 12.sp)

    val marker = rememberDefaultCartesianMarker(
        label = rememberTextComponent(style = axisLabelStyle),
    )

    val decorations = buildList {
        if (baseline != null) {
            add(
                HorizontalLine(
                    y = { baseline },
                    line = LineComponent(fill = Fill(baselineColor), thickness = 1.dp),
                )
            )
        }
        if (highlightExtremes && minValue != null && maxValue != null && minValue != maxValue) {
            val highlightFill = Fill(lineColor.copy(alpha = HIGHLIGHT_ALPHA))
            add(
                HorizontalLine(
                    y = { minValue },
                    line = LineComponent(fill = highlightFill, thickness = 1.dp),
                )
            )
            add(
                HorizontalLine(
                    y = { maxValue },
                    line = LineComponent(fill = highlightFill, thickness = 1.dp),
                )
            )
        }
    }

    val startAxis = VerticalAxis.rememberStart(
        label = if (showYAxis) rememberAxisLabelComponent(style = axisLabelStyle) else null,
        guideline = if (showGrid) rememberAxisGuidelineComponent() else null,
    )
    val bottomAxis = HorizontalAxis.rememberBottom(
        label = if (showXAxis) rememberAxisLabelComponent(style = axisLabelStyle) else null,
        guideline = if (showGrid) rememberAxisGuidelineComponent() else null,
        valueFormatter = bottomAxisValueFormatter,
        labelRotationDegrees = X_AXIS_LABEL_ROTATION,
    )

    CartesianChartHost(
        modifier = Modifier.fillMaxSize(),
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(Fill(lineColor))
                    )
                ),
                rangeProvider = rangeProvider,
            ),
            startAxis = startAxis,
            bottomAxis = bottomAxis,
            marker = marker,
            markerVisibilityListener = markerListener,
            decorations = decorations,
        ),
        modelProducer = modelProducer,
        scrollState = rememberVicoScrollState(scrollEnabled = false),
    )
}

private const val HIGHLIGHT_ALPHA = 0.4f
private const val Y_AXIS_PADDING = 0.05
private const val X_AXIS_LABEL_ROTATION = 45f
