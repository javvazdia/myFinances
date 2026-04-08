package com.myfinances.app.presentation.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.myfinances.app.domain.model.OverviewHistory
import com.myfinances.app.domain.model.OverviewHistoryLine
import com.myfinances.app.domain.model.OverviewPeriodFilter
import com.myfinances.app.domain.model.RecentTransaction
import com.myfinances.app.presentation.shared.formatDateRangeLabel
import com.myfinances.app.presentation.shared.formatMinorMoney

@Composable
internal fun OverviewPeriodFilterCard(
    selectedPeriod: OverviewPeriodFilter,
    customStartEpochMs: Long?,
    customEndEpochMs: Long?,
    onSelectPeriod: (OverviewPeriodFilter) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Cash flow period",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OverviewPeriodFilter.entries.forEach { period ->
                    val isSelected = period == selectedPeriod
                    val label = if (period == OverviewPeriodFilter.CUSTOM) {
                        formatDateRangeLabel(customStartEpochMs, customEndEpochMs)
                    } else {
                        period.label
                    }
                    if (isSelected) {
                        Button(onClick = { onSelectPeriod(period) }) {
                            Text(label)
                        }
                    } else {
                        OutlinedButton(onClick = { onSelectPeriod(period) }) {
                            Text(label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun OverviewHistoryCard(history: OverviewHistory?) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Account performance",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Each line tracks one account, and the total line adds them together over the selected period.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (history == null || history.lines.isEmpty()) {
                Text(
                    text = "Not enough account history is available yet to draw the overview chart.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            OverviewHistoryLegend(lines = history.lines)
            MultiLineOverviewChart(history = history)
        }
    }
}

@Composable
internal fun MetricHighlight(
    title: String,
    value: String,
    supporting: String,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun OverviewHistoryLegend(lines: List<OverviewHistoryLine>) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        lines.forEachIndexed { index, line ->
            val color = overviewChartColor(index, line.isTotal)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (line.isTotal) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "\u25A0",
                        color = color,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = line.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (line.isTotal) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MultiLineOverviewChart(history: OverviewHistory) {
    var chartSize by remember { mutableStateOf(IntSize.Zero) }
    val allPoints = history.lines.flatMap(OverviewHistoryLine::points)
    val minValue = allPoints.minOfOrNull { point -> point.valueMinor } ?: 0L
    val maxValue = allPoints.maxOfOrNull { point -> point.valueMinor } ?: 0L
    val range = (maxValue - minValue).takeIf { it > 0L } ?: 1L
    val guideColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val timelinePoints = history.lines
        .flatMap { line -> line.points }
        .distinctBy { point -> point.timestampEpochMs }
        .sortedBy { point -> point.timestampEpochMs }
    var selectedIndex by remember(timelinePoints) {
        mutableStateOf(timelinePoints.lastIndex.takeIf { it >= 0 } ?: 0)
    }
    val currentTimelinePoints by rememberUpdatedState(timelinePoints)
    val selectedPoint = currentTimelinePoints.getOrNull(selectedIndex)

    selectedPoint?.let { point ->
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = point.detailLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                history.lines.forEachIndexed { index, line ->
                    val selectedLinePoint = line.points
                        .lastOrNull { linePoint -> linePoint.timestampEpochMs <= point.timestampEpochMs }
                        ?: return@forEachIndexed
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "\u25A0",
                                color = overviewChartColor(index, line.isTotal),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = line.label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (line.isTotal) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                        Text(
                            text = formatMinorMoney(selectedLinePoint.valueMinor, history.currencyCode),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (line.isTotal) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = history.minimumLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = history.maximumLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .onPointerEvent(PointerEventType.Enter) { event ->
                updateOverviewSelectedPointIndex(
                    event = event,
                    chartSize = chartSize,
                    timelinePoints = currentTimelinePoints,
                    history = history,
                    onSelectedIndex = { index -> selectedIndex = index },
                )
            }
            .onPointerEvent(PointerEventType.Move) { event ->
                updateOverviewSelectedPointIndex(
                    event = event,
                    chartSize = chartSize,
                    timelinePoints = currentTimelinePoints,
                    history = history,
                    onSelectedIndex = { index -> selectedIndex = index },
                )
            }
            .onPointerEvent(PointerEventType.Press) { event ->
                updateOverviewSelectedPointIndex(
                    event = event,
                    chartSize = chartSize,
                    timelinePoints = currentTimelinePoints,
                    history = history,
                    onSelectedIndex = { index -> selectedIndex = index },
                )
            }
            .onPointerEvent(PointerEventType.Release) { event ->
                updateOverviewSelectedPointIndex(
                    event = event,
                    chartSize = chartSize,
                    timelinePoints = currentTimelinePoints,
                    history = history,
                    onSelectedIndex = { index -> selectedIndex = index },
                )
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { chartSize = it },
        ) {
            val width = chartSize.width.toFloat().takeIf { it > 0f } ?: size.width
            val height = chartSize.height.toFloat().takeIf { it > 0f } ?: size.height
            if (width <= 0f || height <= 0f) return@Canvas

            history.lines.forEachIndexed { index, line ->
                val coordinates = line.points.map { point ->
                    val normalizedX = normalizeX(point.timestampEpochMs, history)
                    val normalizedY = ((point.valueMinor - minValue).toFloat() / range.toFloat())
                    Offset(
                        x = normalizedX * width,
                        y = height - (normalizedY * height),
                    )
                }
                if (coordinates.isEmpty()) return@forEachIndexed

                val path = Path().apply {
                    coordinates.forEachIndexed { pointIndex, offset ->
                        if (pointIndex == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
                    }
                }
                drawPath(
                    path = path,
                    color = overviewChartColor(index, line.isTotal),
                    style = Stroke(width = if (line.isTotal) 7f else 4f),
                )

                val selectedLinePoint = line.points
                    .lastOrNull { point ->
                        point.timestampEpochMs <= (selectedPoint?.timestampEpochMs ?: Long.MIN_VALUE)
                    }
                if (selectedLinePoint != null) {
                    val normalizedX = normalizeX(selectedLinePoint.timestampEpochMs, history)
                    val normalizedY = ((selectedLinePoint.valueMinor - minValue).toFloat() / range.toFloat())
                    drawCircle(
                        color = overviewChartColor(index, line.isTotal),
                        radius = if (line.isTotal) 7f else 5f,
                        center = Offset(
                            x = normalizedX * width,
                            y = height - (normalizedY * height),
                        ),
                    )
                }
            }

            selectedPoint?.let { point ->
                val normalizedX = normalizeX(point.timestampEpochMs, history)
                val x = normalizedX * width
                drawLine(
                    color = guideColor,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 2f,
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = history.startLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = history.endLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun updateOverviewSelectedPointIndex(
    event: PointerEvent,
    chartSize: IntSize,
    timelinePoints: List<com.myfinances.app.domain.model.OverviewHistoryPoint>,
    history: OverviewHistory,
    onSelectedIndex: (Int) -> Unit,
) {
    val position = event.changes.firstOrNull()?.position ?: return
    if (chartSize.width == 0 || timelinePoints.isEmpty()) return
    onSelectedIndex(
        nearestOverviewTimelineIndex(
            x = position.x,
            width = chartSize.width.toFloat(),
            history = history,
            timelinePoints = timelinePoints,
        ),
    )
}

private fun nearestOverviewTimelineIndex(
    x: Float,
    width: Float,
    history: OverviewHistory,
    timelinePoints: List<com.myfinances.app.domain.model.OverviewHistoryPoint>,
): Int {
    if (timelinePoints.size <= 1 || width <= 0f) return 0
    val normalizedX = (x / width).coerceIn(0f, 1f)
    val targetTimestamp = denormalizeX(normalizedX, history)
    return timelinePoints.indices.minByOrNull { index ->
        kotlin.math.abs(timelinePoints[index].timestampEpochMs - targetTimestamp)
    } ?: 0
}

@Composable
internal fun MetricTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun normalizeX(
    timestampEpochMs: Long,
    history: OverviewHistory,
): Float {
    val allPoints = history.lines.flatMap(OverviewHistoryLine::points)
    val minTimestamp = allPoints.minOfOrNull { point -> point.timestampEpochMs } ?: timestampEpochMs
    val maxTimestamp = allPoints.maxOfOrNull { point -> point.timestampEpochMs } ?: timestampEpochMs
    val range = (maxTimestamp - minTimestamp).takeIf { it > 0L } ?: 1L
    return ((timestampEpochMs - minTimestamp).toFloat() / range.toFloat()).coerceIn(0f, 1f)
}

private fun denormalizeX(
    normalizedX: Float,
    history: OverviewHistory,
): Long {
    val allPoints = history.lines.flatMap(OverviewHistoryLine::points)
    val minTimestamp = allPoints.minOfOrNull { point -> point.timestampEpochMs } ?: 0L
    val maxTimestamp = allPoints.maxOfOrNull { point -> point.timestampEpochMs } ?: minTimestamp
    val range = (maxTimestamp - minTimestamp).takeIf { it > 0L } ?: 1L
    return minTimestamp + (normalizedX * range.toFloat()).toLong()
}

private fun overviewChartColor(
    index: Int,
    isTotal: Boolean,
): Color {
    if (isTotal) return Color(0xFF0B8043)
    val palette = listOf(
        Color(0xFF1A73E8),
        Color(0xFFD93025),
        Color(0xFFF29900),
        Color(0xFF7B1FA2),
        Color(0xFF00897B),
        Color(0xFF5D4037),
    )
    return palette[index % palette.size]
}

@Composable
internal fun TransactionRow(transaction: RecentTransaction) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = transaction.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = transaction.category,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = transaction.amountLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (transaction.isExpense) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = transaction.dateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal val OverviewPeriodFilter.label: String
    get() = when (this) {
        OverviewPeriodFilter.ONE_MONTH -> "1 month"
        OverviewPeriodFilter.THREE_MONTHS -> "3 months"
        OverviewPeriodFilter.SIX_MONTHS -> "6 months"
        OverviewPeriodFilter.ONE_YEAR -> "1 year"
        OverviewPeriodFilter.CUSTOM -> "Custom"
        OverviewPeriodFilter.ALL -> "All"
    }
