package com.myfinances.app.integrations.cajaingenieros.sync

import com.myfinances.app.domain.model.integration.ExternalConnection
import com.myfinances.app.domain.model.integration.ExternalConnectionPreview
import com.myfinances.app.domain.model.integration.ExternalConnectionStatus
import com.myfinances.app.domain.model.integration.ExternalDiscoveredAccountPreview
import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.model.integration.ExternalSyncRun
import com.myfinances.app.domain.model.integration.ExternalSyncStatus
import com.myfinances.app.domain.repository.ConnectionSecretStore
import com.myfinances.app.domain.repository.ExternalConnectionsRepository
import com.myfinances.app.integrations.cajaingenieros.api.CajaIngenierosApiClient
import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosCredentialBundle
import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosEnvironment
import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosRegistrationMetadata
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.time.Clock

class ScaffoldedCajaIngenierosIntegrationService(
    private val apiClient: CajaIngenierosApiClient,
    private val externalConnectionsRepository: ExternalConnectionsRepository,
    private val connectionSecretStore: ConnectionSecretStore,
) : CajaIngenierosIntegrationService {
    override val providerId: ExternalProviderId
        get() = ExternalProviderId.CAJA_INGENIEROS

    override suspend fun fetchRegistrationMetadata(): CajaIngenierosRegistrationMetadata =
        apiClient.fetchRegistrationMetadata()

    override suspend fun testConnection(credentials: Map<String, String>): ExternalConnectionPreview {
        val bundle = credentials.toCajaIngenierosCredentialBundle()
        val metadata = fetchRegistrationMetadata()

        return ExternalConnectionPreview(
            suggestedConnectionName = buildSuggestedConnectionName(bundle.environment),
            ownerLabel = "${metadata.onboardingMode} | ${bundle.environment.label}",
            discoveredAccounts = emptyList<ExternalDiscoveredAccountPreview>(),
        )
    }

    override suspend fun connect(credentials: Map<String, String>): ExternalConnection {
        val bundle = credentials.toCajaIngenierosCredentialBundle()
        val now = Clock.System.now().toEpochMilliseconds()
        val existingConnection = externalConnectionsRepository
            .observeConnections()
            .first()
            .firstOrNull { connection -> connection.providerId == ExternalProviderId.CAJA_INGENIEROS }

        val connection = ExternalConnection(
            id = existingConnection?.id ?: "conn-caja-ingenieros-$now-${Random.nextInt(1000, 9999)}",
            providerId = ExternalProviderId.CAJA_INGENIEROS,
            displayName = buildSuggestedConnectionName(bundle.environment),
            status = ExternalConnectionStatus.NOT_CONNECTED,
            externalUserId = null,
            lastSuccessfulSyncEpochMs = null,
            lastSyncAttemptEpochMs = now,
            lastSyncStatus = ExternalSyncStatus.IDLE,
            lastErrorMessage = "Caja Ingenieros credentials saved. Live OAuth token exchange and account discovery are still pending.",
            createdAtEpochMs = existingConnection?.createdAtEpochMs ?: now,
            updatedAtEpochMs = now,
        )

        externalConnectionsRepository.upsertConnection(connection)
        connectionSecretStore.saveSecret(
            providerId = ExternalProviderId.CAJA_INGENIEROS,
            connectionId = connection.id,
            secret = Json.encodeToString(CajaIngenierosCredentialBundle.serializer(), bundle),
        )

        return connection
    }

    override suspend fun runSync(connectionId: String): ExternalSyncRun {
        val bundle = loadSavedCredentials(connectionId)
        error(
            "Caja Ingenieros credentials for ${bundle.environment.label} are saved, but the live OAuth token exchange and account discovery flow are not implemented yet.",
        )
    }

    override suspend fun disconnect(connectionId: String) {
        connectionSecretStore.deleteSecret(
            providerId = ExternalProviderId.CAJA_INGENIEROS,
            connectionId = connectionId,
        )
        externalConnectionsRepository.deleteConnection(connectionId)
    }

    private suspend fun loadSavedCredentials(connectionId: String): CajaIngenierosCredentialBundle {
        val secret = connectionSecretStore.readSecret(
            providerId = ExternalProviderId.CAJA_INGENIEROS,
            connectionId = connectionId,
        ) ?: error("Caja Ingenieros credentials are missing for this connection.")

        return Json.decodeFromString(CajaIngenierosCredentialBundle.serializer(), secret)
    }

    private fun buildSuggestedConnectionName(environment: CajaIngenierosEnvironment): String =
        "Caja Ingenieros (${environment.label})"
}

private fun Map<String, String>.toCajaIngenierosCredentialBundle(): CajaIngenierosCredentialBundle {
    val environment = when (this["environment"]?.trim()?.lowercase()) {
        "sandbox" -> CajaIngenierosEnvironment.SANDBOX
        "production" -> CajaIngenierosEnvironment.PRODUCTION
        else -> error("Choose a Caja Ingenieros environment first.")
    }
    val consumerKey = this["consumerKey"]?.trim().orEmpty()
    val consumerSecret = this["consumerSecret"]?.trim().orEmpty()

    if (consumerKey.isBlank()) {
        error("Paste the Caja Ingenieros consumer key first.")
    }
    if (consumerSecret.isBlank()) {
        error("Paste the Caja Ingenieros consumer secret first.")
    }

    return CajaIngenierosCredentialBundle(
        environment = environment,
        consumerKey = consumerKey,
        consumerSecret = consumerSecret,
        appId = this["appId"]?.trim()?.ifBlank { null },
    )
}

private val CajaIngenierosEnvironment.label: String
    get() = when (this) {
        CajaIngenierosEnvironment.SANDBOX -> "Sandbox"
        CajaIngenierosEnvironment.PRODUCTION -> "Production"
    }
