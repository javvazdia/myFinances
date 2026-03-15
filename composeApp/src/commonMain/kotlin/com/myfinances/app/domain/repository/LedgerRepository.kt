package com.myfinances.app.domain.repository

import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.CategoryKind
import com.myfinances.app.domain.model.FinanceTransaction
import kotlinx.coroutines.flow.Flow

interface LedgerRepository {
    fun observeAccounts(includeArchived: Boolean = false): Flow<List<Account>>

    fun observeCategories(): Flow<List<Category>>

    fun observeCategories(kind: CategoryKind): Flow<List<Category>>

    fun observeRecentTransactions(limit: Int = 50): Flow<List<FinanceTransaction>>

    fun observeAllTransactions(): Flow<List<FinanceTransaction>>

    fun observeTransactionsForAccount(accountId: String): Flow<List<FinanceTransaction>>

    suspend fun upsertAccount(account: Account)

    suspend fun upsertCategory(category: Category)

    suspend fun upsertTransaction(transaction: FinanceTransaction)

    suspend fun deleteTransaction(transactionId: String)
}
