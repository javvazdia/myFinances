package com.myfinances.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.myfinances.app.data.local.entity.InvestmentPositionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InvestmentPositionDao {
    @Query("SELECT * FROM investment_positions WHERE account_id = :accountId ORDER BY instrument_name ASC")
    fun observePositionsForAccount(accountId: String): Flow<List<InvestmentPositionEntity>>

    @Upsert
    suspend fun upsertPositions(positions: List<InvestmentPositionEntity>)

    @Query("DELETE FROM investment_positions WHERE account_id = :accountId")
    suspend fun deletePositionsForAccount(accountId: String)
}
