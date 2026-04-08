package com.myfinances.app.presentation.transactions

import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.TransactionType

internal const val INITIAL_TRANSACTION_PAGE_SIZE = 30

data class TransactionsUiState(
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val transactions: List<com.myfinances.app.domain.model.FinanceTransaction> = emptyList(),
    val recentTransactions: List<TransactionCardUiModel> = emptyList(),
    val transactionLimit: Int = INITIAL_TRANSACTION_PAGE_SIZE,
    val canLoadMoreTransactions: Boolean = false,
    val isLoadingMoreTransactions: Boolean = false,
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

internal fun TransactionsUiState.toCreateMode(): TransactionsUiState =
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
