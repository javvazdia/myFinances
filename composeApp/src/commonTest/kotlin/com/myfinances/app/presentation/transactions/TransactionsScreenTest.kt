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
}
