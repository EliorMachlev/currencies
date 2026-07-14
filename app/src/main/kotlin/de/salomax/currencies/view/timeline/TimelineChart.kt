package de.salomax.currencies.view.timeline

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.DashedShape
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import de.salomax.currencies.util.stripTimePattern
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
    dateFormatLive: LiveData<String>,
    lineColor: Color,
    baselineColor: Color,
    axisColor: Color,
    onScrub: (LocalDate?) -> Unit,
) {
    val entries by entriesLive.observeAsState()
    val showGrid by showGridLive.observeAsState(initial = true)
    val showXAxis by showXAxisLive.observeAsState(initial = true)
    val showYAxis by showYAxisLive.observeAsState(initial = true)
    val highlightExtremes by highlightExtremesLive.observeAsState(initial = true)
    val dateFormat by dateFormatLive.observeAsState(initial = DEFAULT_DATE_FORMAT)
    val axisDateFormatter = remember(dateFormat) {
        DateTimeFormatter.ofPattern(stripYear(stripTimePattern(dateFormat)))
    }

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

    // Vico's axis measurement (getMaxLabelWidth) may call this with x-values outside
    // data.indices while the model is transitioning to a smaller series. Returning a
    // blank string throws IllegalStateException, so clamp to the valid range and fall
    // back to a non-blank placeholder when the series is empty.
    val bottomAxisValueFormatter = remember(data, axisDateFormatter) {
        CartesianValueFormatter { _, value, _ ->
            val lastIdx = data.size - 1
            if (lastIdx < 0) {
                "—"
            } else {
                val idx = value.toInt().coerceIn(0, lastIdx)
                data[idx].first.format(axisDateFormatter)
            }
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

    val markerValueFormatter = remember {
        DefaultCartesianMarker.ValueFormatter.default(decimalCount = MARKER_DECIMAL_COUNT)
    }
    val marker = rememberDefaultCartesianMarker(
        label = rememberTextComponent(style = axisLabelStyle),
        valueFormatter = markerValueFormatter,
    )

    val decorations = buildList {
        // Dashed verticals at year and month boundaries. Suppress the month
        // lines on the year view (heuristic: >90 data points) since ~12 of them
        // just add noise. Year boundaries always imply month boundaries, so
        // skip the month line at the same index to avoid stacking two colors.
        val showMonthChangeLines = data.size <= YEAR_VIEW_MIN_POINTS
        val yearChangeIndices = mutableListOf<Int>()
        val monthChangeIndices = mutableListOf<Int>()
        for (i in 1 until data.size) {
            val prev = data[i - 1].first
            val curr = data[i].first
            if (prev.year != curr.year) {
                yearChangeIndices += i
            } else if (showMonthChangeLines && prev.monthValue != curr.monthValue) {
                monthChangeIndices += i
            }
        }
        val dashedShape = DashedShape(
            dashLength = DASH_LENGTH.dp,
            gapLength = DASH_GAP_LENGTH.dp,
        )
        monthChangeIndices.forEach { idx ->
            add(
                VerticalLine(
                    x = idx.toDouble(),
                    line = LineComponent(
                        fill = Fill(MONTH_CHANGE_COLOR),
                        thickness = 1.dp,
                        shape = dashedShape,
                    ),
                )
            )
        }
        yearChangeIndices.forEach { idx ->
            add(
                VerticalLine(
                    x = idx.toDouble(),
                    line = LineComponent(
                        fill = Fill(YEAR_CHANGE_COLOR),
                        thickness = 1.dp,
                        shape = dashedShape,
                    ),
                )
            )
        }
        if (baseline != null) {
            add(
                HorizontalLine(
                    y = { baseline },
                    line = LineComponent(fill = Fill(baselineColor), thickness = 1.dp),
                )
            )
        }
        if (highlightExtremes && minValue != null && maxValue != null && minValue != maxValue) {
            val maxFill = Fill(lineColor.copy(alpha = HIGHLIGHT_ALPHA))
            val minFill = Fill(MIN_LINE_COLOR.copy(alpha = HIGHLIGHT_ALPHA))
            add(
                HorizontalLine(
                    y = { minValue },
                    line = LineComponent(fill = minFill, thickness = 1.dp),
                )
            )
            add(
                HorizontalLine(
                    y = { maxValue },
                    line = LineComponent(fill = maxFill, thickness = 1.dp),
                )
            )
        }
    }

    val yAxisItemPlacer = remember { VerticalAxis.ItemPlacer.count(count = { Y_AXIS_TARGET_LABEL_COUNT }) }
    val startAxis = VerticalAxis.rememberStart(
        label = if (showYAxis) rememberAxisLabelComponent(style = axisLabelStyle) else null,
        guideline = if (showGrid) rememberAxisGuidelineComponent() else null,
        itemPlacer = yAxisItemPlacer,
    )
    val axisItemPlacer = remember(data.size) {
        val spacing = (data.size / X_AXIS_TARGET_LABEL_COUNT).coerceAtLeast(1)
        HorizontalAxis.ItemPlacer.aligned(spacing = { spacing })
    }
    val bottomAxis = HorizontalAxis.rememberBottom(
        label = if (showXAxis) rememberAxisLabelComponent(style = axisLabelStyle) else null,
        guideline = if (showGrid) rememberAxisGuidelineComponent() else null,
        valueFormatter = bottomAxisValueFormatter,
        labelRotationDegrees = X_AXIS_LABEL_ROTATION,
        itemPlacer = axisItemPlacer,
    )

    // Rebuild the host when the series length changes: Vico's scroll/marker state
    // caches the previous point count and crashes when the dataset shrinks.
    key(data.size) {
        // Force LTR: Vico maps touch x-coordinates against the host's layout direction,
        // so under an RTL locale (e.g. Hebrew) the scrub marker mirrors to the wrong
        // side of the finger and clamps to the left edge.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
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
    }
}

internal const val DEFAULT_DATE_FORMAT = "dd/MM/yy"

private fun stripYear(pattern: String): String =
    pattern.replace("/yy", "").replace("yy/", "")

private const val HIGHLIGHT_ALPHA = 0.4f
private const val Y_AXIS_PADDING = 0.05
private const val X_AXIS_LABEL_ROTATION = 0f
private const val X_AXIS_TARGET_LABEL_COUNT = 7
private const val Y_AXIS_TARGET_LABEL_COUNT = 6
private const val MARKER_DECIMAL_COUNT = 5
private const val DASH_LENGTH = 4f
private const val DASH_GAP_LENGTH = 4f
private const val YEAR_VIEW_MIN_POINTS = 90
private val MIN_LINE_COLOR = Color(0xFFE53935)
private val YEAR_CHANGE_COLOR = Color(0xFF1E88E5)
private val MONTH_CHANGE_COLOR = Color(0xFF8E24AA)

// Vico 3.2.3 ships HorizontalLine but no VerticalLine. Mirror the x mapping used
// by HorizontalAxis (see HorizontalAxis.kt in vico:compose): the parent forces
// LTR so layoutDirectionMultiplier is 1 and getStart(isLtr) == layerBounds.left.
private class VerticalLine(
    private val x: Double,
    private val line: LineComponent,
) : Decoration {
    override fun drawUnderLayers(context: CartesianDrawingContext) {
        with(context) {
            val baseCanvasX = layerBounds.left - scroll + layerDimensions.startPadding
            val canvasX = baseCanvasX +
                ((x - ranges.minX) / ranges.xStep).toFloat() * layerDimensions.xSpacing
            if (canvasX < layerBounds.left || canvasX > layerBounds.right) return
            line.drawVertical(this, canvasX, layerBounds.top, layerBounds.bottom)
        }
    }
}
