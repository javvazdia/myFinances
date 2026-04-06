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
        assertTrue(preview.ownerLabel!!.contains("Sandbox", ignoreCase = true))
        assertTrue(preview.ownerLabel.contains("token", ignoreCase = true))
        assertTrue(preview.discoveredAccounts.isEmpty())
    }

    @Test
    fun connectStoresStructuredCredentialsForLaterOauthWork() = runTest {
        val connection = service.connect(validCredentials)
        val savedSecret = secretStore.readSecret(
            providerId = ExternalProviderId.CAJA_INGENIEROS,
            connectionId = connection.id,
        )!!
        val savedConnections = connectionsRepository.observeConnections().first()

        assertEquals(ExternalConnectionStatus.NEEDS_ATTENTION, connection.status)
        assertEquals(ExternalSyncStatus.IDLE, connection.lastSyncStatus)
        assertEquals(1, savedConnections.size)
        assertTrue(savedSecret.contains("sandbox-key"))
        assertTrue(savedSecret.contains("SANDBOX"))
        assertTrue(connection.lastErrorMessage!!.contains("signed AIS requests", ignoreCase = true))
    }

    @Test
    fun runSyncExplainsThatLiveOauthExchangeIsStillPendingAndRecordsFailedRun() = runTest {
        val connection = service.connect(validCredentials)

        val error = assertFailsWith<IllegalStateException> {
            service.runSync(connection.id)
        }
        val savedConnection = connectionsRepository.observeConnections().first().single()
        val savedSyncRuns = connectionsRepository.observeSyncRuns(connection.id).first()

        assertTrue(error.message!!.contains("signed AIS requests", ignoreCase = true))
        assertEquals(ExternalConnectionStatus.NEEDS_ATTENTION, savedConnection.status)
        assertEquals(ExternalSyncStatus.FAILED, savedConnection.lastSyncStatus)
        assertEquals(1, savedSyncRuns.size)
        assertEquals(ExternalSyncStatus.FAILED, savedSyncRuns.single().status)
        assertTrue(savedSyncRuns.single().message!!.contains("Token scope", ignoreCase = true))
    }
}
