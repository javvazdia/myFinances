package com.myfinances.app.data

import com.myfinances.app.domain.model.FinanceTransaction
import com.myfinances.app.domain.model.OverviewPeriodFilter
import com.myfinances.app.domain.model.TransactionType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultFinanceRepositoryTest {
    @Test
    fun calculatesCashFlowForSelectedOverviewPeriod() {
        val nowEpochMs = epochMsOf(2026, 4, 6)
        val transactions = listOf(
            transaction(
                id = "income-in-range",
                type = TransactionType.INCOME,
                amountMinor = 512_790L,
                postedAtEpochMs = epochMsOf(2026, 3, 25),
            ),
            transaction(
                id = "expense-in-range",
                type = TransactionType.EXPENSE,
                amountMinor = 95_000L,
                postedAtEpochMs = epochMsOf(2026, 4, 2),
            ),
            transaction(
                id = "income-out-of-range",
                type = TransactionType.INCOME,
                amountMinor = 200_00L,
                postedAtEpochMs = epochMsOf(2025, 12, 20),
            ),
        )

        val cashFlow = calculateOverviewCashFlow(
            transactions = transactions,
            period = OverviewPeriodFilter.ONE_MONTH,
            nowEpochMs = nowEpochMs,
        )

        assertEquals(512_790L, cashFlow.incomeMinor)
        assertEquals(95_000L, cashFlow.expenseMinor)
    }

    @Test
    fun includesEverythingForAllTimeFilter() {
        val nowEpochMs = epochMsOf(2026, 4, 6)
        val oldTransactionEpochMs = epochMsOf(2025, 1, 10)

        assertTrue(
            isInOverviewPeriod(
                epochMs = oldTransactionEpochMs,
                period = OverviewPeriodFilter.ALL,
                nowEpochMs = nowEpochMs,
            ),
        )
        assertFalse(
            isInOverviewPeriod(
                epochMs = oldTransactionEpochMs,
                period = OverviewPeriodFilter.ONE_MONTH,
                nowEpochMs = nowEpochMs,
            ),
        )
    }

    @Test
    fun filtersCashFlowByCustomOverviewRange() {
        val transactions = listOf(
            transaction(
                id = "income-selected",
                type = TransactionType.INCOME,
                amountMinor = 1_000_00L,
                postedAtEpochMs = epochMsOf(2026, 3, 25),
            ),
            transaction(
                id = "income-outside",
                type = TransactionType.INCOME,
                amountMinor = 2_000_00L,
                postedAtEpochMs = epochMsOf(2026, 2, 20),
            ),
        )

        val cashFlow = calculateOverviewCashFlow(
            transactions = transactions,
            period = OverviewPeriodFilter.CUSTOM,
            customStartEpochMs = epochMsOf(2026, 3, 1),
            customEndEpochMs = epochMsOf(2026, 3, 31),
        )

        assertEquals(1_000_00L, cashFlow.incomeMinor)
        assertEquals(0L, cashFlow.expenseMinor)
    }
}

private fun transaction(
    id: String,
    type: TransactionType,
    amountMinor: Long,
    postedAtEpochMs: Long,
): FinanceTransaction = FinanceTransaction(
    id = id,
    accountId = "account-1",
    categoryId = null,
    type = type,
    amountMinor = amountMinor,
    currencyCode = "EUR",
    merchantName = null,
    note = null,
    sourceProvider = null,
    externalTransactionId = null,
    postedAtEpochMs = postedAtEpochMs,
    createdAtEpochMs = postedAtEpochMs,
    updatedAtEpochMs = postedAtEpochMs,
)

private fun epochMsOf(year: Int, month: Int, day: Int): Long =
    LocalDate.parse(
        "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}",
    )
        .atStartOfDayIn(TimeZone.currentSystemDefault())
        .toEpochMilliseconds()
