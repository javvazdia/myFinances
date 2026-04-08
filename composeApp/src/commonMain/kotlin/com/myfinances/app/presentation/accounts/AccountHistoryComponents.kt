package com.myfinances.app.presentation.accounts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.myfinances.app.presentation.shared.MyFinancesDateRangePickerDialog
import com.myfinances.app.presentation.shared.formatDateRangeLabel

@Composable
internal fun AccountHistoryCard(
    chart: AccountHistoryChart?,
    availableModes: List<AccountHistoryMode>,
    selectedMode: AccountHistoryMode,
    selectedRange: AccountHistoryRange,
    customStartEpochMs: Long?,
    customEndEpochMs: Long?,
    isLoading: Boolean,
    errorMessage: String?,
    onSelectMode: (AccountHistoryMode) -> Unit,
    onSelectRange: (AccountHistoryRange) -> Unit,
    onApplyCustomRange: (Long, Long) -> Unit,
) {
    var isCustomRangePickerVisible by remember { mutableStateOf(false) }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = chart?.title ?: "Account history",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            if (availableModes.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableModes.forEach { mode ->
                        if (mode == selectedMode) {
                            Button(onClick = { onSelectMode(mode) }) {
                                Text(mode.label)
                            }
                        } else {
                            OutlinedButton(onClick = { onSelectMode(mode) }) {
                                Text(mode.label)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AccountHistoryRange.entries.forEach { range ->
                    val label = if (range == AccountHistoryRange.CUSTOM) {
                        formatDateRangeLabel(customStartEpochMs, customEndEpochMs)
                    } else {
                        range.label
                    }
                    val onClick = {
                        if (range == AccountHistoryRange.CUSTOM) {
                            isCustomRangePickerVisible = true
                        } else {
                            onSelectRange(range)
                        }
                    }

                    if (range == selectedRange) {
                        Button(onClick = onClick) {
                            Text(label)
                        }
                    } else {
                        OutlinedButton(onClick = onClick) {
                            Text(label)
                        }
                    }
                }
            }

            errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            when {
                isLoading && (chart == null || chart.points.isEmpty()) -> {
                    Text(
                        text = "Loading history...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                chart == null || chart.points.isEmpty() -> {
                    Text(
                        text = "No history is available for this account yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    Text(
                        text = chart.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    InteractiveHistoryChart(chart = chart)
                }
            }
        }
    }

    if (isCustomRangePickerVisible) {
        MyFinancesDateRangePickerDialog(
            initialStartEpochMs = customStartEpochMs,
            initialEndEpochMs = customEndEpochMs,
            onDismiss = { isCustomRangePickerVisible = false },
            onConfirm = { startEpochMs, endEpochMs ->
                isCustomRangePickerVisible = false
                onApplyCustomRange(startEpochMs, endEpochMs)
            },
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun InteractiveHistoryChart(chart: AccountHistoryChart) {
    val lineColor = MaterialTheme.colorScheme.primary
    val markerColor = MaterialTheme.colorScheme.tertiary
    var chartSize by remember(chart.points) { mutableStateOf(IntSize.Zero) }
    var selectedIndex by remember(chart.points) {
        mutableStateOf(chart.points.lastIndex.takeIf { it >= 0 } ?: 0)
    }
    val currentPoints by rememberUpdatedState(chart.points)
    val selectedPoint = currentPoints.getOrNull(selectedIndex)

    selectedPoint?.let { point ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.medium,
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = point.detailLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatHistoryValue(
                    value = point.value,
                    valueFormat = chart.valueFormat,
                    currencyCode = chart.currencyCode,
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = chart.minimumLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = chart.maximumLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .onPointerEvent(PointerEventType.Enter) { event ->
                updateSelectedPointIndex(event, chartSize, currentPoints.size) { index -> selectedIndex = index }
            }
            .onPointerEvent(PointerEventType.Move) { event ->
                updateSelectedPointIndex(event, chartSize, currentPoints.size) { index -> selectedIndex = index }
            }
            .onPointerEvent(PointerEventType.Press) { event ->
                updateSelectedPointIndex(event, chartSize, currentPoints.size) { index -> selectedIndex = index }
            }
            .onPointerEvent(PointerEventType.Release) { event ->
                updateSelectedPointIndex(event, chartSize, currentPoints.size) { index -> selectedIndex = index }
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .onSizeChanged { size -> chartSize = size },
        ) {
            val points = chart.points
            if (points.isEmpty()) return@Canvas

            val values = points.map(AccountHistoryPoint::value)
            val minValue = values.minOrNull() ?: 0.0
            val maxValue = values.maxOrNull() ?: 0.0
            val valueRange = (maxValue - minValue).takeIf { range -> range > 0.0 } ?: 1.0
            val horizontalStep = if (points.size > 1) size.width / (points.size - 1) else 0f
            val path = Path()
            val coordinates = points.mapIndexed { index, point ->
                val x = if (points.size == 1) size.width / 2f else horizontalStep * index
                val normalized = ((point.value - minValue) / valueRange).toFloat()
                val y = size.height - (normalized * size.height)
                Offset(x, y)
            }

            coordinates.forEachIndexed { index, offset ->
                if (index == 0) {
                    path.moveTo(offset.x, offset.y)
                } else {
                    path.lineTo(offset.x, offset.y)
                }
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 6f),
            )

            coordinates.getOrNull(selectedIndex)?.let { selectedOffset ->
                drawLine(
                    color = markerColor.copy(alpha = 0.4f),
                    start = Offset(selectedOffset.x, 0f),
                    end = Offset(selectedOffset.x, size.height),
                    strokeWidth = 3f,
                )
                drawCircle(
                    color = markerColor,
                    radius = 8f,
                    center = selectedOffset,
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = chart.startLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = chart.endLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val AccountHistoryMode.label: String
    get() = when (this) {
        AccountHistoryMode.VALUE -> "Value"
        AccountHistoryMode.PERFORMANCE -> "Performance"
        AccountHistoryMode.SNAPSHOTS -> "Snapshots"
    }

private val AccountHistoryRange.label: String
    get() = when (this) {
        AccountHistoryRange.ONE_MONTH -> "1 month"
        AccountHistoryRange.THREE_MONTHS -> "3 months"
        AccountHistoryRange.SIX_MONTHS -> "6 months"
        AccountHistoryRange.ONE_YEAR -> "1 year"
        AccountHistoryRange.THREE_YEARS -> "3 years"
        AccountHistoryRange.CUSTOM -> "Custom"
        AccountHistoryRange.ALL -> "All"
    }

private fun nearestPointIndex(
    x: Float,
    width: Float,
    pointCount: Int,
): Int {
    if (pointCount <= 1 || width <= 0f) return 0
    val step = width / (pointCount - 1)
    return kotlin.math.round(x / step).toInt().coerceIn(0, pointCount - 1)
}

private fun updateSelectedPointIndex(
    event: PointerEvent,
    chartSize: IntSize,
    pointCount: Int,
    onSelectedIndex: (Int) -> Unit,
) {
    val position = event.changes.firstOrNull()?.position ?: return
    if (chartSize.width == 0 || pointCount == 0) return
    onSelectedIndex(
        nearestPointIndex(
            x = position.x,
            width = chartSize.width.toFloat(),
            pointCount = pointCount,
        ),
    )
}
