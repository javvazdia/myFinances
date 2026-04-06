package com.myfinances.app.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.CategoryKind
import com.myfinances.app.domain.model.FinanceTransaction
import com.myfinances.app.domain.model.TransactionType
import com.myfinances.app.domain.repository.LedgerRepository
import com.myfinances.app.integrations.statements.StatementImportResult
import com.myfinances.app.integrations.statements.StatementImportService
import com.myfinances.app.presentation.shared.formatDayLabel
import com.myfinances.app.presentation.shared.formatMinorMoney
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock

data class TransactionsUiState(
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val transactions: List<FinanceTransaction> = emptyList(),
    val recentTransactions: List<TransactionCardUiModel> = emptyList(),
    val isStatementImportSupported: Boolean = false,
    val isImportingStatement: Boolean = false,
    val importMessage: String? = null,
    val isFormVisible: Boolean = false,
    val selectedType: TransactionType = TransactionType.EXPENSE,
    val selectedAccountId: String? = null,
    val selectedCategoryId: String? = null,
    val draftAmount: String = "",
    val draftMerchant: String = "",
    val draftNote: String = "",
    val editingTransactionId: String? = null,
    val selectedTransactionDetailId: String? = null,
    val deleteConfirmationTransactionId: String? = null,
    val isSaving: Boolean = false,
    val pendingDeleteTransactionId: String? = null,
    val errorMessage: String? = null,
) {
    val availableCategories: List<Category>
        get() = categories.filter { category -> category.kind == selectedType.categoryKind }

    val selectedAccountName: String?
        get() = accounts.firstOrNull { account -> account.id == selectedAccountId }?.name

    val selectedCategoryName: String?
        get() = categories.firstOrNull { category -> category.id == selectedCategoryId }?.name

    val isEditing: Boolean
        get() = editingTransactionId != null

    val deleteConfirmationTransactionTitle: String?
        get() = recentTransactions.firstOrNull { transaction ->
            transaction.id == deleteConfirmationTransactionId
        }?.title

    val selectedTransactionDetail: TransactionCardUiModel?
        get() = recentTransactions.firstOrNull { transaction ->
            transaction.id == selectedTransactionDetailId
        }

    val isBusy: Boolean
        get() = isSaving || pendingDeleteTransactionId != null || isImportingStatement
}

data class TransactionCardUiModel(
    val id: String,
    val title: String,
    val accountName: String,
    val categoryName: String,
    val amountLabel: String,
    val dateLabel: String,
    val sourceLabel: String?,
    val metadataPreview: String?,
    val detailRows: List<TransactionDetailRowUiModel>,
    val isProviderManaged: Boolean,
    val isExpense: Boolean,
)

data class TransactionDetailRowUiModel(
    val label: String,
    val value: String,
)

class TransactionsViewModel(
    private val ledgerRepository: LedgerRepository,
    private val statementImportService: StatementImportService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        TransactionsUiState(
            isStatementImportSupported = statementImportService.isSupported,
        ),
    )
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                ledgerRepository.observeAccounts(),
                ledgerRepository.observeCategories(),
                ledgerRepository.observeRecentTransactions(limit = 20),
            ) { accounts, categories, transactions ->
                Triple(accounts, categories, transactions)
            }.collect { (accounts, categories, transactions) ->
                _uiState.update { currentState ->
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

internal fun FinanceTransaction.toCardUiModel(
    accounts: List<Account>,
    categories: List<Category>,
): TransactionCardUiModel {
    val accountName = accounts.firstOrNull { account -> account.id == accountId }?.name ?: "Unknown account"
    val categoryName = categories.firstOrNull { category -> category.id == categoryId }?.name ?: "Uncategorized"
    val isExpense = type == TransactionType.EXPENSE
    val isProviderManaged = isProviderManaged()

    return TransactionCardUiModel(
        id = id,
        title = merchantName ?: type.label,
        accountName = accountName,
        categoryName = categoryName,
        amountLabel = buildSignedAmountLabel(amountMinor, currencyCode, isExpense),
        dateLabel = formatTransactionDateLabel(postedAtEpochMs),
        sourceLabel = buildTransactionSourceLabel(),
        metadataPreview = buildTransactionMetadataPreview(),
        detailRows = buildTransactionDetailRows(accountName, categoryName),
        isProviderManaged = isProviderManaged,
        isExpense = isExpense,
    )
}

private fun FinanceTransaction.buildTransactionMetadataPreview(): String? =
    note?.takeIf(String::isNotBlank)
        ?: externalTransactionId?.takeIf(String::isNotBlank)?.let { reference -> "Reference $reference" }

private fun FinanceTransaction.buildTransactionSourceLabel(): String? =
    sourceProvider?.takeIf(String::isNotBlank)?.let { provider ->
        if (externalTransactionId.isNullOrBlank()) {
            "Imported from $provider"
        } else {
            "Synced from $provider"
        }
    }

private fun FinanceTransaction.buildTransactionDetailRows(
    accountName: String,
    categoryName: String,
): List<TransactionDetailRowUiModel> = buildList {
    add(TransactionDetailRowUiModel(label = "Account", value = accountName))
    add(TransactionDetailRowUiModel(label = "Category", value = categoryName))
    add(TransactionDetailRowUiModel(label = "Type", value = type.label))
    add(TransactionDetailRowUiModel(label = "Date", value = formatTransactionDateLabel(postedAtEpochMs)))
    buildTransactionSourceLabel()?.let { sourceLabel ->
        add(TransactionDetailRowUiModel(label = "Source", value = sourceLabel))
    }
    externalTransactionId?.takeIf(String::isNotBlank)?.let { reference ->
        add(TransactionDetailRowUiModel(label = "Reference", value = reference))
    }
    merchantName?.takeIf(String::isNotBlank)?.let { merchant ->
        add(TransactionDetailRowUiModel(label = "Merchant", value = merchant))
    }
    note?.takeIf(String::isNotBlank)?.let { transactionNote ->
        add(TransactionDetailRowUiModel(label = "Notes", value = transactionNote))
    }
}

private fun buildSignedAmountLabel(
    amountMinor: Long,
    currencyCode: String,
    isExpense: Boolean,
): String {
    val sign = when {
        isExpense -> "-"
        amountMinor < 0L -> "-"
        else -> "+"
    }
    return sign + formatTransactionMoney(kotlin.math.abs(amountMinor), currencyCode)
}

internal fun FinanceTransaction.isProviderManaged(): Boolean =
    !sourceProvider.isNullOrBlank() && !externalTransactionId.isNullOrBlank()

internal val manualTransactionTypes: List<TransactionType> = listOf(
    TransactionType.EXPENSE,
    TransactionType.INCOME,
)

internal val TransactionType.label: String
    get() = name
        .lowercase()
        .replaceFirstChar { character -> character.uppercase() }

internal val TransactionType.categoryKind: CategoryKind
    get() = when (this) {
        TransactionType.INCOME -> CategoryKind.INCOME
        TransactionType.EXPENSE -> CategoryKind.EXPENSE
        TransactionType.TRANSFER -> CategoryKind.TRANSFER
        TransactionType.ADJUSTMENT -> CategoryKind.EXPENSE
    }

internal fun parseTransactionAmountToMinor(rawValue: String): Long? {
    val normalized = rawValue.trim().replace(',', '.')
    if (normalized.isBlank()) return null

    val amountPattern = Regex("^\\d+(\\.\\d{0,2})?$")
    if (!amountPattern.matches(normalized)) return null

    val parts = normalized.split('.')
    val wholePart = parts.firstOrNull()?.toLongOrNull() ?: return null
    val decimalPart = parts.getOrNull(1)?.padEnd(2, '0') ?: "00"
    return (wholePart * 100) + decimalPart.toLong()
}

internal fun generateTransactionId(type: TransactionType, timestampMs: Long): String =
    "txn-${type.name.lowercase()}-$timestampMs-${Random.nextInt(1000, 9999)}"

internal fun formatTransactionAmountInput(amountMinor: Long): String {
    val absoluteAmount = kotlin.math.abs(amountMinor)
    val major = absoluteAmount / 100
    val minor = absoluteAmount % 100
    return "$major.${minor.toString().padStart(2, '0')}"
}

internal fun formatTransactionMoney(amountMinor: Long, currencyCode: String): String =
    formatMinorMoney(amountMinor, currencyCode)

internal fun formatTransactionDateLabel(
    epochMs: Long,
    nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
): String = formatDayLabel(epochMs, nowEpochMs)

private fun TransactionsUiState.toCreateMode(): TransactionsUiState =
    copy(
        draftAmount = "",
        draftMerchant = "",
        draftNote = "",
        isFormVisible = false,
        editingTransactionId = null,
        selectedTransactionDetailId = null,
        deleteConfirmationTransactionId = null,
        isSaving = false,
        pendingDeleteTransactionId = null,
        errorMessage = null,
    )

private fun buildImportMessage(result: StatementImportResult): String =
    if (result.skippedTransactions > 0) {
        "Imported ${result.importedTransactions} transactions from ${result.sourceFileName} into ${result.accountName}. Skipped ${result.skippedTransactions} duplicates. Closing balance ${formatTransactionMoney(result.endingBalanceMinor, result.currencyCode)}."
    } else {
        "Imported ${result.importedTransactions} transactions from ${result.sourceFileName} into ${result.accountName}. Closing balance ${formatTransactionMoney(result.endingBalanceMinor, result.currencyCode)}."
    }
