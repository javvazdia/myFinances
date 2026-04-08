package com.myfinances.app.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.FinanceTransaction
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
import kotlin.time.Clock

private const val TRANSACTION_PAGE_SIZE_STEP = 30

private data class TransactionCollectionSnapshot(
    val accounts: List<Account>,
    val categories: List<Category>,
    val transactions: List<FinanceTransaction>,
    val currentTransactionLimit: Int,
)

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
                    val accounts = snapshot.accounts
                    val categories = snapshot.categories
                    val transactions = snapshot.transactions
                    val nextEditingTransactionId = currentState.editingTransactionId
                        ?.takeIf { editingId ->
                            transactions.any { transaction -> transaction.id == editingId }
                        }

                    val nextSelectedAccountId = currentState.selectedAccountId
                        ?.takeIf { selectedId -> accounts.any { account -> account.id == selectedId } }
                        ?: accounts.firstOrNull()?.id

                    val availableCategories = categories.filter { category ->
                        category.kind == currentState.selectedType.categoryKind
                    }
                    val nextSelectedCategoryId = currentState.selectedCategoryId
                        ?.takeIf { selectedId ->
                            availableCategories.any { category -> category.id == selectedId }
                        }
                        ?: availableCategories.firstOrNull()?.id

                    currentState.copy(
                        accounts = accounts,
                        categories = categories,
                        transactions = transactions,
                        recentTransactions = transactions.map { transaction ->
                            transaction.toCardUiModel(accounts, categories)
                        },
                        transactionLimit = snapshot.currentTransactionLimit,
                        canLoadMoreTransactions = transactions.size >= snapshot.currentTransactionLimit,
                        isLoadingMoreTransactions = false,
                        isStatementImportSupported = statementImportService.isSupported,
                        selectedAccountId = nextSelectedAccountId,
                        selectedCategoryId = nextSelectedCategoryId,
                        editingTransactionId = nextEditingTransactionId,
                        selectedTransactionDetailId = currentState.selectedTransactionDetailId
                            ?.takeIf { detailId ->
                                transactions.any { transaction -> transaction.id == detailId }
                            },
                        deleteConfirmationTransactionId = currentState.deleteConfirmationTransactionId
                            ?.takeIf { confirmationId ->
                                transactions.any { transaction -> transaction.id == confirmationId }
                            },
                        pendingDeleteTransactionId = currentState.pendingDeleteTransactionId
                            ?.takeIf { deletingId ->
                                transactions.any { transaction -> transaction.id == deletingId }
                            },
                        draftAmount = if (currentState.isEditing && nextEditingTransactionId == null) {
                            ""
                        } else {
                            currentState.draftAmount
                        },
                        draftMerchant = if (currentState.isEditing && nextEditingTransactionId == null) {
                            ""
                        } else {
                            currentState.draftMerchant
                        },
                        draftNote = if (currentState.isEditing && nextEditingTransactionId == null) {
                            ""
                        } else {
                            currentState.draftNote
                        },
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
            currentState.toCreateMode().copy(
                isFormVisible = true,
                selectedType = currentState.selectedType,
                selectedAccountId = currentState.selectedAccountId,
                selectedCategoryId = currentState.selectedCategoryId,
            )
        }
    }

    fun hideTransactionForm() {
        _uiState.update { currentState ->
            currentState.toCreateMode().copy(
                selectedType = currentState.selectedType,
                selectedAccountId = currentState.selectedAccountId,
                selectedCategoryId = currentState.selectedCategoryId,
            )
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

            val availableCategories = currentState.categories.filter { category ->
                category.kind == transaction.type.categoryKind
            }

            currentState.copy(
                isFormVisible = true,
                selectedType = transaction.type,
                selectedAccountId = transaction.accountId,
                selectedCategoryId = transaction.categoryId
                    ?.takeIf { selectedId ->
                        availableCategories.any { category -> category.id == selectedId }
                    }
                    ?: availableCategories.firstOrNull()?.id,
                draftAmount = formatTransactionAmountInput(transaction.amountMinor),
                draftMerchant = transaction.merchantName.orEmpty(),
                draftNote = transaction.note.orEmpty(),
                editingTransactionId = transaction.id,
                selectedTransactionDetailId = null,
                deleteConfirmationTransactionId = null,
                pendingDeleteTransactionId = null,
                errorMessage = null,
            )
        }
    }

    fun cancelEditing() {
        hideTransactionForm()
    }

    fun saveTransaction() {
        val snapshot = uiState.value
        val selectedAccount = snapshot.accounts.firstOrNull { it.id == snapshot.selectedAccountId }
        val selectedCategoryId = snapshot.selectedCategoryId
        val amountMinor = parseTransactionAmountToMinor(snapshot.draftAmount)
        val existingTransaction = snapshot.editingTransactionId?.let { editingId ->
            snapshot.transactions.firstOrNull { transaction -> transaction.id == editingId }
        }

        val error = when {
            existingTransaction?.isProviderManaged() == true ->
                "Synced provider transactions are read-only right now."
            selectedAccount == null -> "Select an account first."
            selectedCategoryId == null -> "Select a category first."
            amountMinor == null || amountMinor <= 0L ->
                "Amount must be a positive number with up to 2 decimals."
            else -> null
        }

        if (error != null) {
            _uiState.update { currentState ->
                currentState.copy(errorMessage = error)
            }
            return
        }

        val validatedAccount = selectedAccount ?: return
        val validatedAmountMinor = amountMinor ?: return

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    isSaving = true,
                    errorMessage = null,
                )
            }

            val now = Clock.System.now().toEpochMilliseconds()
            val transaction = FinanceTransaction(
                id = existingTransaction?.id ?: generateTransactionId(snapshot.selectedType, now),
                accountId = validatedAccount.id,
                categoryId = selectedCategoryId,
                type = snapshot.selectedType,
                amountMinor = validatedAmountMinor,
                currencyCode = validatedAccount.currencyCode,
                merchantName = snapshot.draftMerchant.trim().ifBlank { null },
                note = snapshot.draftNote.trim().ifBlank { null },
                sourceProvider = existingTransaction?.sourceProvider,
                externalTransactionId = existingTransaction?.externalTransactionId,
                postedAtEpochMs = existingTransaction?.postedAtEpochMs ?: now,
                createdAtEpochMs = existingTransaction?.createdAtEpochMs ?: now,
                updatedAtEpochMs = now,
            )

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
