package com.myfinances.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.myfinances.app.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE is_archived = 0 ORDER BY name ASC")
    fun observeActiveAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY is_archived ASC, name ASC")
    fun observeAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :accountId LIMIT 1")
    suspend fun getAccountById(accountId: String): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun countAccounts(): Int

    @Upsert
    suspend fun upsertAccount(account: AccountEntity)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)
}
