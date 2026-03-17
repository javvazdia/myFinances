package com.myfinances.app.domain.repository

import com.myfinances.app.domain.model.integration.ExternalAccountLink
import com.myfinances.app.domain.model.integration.ExternalConnection
import com.myfinances.app.domain.model.integration.ExternalSyncRun
import kotlinx.coroutines.flow.Flow

interface ExternalConnectionsRepository {
    fun observeConnections(): Flow<List<ExternalConnection>>

    fun observeAccountLinks(connectionId: String): Flow<List<ExternalAccountLink>>

    fun observeSyncRuns(connectionId: String): Flow<List<ExternalSyncRun>>

    suspend fun upsertConnection(connection: ExternalConnection)

    suspend fun replaceAccountLinks(
        connectionId: String,
        links: List<ExternalAccountLink>,
    )

    suspend fun recordSyncRun(syncRun: ExternalSyncRun)

    suspend fun deleteConnection(connectionId: String)
}
