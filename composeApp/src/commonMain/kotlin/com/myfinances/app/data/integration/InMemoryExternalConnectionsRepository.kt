package com.myfinances.app.data.integration

import com.myfinances.app.domain.model.integration.ExternalAccountLink
import com.myfinances.app.domain.model.integration.ExternalConnection
import com.myfinances.app.domain.model.integration.ExternalSyncRun
import com.myfinances.app.domain.repository.ExternalConnectionsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class InMemoryExternalConnectionsRepository : ExternalConnectionsRepository {
    private val connections = MutableStateFlow<List<ExternalConnection>>(emptyList())
    private val accountLinks = MutableStateFlow<List<ExternalAccountLink>>(emptyList())
    private val syncRuns = MutableStateFlow<List<ExternalSyncRun>>(emptyList())

    override fun observeConnections(): Flow<List<ExternalConnection>> = connections

    override fun observeAccountLinks(connectionId: String): Flow<List<ExternalAccountLink>> =
        accountLinks.map { links ->
            links.filter { link -> link.connectionId == connectionId }
        }

    override fun observeSyncRuns(connectionId: String): Flow<List<ExternalSyncRun>> =
        syncRuns.map { runs ->
            runs.filter { run -> run.connectionId == connectionId }
        }

    override suspend fun upsertConnection(connection: ExternalConnection) {
        connections.update { current ->
            current
                .filterNot { item -> item.id == connection.id }
                .plus(connection)
                .sortedBy(ExternalConnection::displayName)
        }
    }

    override suspend fun replaceAccountLinks(connectionId: String, links: List<ExternalAccountLink>) {
        accountLinks.update { current ->
            current.filterNot { link -> link.connectionId == connectionId } + links
        }
    }

    override suspend fun recordSyncRun(syncRun: ExternalSyncRun) {
        syncRuns.update { current ->
            listOf(syncRun) + current.filterNot { run -> run.id == syncRun.id }
        }
    }

    override suspend fun deleteConnection(connectionId: String) {
        connections.update { current ->
            current.filterNot { connection -> connection.id == connectionId }
        }
        accountLinks.update { current ->
            current.filterNot { link -> link.connectionId == connectionId }
        }
        syncRuns.update { current ->
            current.filterNot { run -> run.connectionId == connectionId }
        }
    }
}
