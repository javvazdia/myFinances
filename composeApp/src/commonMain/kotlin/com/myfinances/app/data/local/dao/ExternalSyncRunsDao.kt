package com.myfinances.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.myfinances.app.data.local.entity.ExternalSyncRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExternalSyncRunsDao {
    @Query("SELECT * FROM external_sync_runs WHERE connection_id = :connectionId ORDER BY started_at_epoch_ms DESC")
    fun observeSyncRuns(connectionId: String): Flow<List<ExternalSyncRunEntity>>

    @Upsert
    suspend fun upsertSyncRun(syncRun: ExternalSyncRunEntity)

    @Query("DELETE FROM external_sync_runs WHERE connection_id = :connectionId")
    suspend fun deleteSyncRunsForConnection(connectionId: String)
}
