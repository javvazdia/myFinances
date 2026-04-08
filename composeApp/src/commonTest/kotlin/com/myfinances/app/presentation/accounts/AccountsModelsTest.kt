package com.myfinances.app.presentation.accounts

import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.AccountSourceType
import com.myfinances.app.domain.model.AccountType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountsModelsTest {
    @Test
    fun selectedAccountHistoryChart_fallsBackToValueModeWhenSelectedModeMissing() {
        val valueChart = testChart(
            title = "Value chart",
            points = listOf(
                point("Jan 1", "Jan 1, 2025", 1_000L, 100.0),
                point("Feb 1", "Feb 1, 2025", 2_000L, 120.0),
            ),
        )
        val state = AccountsUiState(
            accountHistoryCharts = mapOf(AccountHistoryMode.VALUE to valueChart),
            selectedAccountHistoryMode = AccountHistoryMode.PERFORMANCE,
        )

        assertEquals("Value chart", state.selectedAccountHistoryChart?.title)
    }

    @Test
    fun selectedAccountHistoryChart_appliesCustomRangeFilter() {
        val chart = testChart(
            title = "Value chart",
            points = listOf(
                point("Jan 1", "Jan 1, 2025", 1_000L, 100.0),
                point("Mar 1", "Mar 1, 2025", 3_000L, 120.0),
                point("Apr 1", "Apr 1, 2025", 4_000L, 130.0),
            ),
        )
        val state = AccountsUiState(
            accountHistoryCharts = mapOf(AccountHistoryMode.VALUE to chart),
            selectedAccountHistoryMode = AccountHistoryMode.VALUE,
            selectedAccountHistoryRange = AccountHistoryRange.CUSTOM,
            customAccountHistoryStartEpochMs = 2_500L,
            customAccountHistoryEndEpochMs = 3_500L,
        )

        val filtered = state.selectedAccountHistoryChart

        assertEquals(1, filtered?.points?.size)
        assertEquals("Mar 1, 2025", filtered?.points?.single()?.detailLabel)
    }

    @Test
    fun toCreateMode_resetsDetailAndEditState() {
        val state = AccountsUiState(
            accounts = listOf(testAccount()),
            selectedAccountId = "acc-1",
            selectedInvestmentPositions = listOf(),
            accountHistoryCharts = mapOf(AccountHistoryMode.VALUE to testChart()),
            isLoadingAccountHistory = true,
            accountHistoryErrorMessage = "boom",
            draftName = "Draft",
            selectedType = AccountType.SAVINGS,
            draftCurrencyCode = "USD",
            draftOpeningBalance = "12.50",
            isFormVisible = true,
            editingAccountId = "acc-1",
            deleteConfirmationAccountId = "acc-1",
            isSaving = true,
            pendingDeleteAccountId = "acc-1",
            errorMessage = "error",
        )

        val reset = state.toCreateMode()

        assertNull(reset.selectedAccountId)
        assertTrue(reset.accountHistoryCharts.isEmpty())
        assertFalse(reset.isFormVisible)
        assertNull(reset.editingAccountId)
        assertNull(reset.deleteConfirmationAccountId)
        assertEquals(AccountType.CHECKING, reset.selectedType)
        assertEquals("USD", reset.draftCurrencyCode)
        assertEquals("", reset.draftName)
        assertEquals("", reset.draftOpeningBalance)
        assertNull(reset.errorMessage)
    }

    @Test
    fun currentBalanceMinorFor_fallsBackToOpeningBalanceAndZero() {
        val account = testAccount()
        val state = AccountsUiState(
            accounts = listOf(account),
        )

        assertEquals(account.openingBalanceMinor, state.currentBalanceMinorFor(account.id))
        assertEquals(0L, state.currentBalanceMinorFor("missing"))
    }
}

private fun testAccount(): Account = Account(
    id = "acc-1",
    name = "Checking",
    type = AccountType.CHECKING,
    currencyCode = "EUR",
    openingBalanceMinor = 50_00L,
    sourceType = AccountSourceType.MANUAL,
    sourceProvider = null,
    externalAccountId = null,
    lastSyncedAtEpochMs = null,
    isArchived = false,
    createdAtEpochMs = 0L,
    updatedAtEpochMs = 0L,
)

private fun testChart(
    title: String = "Chart",
    points: List<AccountHistoryPoint> = listOf(
        point("Jan 1", "Jan 1, 2025", 1_000L, 100.0),
        point("Feb 1", "Feb 1, 2025", 2_000L, 120.0),
    ),
): AccountHistoryChart = AccountHistoryChart(
    title = title,
    subtitle = "Subtitle",
    points = points,
    valueFormat = AccountHistoryValueFormat.MONEY,
    currencyCode = "EUR",
    minimumLabel = "100.00 EUR",
    maximumLabel = "120.00 EUR",
    startLabel = points.first().axisLabel,
    endLabel = points.last().axisLabel,
)

private fun point(
    axisLabel: String,
    detailLabel: String,
    timestampEpochMs: Long,
    value: Double,
): AccountHistoryPoint = AccountHistoryPoint(
    axisLabel = axisLabel,
    detailLabel = detailLabel,
    timestampEpochMs = timestampEpochMs,
    value = value,
)
