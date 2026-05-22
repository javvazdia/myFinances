package com.myfinances.app.presentation.overview

import com.myfinances.app.domain.model.OverviewHistory
import com.myfinances.app.domain.model.OverviewHistoryLine
import com.myfinances.app.domain.model.OverviewHistoryPoint
import kotlin.test.Test
import kotlin.test.assertEquals

class OverviewHistoryFiltersTest {
    @Test
    fun filtersOverviewHistoryToSelectedAccountLine() {
        val history = overviewHistory()

        val filtered = history.filteredForLine("account-2")

        assertEquals(1, filtered.lines.size)
        assertEquals("Broker", filtered.lines.single().label)
        assertEquals("200.00 EUR", filtered.minimumLabel)
        assertEquals("250.00 EUR", filtered.maximumLabel)
        assertEquals("Jan", filtered.startLabel)
        assertEquals("Feb", filtered.endLabel)
    }

    @Test
    fun keepsFullOverviewHistoryWhenNoAccountIsSelected() {
        val history = overviewHistory()

        val filtered = history.filteredForLine(null)

        assertEquals(history, filtered)
    }

    @Test
    fun resolvesSelectedLineLabelOnlyForAccountLines() {
        val history = overviewHistory()

        assertEquals("Checking", history.selectedLineLabel("account-1"))
        assertEquals(null, history.selectedLineLabel("total"))
    }
}

private fun overviewHistory(): OverviewHistory = OverviewHistory(
    currencyCode = "EUR",
    lines = listOf(
        OverviewHistoryLine(
            id = "total",
            label = "Total",
            points = listOf(
                overviewPoint(epochMs = 1L, valueMinor = 300_00L, label = "Jan"),
                overviewPoint(epochMs = 2L, valueMinor = 375_00L, label = "Feb"),
            ),
            isTotal = true,
        ),
        OverviewHistoryLine(
            id = "account-1",
            label = "Checking",
            points = listOf(
                overviewPoint(epochMs = 1L, valueMinor = 100_00L, label = "Jan"),
                overviewPoint(epochMs = 2L, valueMinor = 125_00L, label = "Feb"),
            ),
            isTotal = false,
        ),
        OverviewHistoryLine(
            id = "account-2",
            label = "Broker",
            points = listOf(
                overviewPoint(epochMs = 1L, valueMinor = 200_00L, label = "Jan"),
                overviewPoint(epochMs = 2L, valueMinor = 250_00L, label = "Feb"),
            ),
            isTotal = false,
        ),
    ),
    minimumLabel = "100.00 EUR",
    maximumLabel = "375.00 EUR",
    startLabel = "Jan",
    endLabel = "Feb",
)

private fun overviewPoint(
    epochMs: Long,
    valueMinor: Long,
    label: String,
): OverviewHistoryPoint = OverviewHistoryPoint(
    timestampEpochMs = epochMs,
    valueMinor = valueMinor,
    axisLabel = label,
    detailLabel = "$label 2026",
)
