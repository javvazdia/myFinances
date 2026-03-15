package com.myfinances.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.myfinances.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY posted_at_epoch_ms DESC")
    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY posted_at_epoch_ms DESC LIMIT :limit")
    fun observeRecentTransactions(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE account_id = :accountId ORDER BY posted_at_epoch_ms DESC")
    fun observeTransactionsForAccount(accountId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1")
    suspend fun getTransactionById(transactionId: String): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun countTransactions(): Int

    @Upsert
    suspend fun upsertTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteTransactionById(transactionId: String)
}
