package com.myfinances.app.presentation.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.AccountSourceType
import com.myfinances.app.domain.model.AccountType
import com.myfinances.app.domain.model.InvestmentPosition
import com.myfinances.app.domain.model.calculateAccountCurrentBalances
import com.myfinances.app.domain.repository.LedgerRepository
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
import kotlin.random.Random
import kotlin.time.Clock

data class AccountsUiState(
    val accounts: List<Account> = emptyList(),
    val currentBalancesByAccountId: Map<String, Long> = emptyMap(),
    val selectedAccountId: String? = null,
    val selectedInvestmentPositions: List<InvestmentPosition> = emptyList(),
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

    fun currentBalanceMinorFor(accountId: String): Long =
        currentBalancesByAccountId[accountId] ?: accounts
            .firstOrNull { account -> account.id == accountId }
            ?.openingBalanceMinor
            ?: 0L
}

class AccountsViewModel(
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()
    private var selectedPositionsJob: Job? = null

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
                    _uiState.update { currentState ->
                        currentState.copy(
                            selectedAccountId = null,
                            selectedInvestmentPositions = emptyList(),
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
                draftCurrencyCode = currentState.draftCurrencyCode,
            )
        }

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
    }

    fun closeAccountDetails() {
        selectedPositionsJob?.cancel()
        selectedPositionsJob = null
        _uiState.update { currentState ->
            currentState.copy(
                selectedAccountId = null,
                selectedInvestmentPositions = emptyList(),
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

private fun formatHoldingDecimal(value: Double): String =
    value
        .toString()
        .trimEnd('0')
        .trimEnd('.')

private fun AccountsUiState.toCreateMode(): AccountsUiState =
    copy(
        selectedAccountId = null,
        selectedInvestmentPositions = emptyList(),
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
