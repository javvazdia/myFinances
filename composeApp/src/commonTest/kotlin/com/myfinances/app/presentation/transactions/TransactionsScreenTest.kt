package com.myfinances.app.presentation.transactions

import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.CategoryKind
import com.myfinances.app.domain.model.FinanceTransaction
import com.myfinances.app.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionsScreenTest {
    @Test
    fun parsesTransactionAmountIntoMinorUnits() {
        assertEquals(18_75L, parseTransactionAmountToMinor("18.75"))
        assertEquals(120_00L, parseTransactionAmountToMinor("120"))
        assertEquals(9_50L, parseTransactionAmountToMinor("9,5"))
        assertNull(parseTransactionAmountToMinor(""))
        assertNull(parseTransactionAmountToMinor("-10"))
        assertNull(parseTransactionAmountToMinor("10.999"))
    }

    @Test
    fun generatesStableTransactionPrefixes() {
        val transactionId = generateTransactionId(TransactionType.EXPENSE, 1234L)
        assertEquals(true, transactionId.startsWith("txn-expense-1234-"))
    }

    @Test
    fun formatsTransactionAmountForEditingInput() {
        assertEquals("18.75", formatTransactionAmountInput(18_75L))
        assertEquals("120.00", formatTransactionAmountInput(120_00L))
        assertEquals("9.05", formatTransactionAmountInput(9_05L))
    }

    @Test
    fun formatsTransactionDateFromPostedTimestamp() {
        val marchTenth2026 = 1_773_100_800_000L
        val marchEighteenth2026 = 1_773_792_000_000L

        assertEquals("Mar 10", formatTransactionDateLabel(marchTenth2026, nowEpochMs = marchEighteenth2026))
        assertEquals("Today", formatTransactionDateLabel(marchEighteenth2026, nowEpochMs = marchEighteenth2026))
    }

    @Test
    fun exposesDeleteConfirmationTitleFromSelectedTransaction() {
        val uiState = TransactionsUiState(
            recentTransactions = listOf(
                TransactionCardUiModel(
                    id = "txn-1",
                    title = "Groceries",
                    accountName = "Checking",
                    categoryName = "Food",
                    amountLabel = "-18.75 EUR",
                    dateLabel = "Today",
                    sourceLabel = null,
                    metadataPreview = null,
                    detailRows = emptyList(),
                    isProviderManaged = false,
                    isExpense = true,
                ),
            ),
            deleteConfirmationTransactionId = "txn-1",
        )

        assertEquals("Groceries", uiState.deleteConfirmationTransactionTitle)
    }

    @Test
    fun flagsProviderManagedTransactionsAndBuildsDetailRows() {
        val account = Account(
            id = "acc-1",
            name = "Indexa mutual account",
            type = com.myfinances.app.domain.model.AccountType.INVESTMENT,
            currencyCode = "EUR",
            openingBalanceMinor = 0L,
            sourceType = com.myfinances.app.domain.model.AccountSourceType.API_SYNC,
            sourceProvider = "Indexa Capital",
            externalAccountId = "WVKMTVDZ",
            lastSyncedAtEpochMs = 0L,
            isArchived = false,
            createdAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
        )
        val category = Category(
            id = "cat-1",
            name = "Investment purchase",
            kind = CategoryKind.TRANSFER,
            colorHex = "#000000",
            iconKey = "investment",
            isSystem = true,
            isArchived = false,
            createdAtEpochMs = 0L,
        )
        val transaction = FinanceTransaction(
            id = "txn-1",
            accountId = account.id,
            categoryId = category.id,
            type = TransactionType.TRANSFER,
            amountMinor = 12_345L,
            currencyCode = "EUR",
            merchantName = "Vanguard US 500",
            note = "Operation: Purchase | Units: 1.23 | Reference: abc-123",
            sourceProvider = "Indexa Capital",
            externalTransactionId = "abc-123",
            postedAtEpochMs = 1_773_100_800_000L,
            createdAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
        )

        val card = transaction.toCardUiModel(
            accounts = listOf(account),
            categories = listOf(category),
        )

        assertTrue(card.isProviderManaged)
        assertEquals("Synced from Indexa Capital", card.sourceLabel)
        assertEquals("Operation: Purchase | Units: 1.23 | Reference: abc-123", card.metadataPreview)
        assertTrue(card.detailRows.any { row -> row.label == "Reference" && row.value == "abc-123" })
    }

    @Test
    fun leavesManualTransactionsEditable() {
        val transaction = FinanceTransaction(
            id = "txn-1",
            accountId = "acc-1",
            categoryId = null,
            type = TransactionType.INCOME,
            amountMinor = 5_000L,
            currencyCode = "EUR",
            merchantName = "Salary",
            note = null,
            sourceProvider = null,
            externalTransactionId = null,
            postedAtEpochMs = 1_773_100_800_000L,
            createdAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
        )

        assertFalse(transaction.isProviderManaged())
    }
}
