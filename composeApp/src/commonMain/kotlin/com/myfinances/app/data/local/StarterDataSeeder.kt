package com.myfinances.app.data.local

import com.myfinances.app.data.local.db.MyFinancesDatabase
import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.AccountSourceType
import com.myfinances.app.domain.model.AccountType
import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.CategoryKind
import com.myfinances.app.domain.model.FinanceTransaction
import com.myfinances.app.domain.model.TransactionType
import kotlin.time.Clock

class StarterDataSeeder(
    private val database: MyFinancesDatabase,
    private val ledgerRepository: LocalLedgerRepository,
) {
    suspend fun seedIfNeeded() {
        if (database.accountDao().countAccounts() > 0) return
        if (database.categoryDao().countCategories() > 0) return
        if (database.transactionDao().countTransactions() > 0) return

        val now = Clock.System.now().toEpochMilliseconds()
        val oneDay = 86_400_000L

        ledgerRepository.upsertAccount(
            Account(
                id = "account-checking",
                name = "Main Checking",
                type = AccountType.CHECKING,
                currencyCode = "EUR",
                openingBalanceMinor = 225_000,
                sourceType = AccountSourceType.MANUAL,
                sourceProvider = null,
                externalAccountId = null,
                lastSyncedAtEpochMs = null,
                isArchived = false,
                createdAtEpochMs = now - (14 * oneDay),
                updatedAtEpochMs = now,
            ),
        )
        ledgerRepository.upsertAccount(
            Account(
                id = "account-savings",
                name = "Emergency Savings",
                type = AccountType.SAVINGS,
                currencyCode = "EUR",
                openingBalanceMinor = 900_000,
                sourceType = AccountSourceType.MANUAL,
                sourceProvider = null,
                externalAccountId = null,
                lastSyncedAtEpochMs = null,
                isArchived = false,
                createdAtEpochMs = now - (30 * oneDay),
                updatedAtEpochMs = now,
            ),
        )

        val seededCategories = listOf(
            Category(
                id = "cat-income-salary",
                name = "Salary",
                kind = CategoryKind.INCOME,
                colorHex = "#1F8A5B",
                iconKey = "salary",
                isSystem = true,
                isArchived = false,
                createdAtEpochMs = now,
            ),
            Category(
                id = "cat-expense-groceries",
                name = "Groceries",
                kind = CategoryKind.EXPENSE,
                colorHex = "#44617B",
                iconKey = "groceries",
                isSystem = true,
                isArchived = false,
                createdAtEpochMs = now,
            ),
            Category(
                id = "cat-expense-utilities",
                name = "Utilities",
                kind = CategoryKind.EXPENSE,
                colorHex = "#7D8B99",
                iconKey = "utilities",
                isSystem = true,
                isArchived = false,
                createdAtEpochMs = now,
            ),
        )

        for (category in seededCategories) {
            ledgerRepository.upsertCategory(category)
        }

        val seededTransactions = listOf(
            FinanceTransaction(
                id = "txn-salary-001",
                accountId = "account-checking",
                categoryId = "cat-income-salary",
                type = TransactionType.INCOME,
                amountMinor = 395_000,
                currencyCode = "EUR",
                merchantName = "Employer",
                note = "Monthly salary",
                sourceProvider = null,
                externalTransactionId = null,
                postedAtEpochMs = now - (5 * oneDay),
                createdAtEpochMs = now - (5 * oneDay),
                updatedAtEpochMs = now - (5 * oneDay),
            ),
            FinanceTransaction(
                id = "txn-grocery-001",
                accountId = "account-checking",
                categoryId = "cat-expense-groceries",
                type = TransactionType.EXPENSE,
                amountMinor = 8_245,
                currencyCode = "EUR",
                merchantName = "Supermarket",
                note = null,
                sourceProvider = null,
                externalTransactionId = null,
                postedAtEpochMs = now - oneDay,
                createdAtEpochMs = now - oneDay,
                updatedAtEpochMs = now - oneDay,
            ),
            FinanceTransaction(
                id = "txn-utility-001",
                accountId = "account-checking",
                categoryId = "cat-expense-utilities",
                type = TransactionType.EXPENSE,
                amountMinor = 6_490,
                currencyCode = "EUR",
                merchantName = "Electric bill",
                note = null,
                sourceProvider = null,
                externalTransactionId = null,
                postedAtEpochMs = now - (2 * oneDay),
                createdAtEpochMs = now - (2 * oneDay),
                updatedAtEpochMs = now - (2 * oneDay),
            ),
        )

        for (transaction in seededTransactions) {
            ledgerRepository.upsertTransaction(transaction)
        }
    }
}
