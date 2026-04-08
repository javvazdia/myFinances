package com.myfinances.app.presentation.transactions

import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.FinanceTransaction
import kotlin.time.Clock

internal data class TransactionDraftValidation(
    val account: Account,
    val categoryId: String,
    val amountMinor: Long,
    val existingTransaction: FinanceTransaction?,
)

internal fun TransactionsUiState.validateDraft(): Result<TransactionDraftValidation> {
    val selectedAccount = accounts.firstOrNull { it.id == selectedAccountId }
    val selectedCategoryId = selectedCategoryId
    val amountMinor = parseTransactionAmountToMinor(draftAmount)
    val existingTransaction = editingTransactionId?.let { editingId ->
        transactions.firstOrNull { transaction -> transaction.id == editingId }
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

    return if (error != null) {
        Result.failure(IllegalArgumentException(error))
    } else {
        Result.success(
            TransactionDraftValidation(
                account = selectedAccount ?: error("validated above"),
                categoryId = selectedCategoryId ?: error("validated above"),
                amountMinor = amountMinor ?: error("validated above"),
                existingTransaction = existingTransaction,
            ),
        )
    }
}

internal fun TransactionsUiState.buildTransaction(
    validation: TransactionDraftValidation,
    nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
): FinanceTransaction {
    val existingTransaction = validation.existingTransaction

    return FinanceTransaction(
        id = existingTransaction?.id ?: generateTransactionId(selectedType, nowEpochMs),
        accountId = validation.account.id,
        categoryId = validation.categoryId,
        type = selectedType,
        amountMinor = validation.amountMinor,
        currencyCode = validation.account.currencyCode,
        merchantName = draftMerchant.trim().ifBlank { null },
        note = draftNote.trim().ifBlank { null },
        sourceProvider = existingTransaction?.sourceProvider,
        externalTransactionId = existingTransaction?.externalTransactionId,
        postedAtEpochMs = existingTransaction?.postedAtEpochMs ?: nowEpochMs,
        createdAtEpochMs = existingTransaction?.createdAtEpochMs ?: nowEpochMs,
        updatedAtEpochMs = nowEpochMs,
    )
}
