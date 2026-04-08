package com.myfinances.app.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfinances.app.domain.model.TransactionType
import com.myfinances.app.domain.repository.LedgerRepository
import com.myfinances.app.integrations.statements.StatementImportService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TRANSACTION_PAGE_SIZE_STEP = 30

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModel(
    private val ledgerRepository: LedgerRepository,
    private val statementImportService: StatementImportService,
) : ViewModel() {
    private val transactionLimit = MutableStateFlow(INITIAL_TRANSACTION_PAGE_SIZE)
    private val _uiState = MutableStateFlow(
        TransactionsUiState(
            isStatementImportSupported = statementImportService.isSupported,
            transactionLimit = INITIAL_TRANSACTION_PAGE_SIZE,
        ),
    )
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                ledgerRepository.observeAccounts(),
                ledgerRepository.observeCategories(),
                transactionLimit.flatMapLatest { limit ->
                    ledgerRepository.observeRecentTransactions(limit)
                },
                transactionLimit,
            ) { accounts, categories, transactions, currentTransactionLimit ->
                TransactionCollectionSnapshot(
                    accounts = accounts,
                    categories = categories,
                    transactions = transactions,
                    currentTransactionLimit = currentTransactionLimit,
                )
            }.collect { snapshot ->
                _uiState.update { currentState ->
                    currentState.applyCollectionSnapshot(
                        snapshot = snapshot,
                        isStatementImportSupported = statementImportService.isSupported,
                    )
                }
            }
        }
    }

    fun loadMoreTransactions() {
        val snapshot = uiState.value
        if (snapshot.isLoadingMoreTransactions || !snapshot.canLoadMoreTransactions) return

        val nextLimit = snapshot.transactionLimit + TRANSACTION_PAGE_SIZE_STEP
        transactionLimit.value = nextLimit
        _uiState.update { currentState ->
            currentState.copy(
                transactionLimit = nextLimit,
                isLoadingMoreTransactions = true,
            )
        }
    }

    fun showCreateForm() {
        _uiState.update { currentState ->
            currentState.showCreateFormMode()
        }
    }

    fun hideTransactionForm() {
        _uiState.update { currentState ->
            currentState.hideFormMode()
        }
    }

    fun importCajaIngenierosPdf() {
        if (!statementImportService.isSupported) {
            _uiState.update { currentState ->
                currentState.copy(
                    errorMessage = "PDF statement import is not available on this platform yet.",
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    isImportingStatement = true,
                    importMessage = null,
                    errorMessage = null,
                )
            }

            runCatching {
                statementImportService.importCajaIngenierosPdf()
            }.onSuccess { result ->
                _uiState.update { currentState ->
                    currentState.copy(
                        isImportingStatement = false,
                        importMessage = result?.let { importResult ->
                            buildImportMessage(importResult)
                        },
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { currentState ->
                    currentState.copy(
                        isImportingStatement = false,
                        errorMessage = throwable.message ?: "PDF import failed.",
                    )
                }
            }
        }
    }

    fun onTypeSelected(value: TransactionType) {
        _uiState.update { currentState ->
            val availableCategories = currentState.categories.filter { category ->
                category.kind == value.categoryKind
            }
            currentState.copy(
                selectedType = value,
                selectedCategoryId = availableCategories.firstOrNull()?.id,
                errorMessage = null,
            )
        }
    }

    fun onAccountSelected(value: String) {
        _uiState.update { currentState ->
            currentState.copy(
                selectedAccountId = value,
                errorMessage = null,
            )
        }
    }

    fun onCategorySelected(value: String) {
        _uiState.update { currentState ->
            currentState.copy(
                selectedCategoryId = value,
                errorMessage = null,
            )
        }
    }

    fun onAmountChange(value: String) {
        _uiState.update { currentState ->
            currentState.copy(
                draftAmount = value,
                errorMessage = null,
            )
        }
    }

    fun onMerchantChange(value: String) {
        _uiState.update { currentState ->
            currentState.copy(
                draftMerchant = value,
                errorMessage = null,
            )
        }
    }

    fun onNoteChange(value: String) {
        _uiState.update { currentState ->
            currentState.copy(
                draftNote = value,
                errorMessage = null,
            )
        }
    }

    fun editTransaction(transactionId: String) {
        _uiState.update { currentState ->
            val transaction = currentState.transactions.firstOrNull { item -> item.id == transactionId }
                ?: return@update currentState
            if (transaction.isProviderManaged()) {
                return@update currentState.copy(
                    errorMessage = "Synced provider transactions are read-only right now.",
                )
            }

            currentState.startEditing(transaction)
        }
    }

    fun cancelEditing() {
        hideTransactionForm()
    }

    fun saveTransaction() {
        val snapshot = uiState.value
        val validation = snapshot.validateDraft()
        if (validation.isFailure) {
            _uiState.update { currentState ->
                currentState.copy(
                    errorMessage = validation.exceptionOrNull()?.message ?: "Transaction data is invalid.",
                )
            }
            return
        }
        val validatedDraft = validation.getOrThrow()

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    isSaving = true,
                    errorMessage = null,
                )
            }

            val transaction = snapshot.buildTransaction(validatedDraft)
            ledgerRepository.upsertTransaction(transaction)

            _uiState.update { currentState ->
                currentState.toCreateMode().copy(
                    selectedType = snapshot.selectedType,
                    selectedAccountId = snapshot.selectedAccountId,
                    selectedCategoryId = snapshot.selectedCategoryId,
                    isSaving = false,
                )
            }
        }
    }

    fun requestDeleteTransaction(transactionId: String) {
        _uiState.update { currentState ->
            val transaction = currentState.transactions.firstOrNull { item -> item.id == transactionId }
                ?: return@update currentState
            if (transaction.isProviderManaged()) {
                return@update currentState.copy(
                    errorMessage = "Synced provider transactions are managed by the provider sync.",
                )
            }

            currentState.copy(
                selectedTransactionDetailId = null,
                deleteConfirmationTransactionId = transactionId,
                errorMessage = null,
            )
        }
    }

    fun showTransactionDetails(transactionId: String) {
        _uiState.update { currentState ->
            currentState.copy(
                selectedTransactionDetailId = transactionId,
                errorMessage = null,
            )
        }
    }

    fun dismissTransactionDetails() {
        _uiState.update { currentState ->
            currentState.copy(
                selectedTransactionDetailId = null,
            )
        }
    }

    fun dismissDeleteDialog() {
        _uiState.update { currentState ->
            currentState.copy(
                deleteConfirmationTransactionId = null,
                errorMessage = null,
            )
        }
    }

    fun confirmDeleteTransaction() {
        val transactionId = uiState.value.deleteConfirmationTransactionId ?: return

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    deleteConfirmationTransactionId = null,
                    pendingDeleteTransactionId = transactionId,
                    errorMessage = null,
                )
            }

            ledgerRepository.deleteTransaction(transactionId)

            _uiState.update { currentState ->
                if (currentState.editingTransactionId == transactionId) {
                    currentState.toCreateMode().copy(
                        selectedType = currentState.selectedType,
                        selectedAccountId = currentState.selectedAccountId,
                        selectedCategoryId = currentState.selectedCategoryId,
                    )
                } else {
                    currentState.copy(
                        pendingDeleteTransactionId = null,
                        errorMessage = null,
                    )
                }
            }
        }
    }
}
