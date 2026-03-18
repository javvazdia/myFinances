package com.myfinances.app.integrations.indexa.sync

import com.myfinances.app.data.integration.InMemoryConnectionSecretStore
import com.myfinances.app.data.integration.InMemoryExternalConnectionsRepository
import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.model.integration.ExternalSyncStatus
import com.myfinances.app.integrations.indexa.api.StubIndexaApiClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class StubIndexaIntegrationServiceTest {
    @Test
    fun connectCreatesConnectionAndStoresSecret() = runBlocking {
        val connectionsRepository = InMemoryExternalConnectionsRepository()
        val secretStore = InMemoryConnectionSecretStore()
        val service = StubIndexaIntegrationService(
            apiClient = StubIndexaApiClient(),
            externalConnectionsRepository = connectionsRepository,
            connectionSecretStore = secretStore,
        )

        val connection = service.connect("demo-token")

        val savedSecret = secretStore.readSecret(
            providerId = ExternalProviderId.INDEXA,
            connectionId = connection.id,
        )
        val savedConnections = connectionsRepository.observeConnections().first()
        val accountLinks = connectionsRepository.observeAccountLinks(connection.id).first()

        assertEquals(ExternalProviderId.INDEXA, connection.providerId)
        assertEquals(ExternalSyncStatus.IDLE, connection.lastSyncStatus)
        assertEquals(1, savedConnections.size)
        assertEquals(1, accountLinks.size)
        assertNotNull(savedSecret)
        assertEquals("demo-token", savedSecret)
    }
}
