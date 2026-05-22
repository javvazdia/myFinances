package com.myfinances.app.presentation.overview

import com.myfinances.app.domain.model.OverviewHistory
import com.myfinances.app.domain.model.OverviewHistoryLine
import com.myfinances.app.domain.model.OverviewHistoryPoint
import com.myfinances.app.presentation.shared.formatMinorMoney

internal fun OverviewHistory.filteredForLine(lineId: String?): OverviewHistory {
    val selectedLine = lineId
        ?.let { selectedId -> lines.firstOrNull { line -> !line.isTotal && line.id == selectedId } }
        ?: return this

    val values = selectedLine.points.map(OverviewHistoryPoint::valueMinor)
    if (values.isEmpty()) return this

    return copy(
        lines = listOf(selectedLine),
        minimumLabel = formatMinorMoney(values.minOrNull() ?: 0L, currencyCode),
        maximumLabel = formatMinorMoney(values.maxOrNull() ?: 0L, currencyCode),
        startLabel = selectedLine.points.first().axisLabel,
        endLabel = selectedLine.points.last().axisLabel,
    )
}

internal fun OverviewHistory.selectedLineLabel(lineId: String?): String? =
    lineId?.let { selectedId ->
        lines.firstOrNull { line -> !line.isTotal && line.id == selectedId }?.label
    }

internal fun overviewLineColor(
    line: OverviewHistoryLine,
    allLines: List<OverviewHistoryLine>,
) = overviewChartColor(
    index = allLines.indexOfFirst { item -> item.id == line.id }.takeIf { it >= 0 } ?: 0,
    isTotal = line.isTotal,
)
