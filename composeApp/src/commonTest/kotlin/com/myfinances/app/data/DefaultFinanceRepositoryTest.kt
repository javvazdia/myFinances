package com.myfinances.app.data

import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.AccountSourceType
import com.myfinances.app.domain.model.AccountType
import com.myfinances.app.domain.model.AccountValuationSnapshot
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

    @Test
    fun buildsOverviewHistoryWithTotalAndPerAccountLines() {
        val history = buildOverviewHistory(
            accounts = listOf(
                account(id = "acc-1", name = "Checking", sourceType = AccountSourceType.MANUAL),
                account(id = "acc-2", name = "Broker", sourceType = AccountSourceType.API_SYNC, type = AccountType.INVESTMENT),
            ),
            allTransactions = listOf(
                transaction(
                    id = "txn-1",
                    type = TransactionType.INCOME,
                    amountMinor = 100_00L,
                    postedAtEpochMs = epochMsOf(2026, 1, 10),
                ).copy(accountId = "acc-1"),
            ),
            allSnapshots = listOf(
                snapshot(
                    id = "snap-1",
                    accountId = "acc-2",
                    valueMinor = 1_000_00L,
                    valuationDate = "2026-01-10",
                ),
                snapshot(
                    id = "snap-2",
                    accountId = "acc-2",
                    valueMinor = 1_050_00L,
                    valuationDate = "2026-02-10",
                ),
            ),
            period = OverviewPeriodFilter.ALL,
            nowEpochMs = epochMsOf(2026, 2, 15),
        )

        assertEquals(3, history?.lines?.size)
        assertEquals("Total", history?.lines?.first()?.label)
        assertEquals("Checking", history?.lines?.get(1)?.label)
        assertEquals("Broker", history?.lines?.get(2)?.label)
        assertEquals(1_100_00L, history?.lines?.first()?.points?.first()?.valueMinor)
        assertEquals(1_150_00L, history?.lines?.first()?.points?.last()?.valueMinor)
    }

    @Test
    fun customOverviewHistoryRangeCarriesForwardStartingBalance() {
        val history = buildOverviewHistory(
            accounts = listOf(
                account(id = "acc-1", name = "Checking", sourceType = AccountSourceType.MANUAL),
            ),
            allTransactions = listOf(
                transaction(
                    id = "txn-1",
                    type = TransactionType.INCOME,
                    amountMinor = 100_00L,
                    postedAtEpochMs = epochMsOf(2026, 1, 10),
                ).copy(accountId = "acc-1"),
                transaction(
                    id = "txn-2",
                    type = TransactionType.EXPENSE,
                    amountMinor = 25_00L,
                    postedAtEpochMs = epochMsOf(2026, 2, 10),
                ).copy(accountId = "acc-1"),
            ),
            allSnapshots = emptyList(),
            period = OverviewPeriodFilter.CUSTOM,
            customStartEpochMs = epochMsOf(2026, 2, 1),
            customEndEpochMs = epochMsOf(2026, 2, 28),
            nowEpochMs = epochMsOf(2026, 2, 20),
        )

        val accountLine = history?.lines?.firstOrNull { line -> !line.isTotal }

        assertEquals(2, accountLine?.points?.size)
        assertEquals(100_00L, accountLine?.points?.first()?.valueMinor)
        assertEquals(epochMsOf(2026, 2, 1), accountLine?.points?.first()?.timestampEpochMs)
        assertEquals(75_00L, accountLine?.points?.last()?.valueMinor)
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

private fun account(
    id: String,
    name: String,
    sourceType: AccountSourceType,
    type: AccountType = AccountType.CHECKING,
): Account = Account(
    id = id,
    name = name,
    type = type,
    currencyCode = "EUR",
    openingBalanceMinor = 0L,
    sourceType = sourceType,
    sourceProvider = if (sourceType == AccountSourceType.API_SYNC) "Indexa Capital" else null,
    externalAccountId = null,
    lastSyncedAtEpochMs = null,
    isArchived = false,
    createdAtEpochMs = epochMsOf(2026, 1, 1),
    updatedAtEpochMs = epochMsOf(2026, 1, 1),
)

private fun snapshot(
    id: String,
    accountId: String,
    valueMinor: Long,
    valuationDate: String,
): AccountValuationSnapshot = AccountValuationSnapshot(
    id = id,
    accountId = accountId,
    sourceProvider = "Indexa Capital",
    currencyCode = "EUR",
    valueMinor = valueMinor,
    valuationDate = valuationDate,
    capturedAtEpochMs = epochMsOf(
        valuationDate.substring(0, 4).toInt(),
        valuationDate.substring(5, 7).toInt(),
        valuationDate.substring(8, 10).toInt(),
    ),
)

private fun epochMsOf(year: Int, month: Int, day: Int): Long =
    LocalDate.parse(
        "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}",
    )
        .atStartOfDayIn(TimeZone.currentSystemDefault())
        .toEpochMilliseconds()
