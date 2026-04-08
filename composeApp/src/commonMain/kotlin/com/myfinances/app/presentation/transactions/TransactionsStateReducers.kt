package com.myfinances.app.presentation.transactions

import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.FinanceTransaction

internal data class TransactionCollectionSnapshot(
    val accounts: List<Account>,
    val categories: List<Category>,
    val transactions: List<FinanceTransaction>,
    val currentTransactionLimit: Int,
)

internal fun TransactionsUiState.applyCollectionSnapshot(
    snapshot: TransactionCollectionSnapshot,
    isStatementImportSupported: Boolean,
): TransactionsUiState {
    val nextEditingTransactionId = editingTransactionId
        ?.takeIf { editingId ->
            snapshot.transactions.any { transaction -> transaction.id == editingId }
        }

    val nextSelectedAccountId = selectedAccountId
        ?.takeIf { selectedId -> snapshot.accounts.any { account -> account.id == selectedId } }
        ?: snapshot.accounts.firstOrNull()?.id

    val availableCategories = snapshot.categories.filter { category ->
        category.kind == selectedType.categoryKind
    }
    val nextSelectedCategoryId = selectedCategoryId
        ?.takeIf { selectedId ->
            availableCategories.any { category -> category.id == selectedId }
        }
        ?: availableCategories.firstOrNull()?.id

    val editWasCleared = isEditing && nextEditingTransactionId == null

    return copy(
        accounts = snapshot.accounts,
        categories = snapshot.categories,
        transactions = snapshot.transactions,
        recentTransactions = snapshot.transactions.map { transaction ->
            transaction.toCardUiModel(snapshot.accounts, snapshot.categories)
        },
        transactionLimit = snapshot.currentTransactionLimit,
        canLoadMoreTransactions = snapshot.transactions.size >= snapshot.currentTransactionLimit,
        isLoadingMoreTransactions = false,
        isStatementImportSupported = isStatementImportSupported,
        selectedAccountId = nextSelectedAccountId,
        selectedCategoryId = nextSelectedCategoryId,
        editingTransactionId = nextEditingTransactionId,
        selectedTransactionDetailId = selectedTransactionDetailId
            ?.takeIf { detailId ->
                snapshot.transactions.any { transaction -> transaction.id == detailId }
            },
        deleteConfirmationTransactionId = deleteConfirmationTransactionId
            ?.takeIf { confirmationId ->
                snapshot.transactions.any { transaction -> transaction.id == confirmationId }
            },
        pendingDeleteTransactionId = pendingDeleteTransactionId
            ?.takeIf { deletingId ->
                snapshot.transactions.any { transaction -> transaction.id == deletingId }
            },
        draftAmount = if (editWasCleared) "" else draftAmount,
        draftMerchant = if (editWasCleared) "" else draftMerchant,
        draftNote = if (editWasCleared) "" else draftNote,
    )
}

internal fun TransactionsUiState.showCreateFormMode(): TransactionsUiState =
    toCreateMode().copy(
        isFormVisible = true,
        selectedType = selectedType,
        selectedAccountId = selectedAccountId,
        selectedCategoryId = selectedCategoryId,
    )

internal fun TransactionsUiState.hideFormMode(): TransactionsUiState =
    toCreateMode().copy(
        selectedType = selectedType,
        selectedAccountId = selectedAccountId,
        selectedCategoryId = selectedCategoryId,
    )

internal fun TransactionsUiState.startEditing(transaction: FinanceTransaction): TransactionsUiState {
    val availableCategories = categories.filter { category ->
        category.kind == transaction.type.categoryKind
    }

    return copy(
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
