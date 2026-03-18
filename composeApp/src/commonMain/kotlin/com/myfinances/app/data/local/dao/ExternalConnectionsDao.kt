package com.myfinances.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.myfinances.app.data.local.entity.ExternalConnectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExternalConnectionsDao {
    @Query("SELECT * FROM external_connections ORDER BY display_name ASC")
    fun observeConnections(): Flow<List<ExternalConnectionEntity>>

    @Upsert
    suspend fun upsertConnection(connection: ExternalConnectionEntity)

    @Query("DELETE FROM external_connections WHERE id = :connectionId")
    suspend fun deleteConnectionById(connectionId: String)
}
