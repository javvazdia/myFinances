package com.myfinances.app.presentation.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.AccountSourceType
import com.myfinances.app.domain.model.AccountType
import com.myfinances.app.domain.model.calculateAccountCurrentBalances
import com.myfinances.app.domain.repository.LedgerRepository
import com.myfinances.app.integrations.indexa.sync.INDEXA_PROVIDER_NAME
import com.myfinances.app.integrations.indexa.sync.IndexaIntegrationService
import com.myfinances.app.logging.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

class AccountsViewModel(
    private val ledgerRepository: LedgerRepository,
    private val indexaIntegrationService: IndexaIntegrationService? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()
    private var selectedPositionsJob: Job? = null
    private var selectedTransactionsJob: Job? = null
    private var selectedPerformanceJob: Job? = null
    private var selectedSnapshotsJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                ledgerRepository.observeAccounts(),
                ledgerRepository.observeAllTransactions(),
            ) { accounts, transactions ->
                accounts to transactions
            }.collect { (accounts, transactions) ->
                val currentBalances = calculateAccountCurrentBalances(
                    accounts = accounts,
                    transactions = transactions,
                )

                _uiState.update { currentState ->
                    currentState.copy(
                        accounts = accounts,
                        currentBalancesByAccountId = currentBalances,
                        selectedAccountId = currentState.selectedAccountId
                            ?.takeIf { selectedId -> accounts.any { account -> account.id == selectedId } },
                        editingAccountId = currentState.editingAccountId
                            ?.takeIf { editingId -> accounts.any { account -> account.id == editingId } },
                        deleteConfirmationAccountId = currentState.deleteConfirmationAccountId
                            ?.takeIf { deleteId -> accounts.any { account -> account.id == deleteId } },
                        pendingDeleteAccountId = currentState.pendingDeleteAccountId
                            ?.takeIf { deletingId -> accounts.any { account -> account.id == deletingId } },
                    )
                }

                val selectedId = uiState.value.selectedAccountId
                if (selectedId != null && accounts.none { account -> account.id == selectedId }) {
                    selectedPositionsJob?.cancel()
                    selectedTransactionsJob?.cancel()
                    selectedPerformanceJob?.cancel()
                    selectedSnapshotsJob?.cancel()
                    _uiState.update { currentState ->
                        currentState.copy(
                            selectedAccountId = null,
                            selectedInvestmentPositions = emptyList(),
                            accountHistoryCharts = emptyMap(),
                            selectedAccountHistoryMode = AccountHistoryMode.VALUE,
                            selectedAccountHistoryRange = AccountHistoryRange.ALL,
                            customAccountHistoryStartEpochMs = null,
                            customAccountHistoryEndEpochMs = null,
                            isLoadingAccountHistory = false,
                            accountHistoryErrorMessage = null,
                        )
                    }
                }

                if (uiState.value.isEditing && uiState.value.editingAccount == null) {
                    hideAccountForm()
                }
            }
        }
    }

    fun showCreateForm() {
        _uiState.update { currentState ->
            currentState.toCreateMode().copy(
                isFormVisible = true,
                selectedAccountId = null,
                draftCurrencyCode = currentState.draftCurrencyCode,
            )
        }
    }

    fun hideAccountForm() {
        _uiState.update { currentState ->
            currentState.toCreateMode().copy(
                draftCurrencyCode = currentState.draftCurrencyCode,
            )
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { currentState ->
            currentState.copy(
                draftName = value,
                errorMessage = null,
            )
        }
    }

    fun onTypeSelected(value: AccountType) {
        _uiState.update { currentState ->
            currentState.copy(
                selectedType = value,
                errorMessage = null,
            )
        }
    }

    fun onCurrencyCodeChange(value: String) {
        _uiState.update { currentState ->
            currentState.copy(
                draftCurrencyCode = value,
                errorMessage = null,
            )
        }
    }

    fun onOpeningBalanceChange(value: String) {
        _uiState.update { currentState ->
            currentState.copy(
                draftOpeningBalance = value,
                errorMessage = null,
            )
        }
    }

    fun selectAccountHistoryMode(mode: AccountHistoryMode) {
        _uiState.update { currentState ->
            if (currentState.accountHistoryCharts.containsKey(mode)) {
                currentState.copy(selectedAccountHistoryMode = mode)
            } else {
                currentState
            }
        }
    }

    fun selectAccountHistoryRange(range: AccountHistoryRange) {
        _uiState.update { currentState ->
            currentState.copy(selectedAccountHistoryRange = range)
        }
    }

    fun applyCustomAccountHistoryRange(
        startEpochMs: Long,
        endEpochMs: Long,
    ) {
        _uiState.update { currentState ->
            currentState.copy(
                selectedAccountHistoryRange = AccountHistoryRange.CUSTOM,
                customAccountHistoryStartEpochMs = startEpochMs,
                customAccountHistoryEndEpochMs = endEpochMs,
            )
        }
    }

    fun saveAccount() {
        val snapshot = uiState.value
        val existingAccount = snapshot.editingAccount
        val name = snapshot.draftName.trim()
        val currencyCode = normalizeCurrencyCode(snapshot.draftCurrencyCode)
        val openingBalanceMinor = parseAmountToMinor(snapshot.draftOpeningBalance)

        val error = when {
            name.isBlank() -> "Account name is required."
            currencyCode == null -> "Currency code must be 3 letters, like EUR or USD."
            openingBalanceMinor == null -> "Balance must be a valid number with up to 2 decimals."
            else -> null
        }

        if (error != null) {
            _uiState.update { currentState ->
                currentState.copy(errorMessage = error)
            }
            return
        }

        val validatedCurrencyCode = currencyCode ?: return
        val validatedOpeningBalanceMinor = openingBalanceMinor ?: return

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    isSaving = true,
                    errorMessage = null,
                )
            }

            val now = Clock.System.now().toEpochMilliseconds()
            val account = Account(
                id = existingAccount?.id ?: generateAccountId(name, now),
                name = name,
                type = snapshot.selectedType,
                currencyCode = validatedCurrencyCode,
                openingBalanceMinor = validatedOpeningBalanceMinor,
                sourceType = existingAccount?.sourceType ?: AccountSourceType.MANUAL,
                sourceProvider = existingAccount?.sourceProvider,
                externalAccountId = existingAccount?.externalAccountId,
                lastSyncedAtEpochMs = existingAccount?.lastSyncedAtEpochMs,
                isArchived = existingAccount?.isArchived ?: false,
                createdAtEpochMs = existingAccount?.createdAtEpochMs ?: now,
                updatedAtEpochMs = now,
            )

            ledgerRepository.upsertAccount(account)

            _uiState.update { currentState ->
                currentState.toCreateMode().copy(
                    draftCurrencyCode = validatedCurrencyCode,
                )
            }
        }
    }

    fun selectAccount(accountId: String) {
        _uiState.update { currentState ->
            currentState.toCreateMode().copy(
                selectedAccountId = accountId,
                accountHistoryCharts = emptyMap(),
                selectedAccountHistoryMode = AccountHistoryMode.VALUE,
                selectedAccountHistoryRange = AccountHistoryRange.ALL,
                customAccountHistoryStartEpochMs = null,
                customAccountHistoryEndEpochMs = null,
                isLoadingAccountHistory = false,
                accountHistoryErrorMessage = null,
                draftCurrencyCode = currentState.draftCurrencyCode,
            )
        }

        val selectedAccount = uiState.value.accounts.firstOrNull { account -> account.id == accountId }

        selectedPositionsJob?.cancel()
        selectedPositionsJob = viewModelScope.launch {
            ledgerRepository.observeInvestmentPositions(accountId).collect { positions ->
                _uiState.update { currentState ->
                    if (currentState.selectedAccountId != accountId) {
                        currentState
                    } else {
                        currentState.copy(selectedInvestmentPositions = positions)
                    }
                }
            }
        }

        selectedTransactionsJob?.cancel()
        selectedPerformanceJob?.cancel()
        selectedSnapshotsJob?.cancel()
        selectedSnapshotsJob = viewModelScope.launch {
            ledgerRepository.observeAccountValuationSnapshots(accountId).collect { snapshots ->
                val snapshotChart = selectedAccount?.let { account ->
                    buildSnapshotHistoryChart(account = account, snapshots = snapshots)
                }
                AppLogger.debug(
                    tag = "AccountsHistory",
                    message = "Rendering snapshot history for $accountId with ${snapshots.size} snapshot(s)",
                )
                _uiState.update { currentState ->
                    if (currentState.selectedAccountId != accountId) {
                        currentState
                    } else {
                        val updatedCharts = currentState.accountHistoryCharts
                            .toMutableMap()
                            .apply {
                                if (snapshotChart == null) {
                                    remove(AccountHistoryMode.SNAPSHOTS)
                                } else {
                                    put(AccountHistoryMode.SNAPSHOTS, snapshotChart)
                                }
                            }
                        currentState.copy(
                            accountHistoryCharts = updatedCharts,
                            selectedAccountHistoryMode = currentState.selectedAccountHistoryMode
                                .takeIf(updatedCharts::containsKey)
                                ?: updatedCharts.keys.firstOrNull()
                                ?: AccountHistoryMode.VALUE,
                        )
                    }
                }
            }
        }
        if (selectedAccount != null) {
            observeAccountHistory(selectedAccount)
        }
    }

    fun closeAccountDetails() {
        selectedPositionsJob?.cancel()
        selectedPositionsJob = null
        selectedTransactionsJob?.cancel()
        selectedTransactionsJob = null
        selectedPerformanceJob?.cancel()
        selectedPerformanceJob = null
        selectedSnapshotsJob?.cancel()
        selectedSnapshotsJob = null
        _uiState.update { currentState ->
            currentState.copy(
                selectedAccountId = null,
                selectedInvestmentPositions = emptyList(),
                accountHistoryCharts = emptyMap(),
                selectedAccountHistoryMode = AccountHistoryMode.VALUE,
                selectedAccountHistoryRange = AccountHistoryRange.ALL,
                customAccountHistoryStartEpochMs = null,
                customAccountHistoryEndEpochMs = null,
                isLoadingAccountHistory = false,
                accountHistoryErrorMessage = null,
            )
        }
    }

    fun editAccount(accountId: String) {
        closeAccountDetails()

        _uiState.update { currentState ->
            val account = currentState.accounts.firstOrNull { item -> item.id == accountId }
                ?: return@update currentState

            currentState.copy(
                draftName = account.name,
                selectedType = account.type,
                draftCurrencyCode = account.currencyCode,
                draftOpeningBalance = formatAccountAmountInput(account.openingBalanceMinor),
                isFormVisible = true,
                editingAccountId = account.id,
                deleteConfirmationAccountId = null,
                pendingDeleteAccountId = null,
                errorMessage = null,
            )
        }
    }

    fun requestDeleteAccount(accountId: String) {
        _uiState.update { currentState ->
            currentState.copy(
                deleteConfirmationAccountId = accountId,
                errorMessage = null,
            )
        }
    }

    fun dismissDeleteDialog() {
        _uiState.update { currentState ->
            currentState.copy(
                deleteConfirmationAccountId = null,
                errorMessage = null,
            )
        }
    }

    fun confirmDeleteAccount() {
        val snapshot = uiState.value
        val accountId = snapshot.deleteConfirmationAccountId ?: return
        val currentCurrency = snapshot.draftCurrencyCode

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    deleteConfirmationAccountId = null,
                    pendingDeleteAccountId = accountId,
                    errorMessage = null,
                )
            }

            ledgerRepository.deleteAccount(accountId)

            _uiState.update { currentState ->
                currentState.toCreateMode().copy(
                    draftCurrencyCode = currentCurrency,
                )
            }
        }
    }

    private fun observeAccountHistory(account: Account) {
        if (
            account.sourceType == AccountSourceType.API_SYNC &&
            account.sourceProvider == INDEXA_PROVIDER_NAME
        ) {
            AppLogger.debug(
                tag = "AccountsHistory",
                message = "Attempting Indexa history load for account ${account.id} (${account.name})",
            )
            _uiState.update { currentState ->
                currentState.copy(
                    isLoadingAccountHistory = true,
                    accountHistoryErrorMessage = null,
                    accountHistoryCharts = emptyMap(),
                    selectedAccountHistoryMode = AccountHistoryMode.VALUE,
                    selectedAccountHistoryRange = AccountHistoryRange.ALL,
                    customAccountHistoryStartEpochMs = null,
                    customAccountHistoryEndEpochMs = null,
                )
            }

            selectedPerformanceJob = viewModelScope.launch {
                val performanceHistory = runCatching {
                    indexaIntegrationService?.fetchPerformanceHistory(account.id)
                }.getOrElse { throwable ->
                    AppLogger.error(
                        tag = "AccountsHistory",
                        message = "Indexa history load failed for account ${account.id}: ${throwable.message}",
                        throwable = throwable,
                    )
                    if (uiState.value.selectedAccountId == account.id) {
                        _uiState.update { currentState ->
                            currentState.copy(
                                isLoadingAccountHistory = false,
                                accountHistoryErrorMessage = throwable.message
                                    ?: "Indexa performance history could not be loaded.",
                            )
                        }
                    }
                    null
                }

                val historyCharts = performanceHistory?.let { history ->
                    buildIndexaHistoryCharts(
                        history = history,
                        currencyCode = account.currencyCode,
                        currentBalanceMinor = uiState.value.currentBalanceMinorFor(account.id),
                    )
                }
                AppLogger.debug(
                    tag = "AccountsHistory",
                    message = "History chart modes for ${account.id}: ${historyCharts?.keys?.joinToString() ?: "none"}",
                )

                if (historyCharts != null && historyCharts.isNotEmpty() && uiState.value.selectedAccountId == account.id) {
                    _uiState.update { currentState ->
                        val updatedCharts = currentState.accountHistoryCharts
                            .filterKeys { mode -> mode == AccountHistoryMode.SNAPSHOTS }
                            .toMutableMap()
                            .apply { putAll(historyCharts) }
                        currentState.copy(
                            isLoadingAccountHistory = false,
                            accountHistoryErrorMessage = null,
                            accountHistoryCharts = updatedCharts,
                            selectedAccountHistoryMode = AccountHistoryMode.VALUE
                                .takeIf(updatedCharts::containsKey)
                                ?: updatedCharts.keys.first(),
                        )
                    }
                    return@launch
                }

                if (uiState.value.selectedAccountId == account.id) {
                    AppLogger.debug(
                        tag = "AccountsHistory",
                        message = "Falling back to local account history for ${account.id}",
                    )
                    startLocalAccountHistoryObservation(
                        account = account,
                        fallbackMessage = "Indexa evolution data is unavailable for this account right now. Showing local imported cash history instead.",
                    )
                }
            }

            return
        }

        startLocalAccountHistoryObservation(account = account)
    }

    private fun startLocalAccountHistoryObservation(
        account: Account,
        fallbackMessage: String? = null,
    ) {
        selectedTransactionsJob?.cancel()
        selectedTransactionsJob = viewModelScope.launch {
            ledgerRepository.observeTransactionsForAccount(account.id).collect { transactions ->
                AppLogger.debug(
                    tag = "AccountsHistory",
                    message = "Rendering local history for ${account.id} with ${transactions.size} transaction(s)",
                )
                _uiState.update { currentState ->
                    if (currentState.selectedAccountId != account.id) {
                        currentState
                    } else {
                        val updatedCharts = currentState.accountHistoryCharts
                            .filterKeys { mode -> mode == AccountHistoryMode.SNAPSHOTS }
                            .toMutableMap()
                            .apply {
                                put(
                                    AccountHistoryMode.VALUE,
                                    buildLocalAccountHistoryChart(account, transactions),
                                )
                            }
                        currentState.copy(
                            accountHistoryCharts = updatedCharts,
                            selectedAccountHistoryMode = currentState.selectedAccountHistoryMode
                                .takeIf(updatedCharts::containsKey)
                                ?: AccountHistoryMode.VALUE,
                            isLoadingAccountHistory = false,
                            accountHistoryErrorMessage = fallbackMessage,
                        )
                    }
                }
            }
        }
    }
}
