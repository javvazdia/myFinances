package com.myfinances.app.integrations.cajaingenieros.sync

import com.myfinances.app.data.integration.InMemoryConnectionSecretStore
import com.myfinances.app.data.integration.InMemoryExternalConnectionsRepository
import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.model.integration.ExternalConnectionStatus
import com.myfinances.app.domain.model.integration.ExternalSyncStatus
import com.myfinances.app.integrations.cajaingenieros.api.StubCajaIngenierosApiClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScaffoldedCajaIngenierosIntegrationServiceTest {
    private val connectionsRepository = InMemoryExternalConnectionsRepository()
    private val secretStore = InMemoryConnectionSecretStore()
    private val service = ScaffoldedCajaIngenierosIntegrationService(
        apiClient = StubCajaIngenierosApiClient(),
        externalConnectionsRepository = connectionsRepository,
        connectionSecretStore = secretStore,
    )

    private val validCredentials = mapOf(
        "environment" to "sandbox",
        "consumerKey" to "sandbox-key",
        "consumerSecret" to "sandbox-secret",
        "appId" to "sandbox-app",
    )

    @Test
    fun exposesScaffoldedPreviewMetadata() = runTest {
        val preview = service.testConnection(validCredentials)

        assertEquals("Caja Ingenieros (Sandbox)", preview.suggestedConnectionName)
        assertTrue(preview.ownerLabel?.contains("Sandbox", ignoreCase = true) == true)
        assertTrue(preview.discoveredAccounts.isEmpty())
    }

    @Test
    fun connectStoresStructuredCredentialsForLaterOauthWork() = runTest {
        val connection = service.connect(validCredentials)
        val savedSecret = secretStore.readSecret(
            providerId = ExternalProviderId.CAJA_INGENIEROS,
            connectionId = connection.id,
        )
        val savedConnections = connectionsRepository.observeConnections().first()

        assertEquals(ExternalConnectionStatus.NOT_CONNECTED, connection.status)
        assertEquals(ExternalSyncStatus.IDLE, connection.lastSyncStatus)
        assertEquals(1, savedConnections.size)
        assertTrue(savedSecret?.contains("sandbox-key") == true)
        assertTrue(savedSecret?.contains("SANDBOX") == true)
    }

    @Test
    fun runSyncExplainsThatLiveOauthExchangeIsStillPending() = runTest {
        val connection = service.connect(validCredentials)

        val error = assertFailsWith<IllegalStateException> {
            service.runSync(connection.id)
        }

        assertTrue(error.message!!.contains("OAuth token exchange", ignoreCase = true))
    }
}
