package com.myfinances.app.data.local

import com.myfinances.app.data.local.db.MyFinancesDatabase
import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.CategoryKind
import com.myfinances.app.domain.model.FinanceTransaction
import com.myfinances.app.domain.repository.LedgerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalLedgerRepository(
    private val database: MyFinancesDatabase,
) : LedgerRepository {
    override fun observeAccounts(includeArchived: Boolean): Flow<List<Account>> {
        val source = if (includeArchived) {
            database.accountDao().observeAllAccounts()
        } else {
            database.accountDao().observeActiveAccounts()
        }

        return source.map { accounts -> accounts.map(AccountEntityMapper::toDomain) }
    }

    override fun observeCategories(): Flow<List<Category>> =
        database.categoryDao()
            .observeActiveCategories()
            .map { categories -> categories.map(CategoryEntityMapper::toDomain) }

    override fun observeCategories(kind: CategoryKind): Flow<List<Category>> =
        database.categoryDao()
            .observeActiveCategoriesByKind(kind)
            .map { categories -> categories.map(CategoryEntityMapper::toDomain) }

    override fun observeRecentTransactions(limit: Int): Flow<List<FinanceTransaction>> =
        database.transactionDao()
            .observeRecentTransactions(limit)
            .map { transactions -> transactions.map(TransactionEntityMapper::toDomain) }

    override fun observeAllTransactions(): Flow<List<FinanceTransaction>> =
        database.transactionDao()
            .observeAllTransactions()
            .map { transactions -> transactions.map(TransactionEntityMapper::toDomain) }

    override fun observeTransactionsForAccount(accountId: String): Flow<List<FinanceTransaction>> =
        database.transactionDao()
            .observeTransactionsForAccount(accountId)
            .map { transactions -> transactions.map(TransactionEntityMapper::toDomain) }

    override suspend fun upsertAccount(account: Account) {
        database.accountDao().upsertAccount(AccountEntityMapper.toEntity(account))
    }

    override suspend fun upsertCategory(category: Category) {
        database.categoryDao().upsertCategory(CategoryEntityMapper.toEntity(category))
    }

    override suspend fun upsertTransaction(transaction: FinanceTransaction) {
        database.transactionDao().upsertTransaction(TransactionEntityMapper.toEntity(transaction))
    }

    override suspend fun deleteCategory(categoryId: String) {
        database.categoryDao().getCategoryById(categoryId)?.let { category ->
            database.categoryDao().deleteCategory(category)
        }
    }

    override suspend fun deleteTransaction(transactionId: String) {
        database.transactionDao().deleteTransactionById(transactionId)
    }
}
