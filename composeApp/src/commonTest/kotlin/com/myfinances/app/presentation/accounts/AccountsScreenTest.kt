package com.myfinances.app.presentation.accounts

import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.AccountValuationSnapshot
import com.myfinances.app.domain.model.AccountSourceType
import com.myfinances.app.domain.model.AccountType
import com.myfinances.app.domain.model.FinanceTransaction
import com.myfinances.app.domain.model.InvestmentPosition
import com.myfinances.app.domain.model.TransactionType
import com.myfinances.app.integrations.indexa.model.IndexaPerformanceHistory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountsScreenTest {
    @Test
    fun normalizesCurrencyCode() {
        assertEquals("EUR", normalizeCurrencyCode(" eur "))
        assertNull(normalizeCurrencyCode("EU"))
        assertNull(normalizeCurrencyCode("EU1"))
    }

    @Test
    fun parsesOpeningBalanceIntoMinorUnits() {
        assertEquals(0L, parseAmountToMinor(""))
        assertEquals(123_45L, parseAmountToMinor("123.45"))
        assertEquals(-250_00L, parseAmountToMinor("-250"))
        assertEquals(12_30L, parseAmountToMinor("12,3"))
        assertNull(parseAmountToMinor("12.345"))
    }

    @Test
    fun formatsAccountAmountForEditingInput() {
        assertEquals("123.45", formatAccountAmountInput(123_45L))
        assertEquals("-250.00", formatAccountAmountInput(-250_00L))
    }

    @Test
    fun buildsReadablePositionSubtitle() {
        val subtitle = buildPositionSubtitle(
            InvestmentPosition(
                id = "position-1",
                accountId = "account-1",
                providerAccountId = "INDEXA01",
                instrumentIsin = "IE0032126645",
                instrumentName = "Vanguard US 500",
                assetClass = "equity_north_america",
                titles = 12.50,
                price = 244.10,
                marketValueMinor = 305_125,
                costAmountMinor = 280_000,
                valuationDate = "2026-03-18",
                updatedAtEpochMs = 1L,
            ),
        )

        assertEquals("equity_north_america | 12.5 units | @ 244.1", subtitle)
    }

    @Test
    fun buildsReadablePositionValuationLabel() {
        val label = buildPositionValuationLabel(
            position = InvestmentPosition(
                id = "position-1",
                accountId = "account-1",
                providerAccountId = "INDEXA01",
                instrumentIsin = "IE0032126645",
                instrumentName = "Vanguard US 500",
                assetClass = "equity_north_america",
                titles = 12.50,
                price = 244.10,
                marketValueMinor = 305_125,
                costAmountMinor = 280_000,
                valuationDate = "2026-03-18",
                updatedAtEpochMs = 1L,
            ),
            currencyCode = "EUR",
        )

        assertEquals("Market value 3051.25 EUR | Cost 2800.00 EUR", label)
    }

    @Test
    fun buildsLocalAccountHistoryFromRunningBalance() {
        val chart = buildLocalAccountHistoryChart(
            account = Account(
                id = "account-1",
                name = "Checking",
                type = AccountType.CHECKING,
                currencyCode = "EUR",
                openingBalanceMinor = 1_000_00,
                sourceType = AccountSourceType.MANUAL,
                sourceProvider = null,
                externalAccountId = null,
                lastSyncedAtEpochMs = null,
                isArchived = false,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
            ),
            transactions = listOf(
                FinanceTransaction(
                    id = "txn-1",
                    accountId = "account-1",
                    categoryId = null,
                    type = TransactionType.EXPENSE,
                    amountMinor = 100_00,
                    currencyCode = "EUR",
                    merchantName = null,
                    note = null,
                    sourceProvider = null,
                    externalTransactionId = null,
                    postedAtEpochMs = 2L,
                    createdAtEpochMs = 2L,
                    updatedAtEpochMs = 2L,
                ),
            ),
        )

        assertEquals(2, chart.points.size)
        assertEquals(1000.0, chart.points.first().value)
        assertEquals(900.0, chart.points.last().value)
    }

    @Test
    fun buildsIndexaValueAndPerformanceChartsFromHistory() {
        val charts = buildIndexaHistoryCharts(
            IndexaPerformanceHistory(
                accountNumber = "INDEXA01",
                valueHistory = mapOf(
                    "2024-01-31" to 12_400.0,
                    "2024-02-29" to 12_650.0,
                ),
                normalizedHistory = mapOf(
                    "2024-01-31" to 1.02,
                    "2024-02-29" to 1.05,
                ),
            ),
            currencyCode = "EUR",
            currentBalanceMinor = 12_650_00,
        )

        assertEquals("Indexa account value", charts[AccountHistoryMode.VALUE]?.title)
        assertEquals("Indexa performance history", charts[AccountHistoryMode.PERFORMANCE]?.title)
        assertEquals(12400.0, charts[AccountHistoryMode.VALUE]?.points?.first()?.value)
        assertEquals(105.0, charts[AccountHistoryMode.PERFORMANCE]?.points?.last()?.value)
    }

    @Test
    fun reconstructsIndexaValueChartFromNormalizedHistoryWhenMoneySeriesIsMissing() {
        val charts = buildIndexaHistoryCharts(
            history = IndexaPerformanceHistory(
                accountNumber = "INDEXA01",
                valueHistory = emptyMap(),
                normalizedHistory = mapOf(
                    "2024-01-31" to 1.0,
                    "2024-02-29" to 1.05,
                ),
            ),
            currencyCode = "EUR",
            currentBalanceMinor = 12_600_00,
        )

        assertEquals("Indexa account value", charts[AccountHistoryMode.VALUE]?.title)
        assertEquals(
            "Estimated account value reconstructed from Indexa evolution data and the current synced portfolio value.",
            charts[AccountHistoryMode.VALUE]?.subtitle,
        )
        assertEquals(12000.0, charts[AccountHistoryMode.VALUE]?.points?.first()?.value)
        assertEquals(12600.0, charts[AccountHistoryMode.VALUE]?.points?.last()?.value)
        assertEquals("Indexa performance history", charts[AccountHistoryMode.PERFORMANCE]?.title)
    }

    @Test
    fun buildsSnapshotHistoryChartFromStoredSnapshots() {
        val chart = buildSnapshotHistoryChart(
            account = Account(
                id = "account-1",
                name = "Indexa",
                type = AccountType.INVESTMENT,
                currencyCode = "EUR",
                openingBalanceMinor = 0L,
                sourceType = AccountSourceType.API_SYNC,
                sourceProvider = "Indexa Capital",
                externalAccountId = "INDEXA01",
                lastSyncedAtEpochMs = null,
                isArchived = false,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
            ),
            snapshots = listOf(
                AccountValuationSnapshot(
                    id = "snapshot-1",
                    accountId = "account-1",
                    sourceProvider = "Indexa Capital",
                    currencyCode = "EUR",
                    valueMinor = 12_400_00L,
                    valuationDate = "2024-01-31",
                    capturedAtEpochMs = 1L,
                ),
                AccountValuationSnapshot(
                    id = "snapshot-2",
                    accountId = "account-1",
                    sourceProvider = "Indexa Capital",
                    currencyCode = "EUR",
                    valueMinor = 12_650_00L,
                    valuationDate = "2024-02-29",
                    capturedAtEpochMs = 2L,
                ),
            ),
        )

        assertEquals("Snapshot history", chart?.title)
        assertEquals(12400.0, chart?.points?.first()?.value)
        assertEquals(12650.0, chart?.points?.last()?.value)
    }
}
