package com.myfinances.app.presentation.transactions

import com.myfinances.app.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
                    isExpense = true,
                ),
            ),
            deleteConfirmationTransactionId = "txn-1",
        )

        assertEquals("Groceries", uiState.deleteConfirmationTransactionTitle)
    }
}
