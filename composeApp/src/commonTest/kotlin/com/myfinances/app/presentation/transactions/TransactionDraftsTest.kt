package com.myfinances.app.presentation.transactions

import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.AccountSourceType
import com.myfinances.app.domain.model.AccountType
import com.myfinances.app.domain.model.FinanceTransaction
import com.myfinances.app.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionDraftsTest {
    @Test
    fun validateDraft_returnsSuccessForManualTransactionDraft() {
        val account = testAccount()
        val state = TransactionsUiState(
            accounts = listOf(account),
            selectedAccountId = account.id,
            selectedCategoryId = "cat-1",
            draftAmount = "18.75",
            draftMerchant = "Groceries",
            draftNote = "Weekly shop",
        )

        val result = state.validateDraft()

        assertTrue(result.isSuccess)
        val validation = result.getOrNull()
        assertNotNull(validation)
        assertEquals(account.id, validation.account.id)
        assertEquals("cat-1", validation.categoryId)
        assertEquals(18_75L, validation.amountMinor)
        assertNull(validation.existingTransaction)
    }

    @Test
    fun validateDraft_rejectsProviderManagedEdit() {
        val account = testAccount()
        val existingTransaction = FinanceTransaction(
            id = "txn-1",
            accountId = account.id,
            categoryId = "cat-1",
            type = TransactionType.EXPENSE,
            amountMinor = 10_00L,
            currencyCode = "EUR",
            merchantName = "Provider entry",
            note = null,
            sourceProvider = "Indexa Capital",
            externalTransactionId = "provider-1",
            postedAtEpochMs = 1_000L,
            createdAtEpochMs = 1_000L,
            updatedAtEpochMs = 1_000L,
        )
        val state = TransactionsUiState(
            accounts = listOf(account),
            transactions = listOf(existingTransaction),
            selectedAccountId = account.id,
            selectedCategoryId = "cat-1",
            draftAmount = "10.00",
            editingTransactionId = existingTransaction.id,
        )

        val result = state.validateDraft()

        assertTrue(result.isFailure)
        assertEquals(
            "Synced provider transactions are read-only right now.",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun buildTransaction_preservesExistingMetadataAndTrimsBlankFields() {
        val account = testAccount()
        val existingTransaction = FinanceTransaction(
            id = "txn-1",
            accountId = account.id,
            categoryId = "cat-old",
            type = TransactionType.EXPENSE,
            amountMinor = 10_00L,
            currencyCode = "EUR",
            merchantName = "Old merchant",
            note = "Old note",
            sourceProvider = "Caja PDF",
            externalTransactionId = "import-1",
            postedAtEpochMs = 2_000L,
            createdAtEpochMs = 1_000L,
            updatedAtEpochMs = 2_000L,
        )
        val state = TransactionsUiState(
            selectedType = TransactionType.INCOME,
            draftMerchant = "   ",
            draftNote = "   ",
        )
        val validation = TransactionDraftValidation(
            account = account,
            categoryId = "cat-1",
            amountMinor = 25_00L,
            existingTransaction = existingTransaction,
        )

        val transaction = state.buildTransaction(
            validation = validation,
            nowEpochMs = 9_999L,
        )

        assertEquals(existingTransaction.id, transaction.id)
        assertEquals(account.id, transaction.accountId)
        assertEquals("cat-1", transaction.categoryId)
        assertEquals(TransactionType.INCOME, transaction.type)
        assertEquals(25_00L, transaction.amountMinor)
        assertNull(transaction.merchantName)
        assertNull(transaction.note)
        assertEquals("Caja PDF", transaction.sourceProvider)
        assertEquals("import-1", transaction.externalTransactionId)
        assertEquals(2_000L, transaction.postedAtEpochMs)
        assertEquals(1_000L, transaction.createdAtEpochMs)
        assertEquals(9_999L, transaction.updatedAtEpochMs)
    }
}

private fun testAccount(): Account = Account(
    id = "acc-1",
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
