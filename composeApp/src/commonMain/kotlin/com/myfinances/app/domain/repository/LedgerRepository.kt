package com.myfinances.app.domain.repository

import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.CategoryKind
import com.myfinances.app.domain.model.FinanceTransaction
import com.myfinances.app.domain.model.InvestmentPosition
import kotlinx.coroutines.flow.Flow

interface LedgerRepository {
    fun observeAccounts(includeArchived: Boolean = false): Flow<List<Account>>

    fun observeInvestmentPositions(accountId: String): Flow<List<InvestmentPosition>>

    fun observeCategories(): Flow<List<Category>>

    fun observeCategories(kind: CategoryKind): Flow<List<Category>>

    fun observeRecentTransactions(limit: Int = 50): Flow<List<FinanceTransaction>>

    fun observeAllTransactions(): Flow<List<FinanceTransaction>>

    fun observeTransactionsForAccount(accountId: String): Flow<List<FinanceTransaction>>

    suspend fun upsertAccount(account: Account)

    suspend fun replaceInvestmentPositions(
        accountId: String,
        positions: List<InvestmentPosition>,
    )

    suspend fun upsertCategory(category: Category)

    suspend fun upsertTransaction(transaction: FinanceTransaction)

    suspend fun deleteAccount(accountId: String)

    suspend fun deleteCategory(categoryId: String)

    suspend fun deleteTransaction(transactionId: String)
}
