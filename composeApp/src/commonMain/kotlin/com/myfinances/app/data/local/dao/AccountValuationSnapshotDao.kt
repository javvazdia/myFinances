package com.myfinances.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.myfinances.app.data.local.entity.AccountValuationSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountValuationSnapshotDao {
    @Query(
        """
        SELECT * FROM account_valuation_snapshots
        WHERE account_id = :accountId
        ORDER BY captured_at_epoch_ms ASC
        """,
    )
    fun observeSnapshotsForAccount(accountId: String): Flow<List<AccountValuationSnapshotEntity>>

    @Upsert
    suspend fun upsertSnapshot(snapshot: AccountValuationSnapshotEntity)
}
