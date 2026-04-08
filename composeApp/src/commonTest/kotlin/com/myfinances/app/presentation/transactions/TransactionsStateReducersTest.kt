package com.myfinances.app.presentation.transactions

import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.AccountSourceType
import com.myfinances.app.domain.model.AccountType
import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.CategoryKind
import com.myfinances.app.domain.model.FinanceTransaction
import com.myfinances.app.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionsStateReducersTest {
    @Test
    fun applyCollectionSnapshot_reconcilesSelectionsAndClearsStaleEditingDraft() {
        val account = account(id = "acc-1")
        val category = category(id = "cat-expense", kind = CategoryKind.EXPENSE)
        val transaction = transaction(id = "txn-1")
        val state = TransactionsUiState(
            selectedType = TransactionType.EXPENSE,
            selectedAccountId = "missing-account",
            selectedCategoryId = "missing-category",
            draftAmount = "18.75",
            draftMerchant = "Groceries",
            draftNote = "Old note",
            editingTransactionId = "missing-transaction",
            selectedTransactionDetailId = "txn-1",
            deleteConfirmationTransactionId = "txn-1",
            pendingDeleteTransactionId = "txn-1",
            isLoadingMoreTransactions = true,
        )

        val updated = state.applyCollectionSnapshot(
            snapshot = TransactionCollectionSnapshot(
                accounts = listOf(account),
                categories = listOf(category),
                transactions = listOf(transaction),
                currentTransactionLimit = 1,
            ),
            isStatementImportSupported = true,
        )

        assertEquals(account.id, updated.selectedAccountId)
        assertEquals(category.id, updated.selectedCategoryId)
        assertNull(updated.editingTransactionId)
        assertEquals("", updated.draftAmount)
        assertEquals("", updated.draftMerchant)
        assertEquals("", updated.draftNote)
        assertEquals("txn-1", updated.selectedTransactionDetailId)
        assertEquals("txn-1", updated.deleteConfirmationTransactionId)
        assertEquals("txn-1", updated.pendingDeleteTransactionId)
        assertFalse(updated.isLoadingMoreTransactions)
        assertTrue(updated.canLoadMoreTransactions)
        assertTrue(updated.isStatementImportSupported)
    }

    @Test
    fun showAndHideFormMode_keepCurrentSelections() {
        val state = TransactionsUiState(
            selectedType = TransactionType.INCOME,
            selectedAccountId = "acc-1",
            selectedCategoryId = "cat-income",
            draftAmount = "15.00",
            editingTransactionId = "txn-1",
        )

        val createMode = state.showCreateFormMode()
        val hiddenMode = state.hideFormMode()

        assertTrue(createMode.isFormVisible)
        assertEquals(TransactionType.INCOME, createMode.selectedType)
        assertEquals("acc-1", createMode.selectedAccountId)
        assertEquals("cat-income", createMode.selectedCategoryId)

        assertFalse(hiddenMode.isFormVisible)
        assertEquals(TransactionType.INCOME, hiddenMode.selectedType)
        assertEquals("acc-1", hiddenMode.selectedAccountId)
        assertEquals("cat-income", hiddenMode.selectedCategoryId)
        assertNull(hiddenMode.editingTransactionId)
    }

    @Test
    fun startEditing_populatesDraftsAndCompatibleCategory() {
        val expenseCategory = category(id = "cat-expense", kind = CategoryKind.EXPENSE)
        val otherCategory = category(id = "cat-income", kind = CategoryKind.INCOME)
        val transaction = transaction(
            id = "txn-1",
            categoryId = "missing-category",
            amountMinor = 12_345L,
            merchantName = "Restaurant",
            note = "Dinner",
        )
        val state = TransactionsUiState(
            categories = listOf(otherCategory, expenseCategory),
            selectedTransactionDetailId = "txn-1",
            deleteConfirmationTransactionId = "txn-1",
            pendingDeleteTransactionId = "txn-1",
            errorMessage = "Previous error",
        )

        val updated = state.startEditing(transaction)

        assertTrue(updated.isFormVisible)
        assertEquals(transaction.id, updated.editingTransactionId)
        assertEquals(TransactionType.EXPENSE, updated.selectedType)
        assertEquals("cat-expense", updated.selectedCategoryId)
        assertEquals("123.45", updated.draftAmount)
        assertEquals("Restaurant", updated.draftMerchant)
        assertEquals("Dinner", updated.draftNote)
        assertNull(updated.selectedTransactionDetailId)
        assertNull(updated.deleteConfirmationTransactionId)
        assertNull(updated.pendingDeleteTransactionId)
        assertNull(updated.errorMessage)
    }
}

private fun account(id: String): Account = Account(
    id = id,
    name = "Checking",
    type = AccountType.CHECKING,
    currencyCode = "EUR",
    openingBalanceMinor = 0L,
    sourceType = AccountSourceType.MANUAL,
    sourceProvider = null,
    externalAccountId = null,
    lastSyncedAtEpochMs = null,
    isArchived = false,
    createdAtEpochMs = 0L,
    updatedAtEpochMs = 0L,
)

private fun category(id: String, kind: CategoryKind): Category = Category(
    id = id,
    name = id,
    kind = kind,
    colorHex = "#FFFFFF",
    iconKey = "tag",
    isSystem = false,
    isArchived = false,
    createdAtEpochMs = 0L,
)

private fun transaction(
    id: String,
    categoryId: String? = "cat-expense",
    amountMinor: Long = 18_75L,
    merchantName: String? = "Groceries",
    note: String? = null,
): FinanceTransaction = FinanceTransaction(
    id = id,
    accountId = "acc-1",
    categoryId = categoryId,
    type = TransactionType.EXPENSE,
    amountMinor = amountMinor,
    currencyCode = "EUR",
    merchantName = merchantName,
    note = note,
    sourceProvider = null,
    externalTransactionId = null,
    postedAtEpochMs = 1_000L,
    createdAtEpochMs = 1_000L,
    updatedAtEpochMs = 1_000L,
)
