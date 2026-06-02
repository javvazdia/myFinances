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
    fun usesLatestSnapshotAsCurrentValueForManualInvestmentAccounts() {
        val account = Account(
            id = "account-helvetia-1",
            name = "Helvetia",
            type = AccountType.INVESTMENT,
            currencyCode = "EUR",
            openingBalanceMinor = 5_000_00,
            sourceType = AccountSourceType.MANUAL,
            sourceProvider = null,
            externalAccountId = null,
            lastSyncedAtEpochMs = null,
            isArchived = false,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )

        val balance = calculateAccountCurrentBalance(
            account = account,
            transactions = emptyList(),
            latestSnapshot = AccountValuationSnapshot(
                id = "snapshot-1",
                accountId = account.id,
                sourceProvider = "Manual snapshot",
                currencyCode = "EUR",
                valueMinor = 8_250_00,
                valuationDate = "2026-05-10",
                capturedAtEpochMs = 2L,
            ),
        )

        assertEquals(8_250_00, balance)
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

        val balance = calculateAccountCurrentBalance(
            account = account,
            transactions = transactions,
            latestSnapshot = AccountValuationSnapshot(
                id = "snapshot-1",
                accountId = account.id,
                sourceProvider = "Indexa",
                currencyCode = "EUR",
                valueMinor = 12_500_00,
                valuationDate = "2026-05-10",
                capturedAtEpochMs = 2L,
            ),
        )

        assertEquals(12_500_00, balance)
    }

    @Test
    fun usesLatestSnapshotAsCurrentValueForFileImportedInvestmentAccounts() {
        val account = Account(
            id = "account-degiro-1",
            name = "DEGIRO Portfolio",
            type = AccountType.INVESTMENT,
            currencyCode = "EUR",
            openingBalanceMinor = 0L,
            sourceType = AccountSourceType.FILE_IMPORT,
            sourceProvider = "DEGIRO Portfolio CSV",
            externalAccountId = "degiro-portfolio",
            lastSyncedAtEpochMs = 1L,
            isArchived = false,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )

        val balance = calculateAccountCurrentBalance(
            account = account,
            transactions = emptyList(),
            latestSnapshot = AccountValuationSnapshot(
                id = "snapshot-1",
                accountId = account.id,
                sourceProvider = "DEGIRO Portfolio CSV",
                currencyCode = "EUR",
                valueMinor = 150_59L,
                valuationDate = "2026-06-02",
                capturedAtEpochMs = 2L,
            ),
        )

        assertEquals(150_59L, balance)
    }

    @Test
    fun choosesCurrentInvestmentSnapshotByValuationDate() {
        val account = Account(
            id = "account-degiro-1",
            name = "DEGIRO Portfolio",
            type = AccountType.INVESTMENT,
            currencyCode = "EUR",
            openingBalanceMinor = 0L,
            sourceType = AccountSourceType.FILE_IMPORT,
            sourceProvider = "DEGIRO Portfolio CSV",
            externalAccountId = "degiro-portfolio",
            lastSyncedAtEpochMs = 1L,
            isArchived = false,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )

        val balances = calculateAccountCurrentBalances(
            accounts = listOf(account),
            transactions = emptyList(),
            snapshots = listOf(
                AccountValuationSnapshot(
                    id = "snapshot-new-capture-old-valuation",
                    accountId = account.id,
                    sourceProvider = "DEGIRO Portfolio CSV",
                    currencyCode = "EUR",
                    valueMinor = 100_00L,
                    valuationDate = "2026-05-01",
                    capturedAtEpochMs = 3L,
                ),
                AccountValuationSnapshot(
                    id = "snapshot-old-capture-new-valuation",
                    accountId = account.id,
                    sourceProvider = "DEGIRO Portfolio CSV",
                    currencyCode = "EUR",
                    valueMinor = 200_00L,
                    valuationDate = "2026-06-01",
                    capturedAtEpochMs = 2L,
                ),
            ),
        )

        assertEquals(200_00L, balances.getValue(account.id))
    }
}
