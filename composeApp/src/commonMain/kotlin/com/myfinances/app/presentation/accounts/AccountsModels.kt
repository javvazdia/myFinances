package com.myfinances.app.presentation.accounts

import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.AccountType
import com.myfinances.app.domain.model.InvestmentPosition

data class AccountsUiState(
    val accounts: List<Account> = emptyList(),
    val currentBalancesByAccountId: Map<String, Long> = emptyMap(),
    val selectedAccountId: String? = null,
    val selectedInvestmentPositions: List<InvestmentPosition> = emptyList(),
    val accountHistoryCharts: Map<AccountHistoryMode, AccountHistoryChart> = emptyMap(),
    val selectedAccountHistoryMode: AccountHistoryMode = AccountHistoryMode.VALUE,
    val selectedAccountHistoryRange: AccountHistoryRange = AccountHistoryRange.ALL,
    val customAccountHistoryStartEpochMs: Long? = null,
    val customAccountHistoryEndEpochMs: Long? = null,
    val isLoadingAccountHistory: Boolean = false,
    val accountHistoryErrorMessage: String? = null,
    val draftName: String = "",
    val selectedType: AccountType = AccountType.CHECKING,
    val draftCurrencyCode: String = "EUR",
    val draftOpeningBalance: String = "",
    val isFormVisible: Boolean = false,
    val editingAccountId: String? = null,
    val deleteConfirmationAccountId: String? = null,
    val isSaving: Boolean = false,
    val pendingDeleteAccountId: String? = null,
    val errorMessage: String? = null,
) {
    val selectedAccount: Account?
        get() = accounts.firstOrNull { account -> account.id == selectedAccountId }

    val selectedAccountCurrentBalanceMinor: Long?
        get() = selectedAccount?.let { account -> currentBalanceMinorFor(account.id) }

    val editingAccount: Account?
        get() = accounts.firstOrNull { account -> account.id == editingAccountId }

    val isEditing: Boolean
        get() = editingAccountId != null

    val deleteConfirmationAccountName: String?
        get() = accounts.firstOrNull { account ->
            account.id == deleteConfirmationAccountId
        }?.name

    val isBusy: Boolean
        get() = isSaving || pendingDeleteAccountId != null

    val selectedAccountHistoryChart: AccountHistoryChart?
        get() = accountHistoryCharts[selectedAccountHistoryMode]
            ?.filteredBy(
                range = selectedAccountHistoryRange,
                customStartEpochMs = customAccountHistoryStartEpochMs,
                customEndEpochMs = customAccountHistoryEndEpochMs,
            )
            ?: accountHistoryCharts[AccountHistoryMode.VALUE]
                ?.filteredBy(
                    range = selectedAccountHistoryRange,
                    customStartEpochMs = customAccountHistoryStartEpochMs,
                    customEndEpochMs = customAccountHistoryEndEpochMs,
                )
            ?: accountHistoryCharts.values.firstOrNull()
                ?.filteredBy(
                    range = selectedAccountHistoryRange,
                    customStartEpochMs = customAccountHistoryStartEpochMs,
                    customEndEpochMs = customAccountHistoryEndEpochMs,
                )

    val availableAccountHistoryModes: List<AccountHistoryMode>
        get() = AccountHistoryMode.entries.filter(accountHistoryCharts::containsKey)

    fun currentBalanceMinorFor(accountId: String): Long =
        currentBalancesByAccountId[accountId] ?: accounts
            .firstOrNull { account -> account.id == accountId }
            ?.openingBalanceMinor
            ?: 0L
}

enum class AccountHistoryMode {
    VALUE,
    PERFORMANCE,
    SNAPSHOTS,
}

enum class AccountHistoryRange {
    ONE_MONTH,
    THREE_MONTHS,
    SIX_MONTHS,
    ONE_YEAR,
    THREE_YEARS,
    CUSTOM,
    ALL,
}

data class AccountHistoryChart(
    val title: String,
    val subtitle: String,
    val points: List<AccountHistoryPoint>,
    val valueFormat: AccountHistoryValueFormat,
    val currencyCode: String? = null,
    val minimumLabel: String,
    val maximumLabel: String,
    val startLabel: String,
    val endLabel: String,
)

enum class AccountHistoryValueFormat {
    MONEY,
    PERFORMANCE_PERCENT,
}

data class AccountHistoryPoint(
    val axisLabel: String,
    val detailLabel: String,
    val timestampEpochMs: Long,
    val value: Double,
)

internal fun AccountsUiState.toCreateMode(): AccountsUiState =
    copy(
        selectedAccountId = null,
        selectedInvestmentPositions = emptyList(),
        accountHistoryCharts = emptyMap(),
        selectedAccountHistoryMode = AccountHistoryMode.VALUE,
        selectedAccountHistoryRange = AccountHistoryRange.ALL,
        customAccountHistoryStartEpochMs = null,
        customAccountHistoryEndEpochMs = null,
        isLoadingAccountHistory = false,
        accountHistoryErrorMessage = null,
        draftName = "",
        selectedType = AccountType.CHECKING,
        draftOpeningBalance = "",
        isFormVisible = false,
        editingAccountId = null,
        deleteConfirmationAccountId = null,
        isSaving = false,
        pendingDeleteAccountId = null,
        errorMessage = null,
    )
