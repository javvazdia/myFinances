package com.myfinances.app.data.integration

import com.myfinances.app.data.local.ExternalAccountLinkEntityMapper
import com.myfinances.app.data.local.ExternalConnectionEntityMapper
import com.myfinances.app.data.local.ExternalSyncRunEntityMapper
import com.myfinances.app.data.local.db.MyFinancesDatabase
import com.myfinances.app.domain.model.integration.ExternalAccountLink
import com.myfinances.app.domain.model.integration.ExternalConnection
import com.myfinances.app.domain.model.integration.ExternalSyncRun
import com.myfinances.app.domain.repository.ExternalConnectionsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomExternalConnectionsRepository(
    private val database: MyFinancesDatabase,
) : ExternalConnectionsRepository {
    override fun observeConnections(): Flow<List<ExternalConnection>> =
        database.externalConnectionsDao()
            .observeConnections()
            .map { entities -> entities.map(ExternalConnectionEntityMapper::toDomain) }

    override fun observeAccountLinks(connectionId: String): Flow<List<ExternalAccountLink>> =
        database.externalAccountLinksDao()
            .observeAccountLinks(connectionId)
            .map { entities -> entities.map(ExternalAccountLinkEntityMapper::toDomain) }

    override fun observeSyncRuns(connectionId: String): Flow<List<ExternalSyncRun>> =
        database.externalSyncRunsDao()
            .observeSyncRuns(connectionId)
            .map { entities -> entities.map(ExternalSyncRunEntityMapper::toDomain) }

    override suspend fun upsertConnection(connection: ExternalConnection) {
        database.externalConnectionsDao()
            .upsertConnection(ExternalConnectionEntityMapper.toEntity(connection))
    }

    override suspend fun replaceAccountLinks(connectionId: String, links: List<ExternalAccountLink>) {
        database.externalAccountLinksDao().deleteAccountLinksForConnection(connectionId)
        if (links.isNotEmpty()) {
            database.externalAccountLinksDao()
                .upsertAccountLinks(links.map(ExternalAccountLinkEntityMapper::toEntity))
        }
    }

    override suspend fun recordSyncRun(syncRun: ExternalSyncRun) {
        database.externalSyncRunsDao()
            .upsertSyncRun(ExternalSyncRunEntityMapper.toEntity(syncRun))
    }

    override suspend fun deleteConnection(connectionId: String) {
        database.externalConnectionsDao().deleteConnectionById(connectionId)
    }
}
