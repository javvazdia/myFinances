package com.myfinances.app.presentation.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.AccountValuationSnapshot
import com.myfinances.app.domain.model.AccountSourceType
import com.myfinances.app.domain.model.AccountType
import com.myfinances.app.domain.model.FinanceTransaction
import com.myfinances.app.domain.model.InvestmentPosition
import com.myfinances.app.domain.model.TransactionType
import com.myfinances.app.domain.model.calculateAccountCurrentBalances
import com.myfinances.app.domain.repository.LedgerRepository
import com.myfinances.app.integrations.indexa.model.IndexaPerformanceHistory
import com.myfinances.app.integrations.indexa.sync.INDEXA_PROVIDER_NAME
import com.myfinances.app.integrations.indexa.sync.IndexaIntegrationService
import com.myfinances.app.logging.AppLogger
import com.myfinances.app.presentation.shared.formatMinorMoney
import com.myfinances.app.presentation.shared.formatTimestampLabel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant

data class AccountsUiState(
    val accounts: List<Account> = emptyList(),
    val currentBalancesByAccountId: Map<String, Long> = emptyMap(),
    val selectedAccountId: String? = null,
    val selectedInvestmentPositions: List<InvestmentPosition> = emptyList(),
    val accountHistoryCharts: Map<AccountHistoryMode, AccountHistoryChart> = emptyMap(),
    val selectedAccountHistoryMode: AccountHistoryMode = AccountHistoryMode.VALUE,
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
            ?: accountHistoryCharts[AccountHistoryMode.VALUE]
            ?: accountHistoryCharts.values.firstOrNull()

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

data class AccountHistoryChart(
    val title: String,
    val subtitle: String,
    val points: List<AccountHistoryPoint>,
    val minimumLabel: String,
    val maximumLabel: String,
    val startLabel: String,
    val endLabel: String,
)

data class AccountHistoryPoint(
    val label: String,
    val value: Double,
)

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

internal val AccountType.label: String
    get() = name
        .lowercase()
        .split('_')
        .joinToString(" ") { part ->
            part.replaceFirstChar { character -> character.uppercase() }
        }

internal fun normalizeCurrencyCode(rawValue: String): String? {
    val normalized = rawValue.trim().uppercase()
    return normalized.takeIf { value ->
        value.length == 3 && value.all(Char::isLetter)
    }
}

internal fun parseAmountToMinor(rawValue: String): Long? {
    val normalized = rawValue.trim().replace(',', '.')
    if (normalized.isBlank()) return 0L

    val amountPattern = Regex("^-?\\d+(\\.\\d{0,2})?$")
    if (!amountPattern.matches(normalized)) return null

    val isNegative = normalized.startsWith('-')
    val unsignedValue = normalized.removePrefix("-")
    val parts = unsignedValue.split('.')
    val wholePart = parts.firstOrNull()?.toLongOrNull() ?: return null
    val decimalPart = parts.getOrNull(1)?.padEnd(2, '0') ?: "00"
    val minorAmount = (wholePart * 100) + decimalPart.toLong()

    return if (isNegative) -minorAmount else minorAmount
}

internal fun generateAccountId(name: String, timestampMs: Long): String {
    val slug = name
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "account" }

    return "account-$slug-$timestampMs-${Random.nextInt(1000, 9999)}"
}

internal fun formatMoney(amountMinor: Long, currencyCode: String): String =
    formatMinorMoney(amountMinor, currencyCode)

internal fun formatSyncTimestamp(epochMs: Long?): String {
    if (epochMs == null) return "Not synced yet"
    return formatTimestampLabel(epochMs)
}

internal fun buildPositionSubtitle(position: InvestmentPosition): String {
    val assetClass = position.assetClass ?: "Portfolio holding"
    val quantity = position.titles?.let { titles ->
        "${formatHoldingDecimal(titles)} units"
    } ?: "Units unavailable"
    val price = position.price?.let { value ->
        "@ ${formatHoldingDecimal(value)}"
    }

    return listOfNotNull(assetClass, quantity, price).joinToString(" | ")
}

internal fun buildPositionValuationLabel(
    position: InvestmentPosition,
    currencyCode: String,
): String {
    val marketValue = position.marketValueMinor?.let { amount ->
        formatMoney(amount, currencyCode)
    } ?: "Unknown value"
    val costBasis = position.costAmountMinor?.let { amount ->
        "Cost ${formatMoney(amount, currencyCode)}"
    }

    return listOfNotNull("Market value $marketValue", costBasis).joinToString(" | ")
}

internal fun formatAccountAmountInput(amountMinor: Long): String {
    val absoluteAmount = kotlin.math.abs(amountMinor)
    val major = absoluteAmount / 100
    val minor = absoluteAmount % 100
    val prefix = if (amountMinor < 0) "-" else ""
    return "$prefix$major.${minor.toString().padStart(2, '0')}"
}

internal fun buildLocalAccountHistoryChart(
    account: Account,
    transactions: List<FinanceTransaction>,
): AccountHistoryChart {
    val sortedTransactions = transactions.sortedBy(FinanceTransaction::postedAtEpochMs)
    val firstTransactionDate = sortedTransactions.firstOrNull()?.postedAtEpochMs
    val startingPointEpochMs = firstTransactionDate ?: account.createdAtEpochMs
    var runningBalanceMinor = account.openingBalanceMinor
    val points = buildList {
        add(
            AccountHistoryPoint(
                label = formatHistoryDate(startingPointEpochMs),
                value = runningBalanceMinor.toDouble() / 100.0,
            ),
        )
        sortedTransactions.forEach { transaction ->
            runningBalanceMinor += transaction.balanceDeltaMinor()
            add(
                AccountHistoryPoint(
                    label = formatHistoryDate(transaction.postedAtEpochMs),
                    value = runningBalanceMinor.toDouble() / 100.0,
                ),
            )
        }
    }

    return buildAccountHistoryChart(
        title = "Balance history",
        subtitle = "Running balance from the opening balance and tracked transactions.",
        points = points,
        valueFormatter = { value -> formatMinorMoney((value * 100).toLong(), account.currencyCode) },
    )
}

internal fun buildIndexaHistoryCharts(
    history: IndexaPerformanceHistory,
    currencyCode: String,
    currentBalanceMinor: Long,
): Map<AccountHistoryMode, AccountHistoryChart> {
    val charts = linkedMapOf<AccountHistoryMode, AccountHistoryChart>()

    val resolvedValueHistory = history.valueHistory
        .takeIf { valueHistory -> valueHistory.isNotEmpty() }
        ?: reconstructIndexaValueHistory(
            normalizedHistory = history.normalizedHistory,
            currentBalanceMinor = currentBalanceMinor,
        )

    val valuePoints = resolvedValueHistory
        .toSortedMap()
        .map { (date, value) ->
            AccountHistoryPoint(
                label = formatIsoDate(date),
                value = value,
            )
        }

    if (valuePoints.isNotEmpty()) {
        charts[AccountHistoryMode.VALUE] = buildAccountHistoryChart(
            title = "Indexa account value",
            subtitle = if (history.valueHistory.isNotEmpty()) {
                "Historical account value from the Indexa performance endpoint."
            } else {
                "Estimated account value reconstructed from Indexa evolution data and the current synced portfolio value."
            },
            points = valuePoints,
            valueFormatter = { value -> formatMinorMoney((value * 100).toLong(), currencyCode) },
        )
    }

    val performancePoints = history.normalizedHistory
        .toSortedMap()
        .map { (date, value) ->
            AccountHistoryPoint(
                label = formatIsoDate(date),
                value = value * 100.0,
            )
        }

    if (performancePoints.isNotEmpty()) {
        charts[AccountHistoryMode.PERFORMANCE] = buildAccountHistoryChart(
            title = "Indexa performance history",
            subtitle = "Normalized evolution from Indexa. A value of 100 means the starting point of the series.",
            points = performancePoints,
            valueFormatter = { value -> "${value.toInt()}" },
        )
    }

    return charts
}

internal fun buildSnapshotHistoryChart(
    account: Account,
    snapshots: List<AccountValuationSnapshot>,
): AccountHistoryChart? {
    val points = snapshots
        .sortedBy(AccountValuationSnapshot::capturedAtEpochMs)
        .map { snapshot ->
            AccountHistoryPoint(
                label = snapshot.valuationDate?.let(::formatIsoDate)
                    ?: formatHistoryDate(snapshot.capturedAtEpochMs),
                value = snapshot.valueMinor.toDouble() / 100.0,
            )
        }

    if (points.isEmpty()) return null

    return buildAccountHistoryChart(
        title = "Snapshot history",
        subtitle = "Locally stored balance snapshots captured during syncs. Useful even when a provider does not expose full historical data.",
        points = points,
        valueFormatter = { value -> formatMinorMoney((value * 100).toLong(), account.currencyCode) },
    )
}

private fun reconstructIndexaValueHistory(
    normalizedHistory: Map<String, Double>,
    currentBalanceMinor: Long,
): Map<String, Double> {
    if (normalizedHistory.isEmpty()) return emptyMap()

    val latestEntry = normalizedHistory.toSortedMap().lastEntry()
    val latestNormalizedValue = latestEntry.value
    if (latestNormalizedValue <= 0.0) return emptyMap()

    val currentBalance = currentBalanceMinor.toDouble() / 100.0
    if (currentBalance <= 0.0) return emptyMap()

    val baseValue = currentBalance / latestNormalizedValue
    return normalizedHistory.mapValues { (_, normalizedValue) ->
        baseValue * normalizedValue
    }
}

private fun buildAccountHistoryChart(
    title: String,
    subtitle: String,
    points: List<AccountHistoryPoint>,
    valueFormatter: (Double) -> String,
): AccountHistoryChart {
    val values = points.map(AccountHistoryPoint::value)
    val minimum = values.minOrNull() ?: 0.0
    val maximum = values.maxOrNull() ?: 0.0

    return AccountHistoryChart(
        title = title,
        subtitle = subtitle,
        points = points,
        minimumLabel = valueFormatter(minimum),
        maximumLabel = valueFormatter(maximum),
        startLabel = points.first().label,
        endLabel = points.last().label,
    )
}

private fun FinanceTransaction.balanceDeltaMinor(): Long = when (type) {
    TransactionType.INCOME -> amountMinor
    TransactionType.EXPENSE -> -amountMinor
    TransactionType.TRANSFER -> 0L
    TransactionType.ADJUSTMENT -> amountMinor
}

private fun formatHistoryDate(epochMs: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMs)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    return formatLocalDate(date)
}

private fun formatIsoDate(isoDate: String): String = runCatching {
    formatLocalDate(LocalDate.parse(isoDate))
}.getOrDefault(isoDate)

private fun formatLocalDate(date: LocalDate): String {
    val month = date.month.name
        .lowercase()
        .replaceFirstChar { character -> character.uppercase() }
        .take(3)
    return "$month ${date.day}"
}

private fun formatHoldingDecimal(value: Double): String =
    value
        .toString()
        .trimEnd('0')
        .trimEnd('.')

private fun AccountsUiState.toCreateMode(): AccountsUiState =
    copy(
        selectedAccountId = null,
        selectedInvestmentPositions = emptyList(),
        accountHistoryCharts = emptyMap(),
        selectedAccountHistoryMode = AccountHistoryMode.VALUE,
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
