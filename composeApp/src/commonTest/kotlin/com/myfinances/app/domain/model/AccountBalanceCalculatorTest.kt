package com.myfinances.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AccountBalanceCalculatorTest {
    @Test
    fun addsManualAccountTransactionsToOpeningBalance() {
        val account = Account(
            id = "account-1",
            name = "Checking",
            type = AccountType.CHECKING,
            currencyCode = "EUR",
            openingBalanceMinor = 1_000_00,
            sourceType = AccountSourceType.MANUAL,
            sourceProvider = null,
            externalAccountId = null,
            lastSyncedAtEpochMs = null,
            isArchived = false,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )
        val transactions = listOf(
            FinanceTransaction(
                id = "txn-income",
                accountId = account.id,
                categoryId = null,
                type = TransactionType.INCOME,
                amountMinor = 200_00,
                currencyCode = "EUR",
                merchantName = null,
                note = null,
                sourceProvider = null,
                externalTransactionId = null,
                postedAtEpochMs = 1L,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
            ),
            FinanceTransaction(
                id = "txn-expense",
                accountId = account.id,
                categoryId = null,
                type = TransactionType.EXPENSE,
                amountMinor = 50_00,
                currencyCode = "EUR",
                merchantName = null,
                note = null,
                sourceProvider = null,
                externalTransactionId = null,
                postedAtEpochMs = 1L,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
            ),
        )

        assertEquals(1_150_00, calculateAccountCurrentBalance(account, transactions))
    }

    @Test
    fun keepsSyncedInvestmentBalanceAsCurrentSnapshot() {
        val account = Account(
            id = "account-indexa-1",
            name = "Indexa profile",
            type = AccountType.INVESTMENT,
            currencyCode = "EUR",
            openingBalanceMinor = 12_345_67,
            sourceType = AccountSourceType.API_SYNC,
            sourceProvider = "Indexa",
            externalAccountId = "ACC-1",
            lastSyncedAtEpochMs = 1L,
            isArchived = false,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )
        val transactions = listOf(
            FinanceTransaction(
                id = "txn-imported-fee",
                accountId = account.id,
                categoryId = null,
                type = TransactionType.EXPENSE,
                amountMinor = 25_00,
                currencyCode = "EUR",
                merchantName = null,
                note = null,
                sourceProvider = "Indexa",
                externalTransactionId = "ext-1",
                postedAtEpochMs = 1L,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
            ),
        )

        assertEquals(12_345_67, calculateAccountCurrentBalance(account, transactions))
    }
}
