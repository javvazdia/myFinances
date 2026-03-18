package com.myfinances.app.integrations.indexa.sync

import com.myfinances.app.domain.model.integration.ExternalAccountLink
import com.myfinances.app.domain.model.integration.ExternalConnection
import com.myfinances.app.domain.model.integration.ExternalConnectionStatus
import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.model.integration.ExternalSyncRun
import com.myfinances.app.domain.model.integration.ExternalSyncStatus
import com.myfinances.app.domain.repository.ConnectionSecretStore
import com.myfinances.app.domain.repository.ExternalConnectionsRepository
import com.myfinances.app.integrations.indexa.api.IndexaApiClient
import com.myfinances.app.integrations.indexa.model.IndexaConnectionPreview
import kotlinx.coroutines.flow.first
import kotlin.random.Random
import kotlin.time.Clock

class StubIndexaIntegrationService(
    private val apiClient: IndexaApiClient,
    private val externalConnectionsRepository: ExternalConnectionsRepository,
    private val connectionSecretStore: ConnectionSecretStore,
) : IndexaIntegrationService {
    override suspend fun testConnection(accessToken: String): IndexaConnectionPreview {
        val profile = apiClient.fetchUserProfile(accessToken)
        return IndexaConnectionPreview(
            profile = profile,
            suggestedConnectionName = buildSuggestedConnectionName(profile.fullName),
        )
    }

    override suspend fun connect(accessToken: String): ExternalConnection {
        val preview = testConnection(accessToken)
        val now = Clock.System.now().toEpochMilliseconds()
        val existingConnection = externalConnectionsRepository
            .observeConnections()
            .first()
            .firstOrNull { connection -> connection.providerId == ExternalProviderId.INDEXA }

        val connection = ExternalConnection(
            id = existingConnection?.id ?: "conn-indexa-$now-${Random.nextInt(1000, 9999)}",
            providerId = ExternalProviderId.INDEXA,
            displayName = preview.suggestedConnectionName,
            status = ExternalConnectionStatus.CONNECTED,
            externalUserId = preview.profile.email,
            lastSuccessfulSyncEpochMs = null,
            lastSyncAttemptEpochMs = now,
            lastSyncStatus = ExternalSyncStatus.IDLE,
            lastErrorMessage = null,
            createdAtEpochMs = existingConnection?.createdAtEpochMs ?: now,
            updatedAtEpochMs = now,
        )

        externalConnectionsRepository.upsertConnection(connection)
        externalConnectionsRepository.replaceAccountLinks(
            connectionId = connection.id,
            links = preview.profile.accounts.map { account ->
                ExternalAccountLink(
                    connectionId = connection.id,
                    providerAccountId = account.accountNumber,
                    localAccountId = null,
                    accountDisplayName = account.displayName,
                    accountTypeLabel = account.productType,
                    currencyCode = account.currencyCode,
                    lastImportedAtEpochMs = null,
                )
            },
        )
        connectionSecretStore.saveSecret(
            providerId = ExternalProviderId.INDEXA,
            connectionId = connection.id,
            secret = accessToken,
        )

        return connection
    }

    override suspend fun runSync(connectionId: String): ExternalSyncRun {
        val startedAt = Clock.System.now().toEpochMilliseconds()
        val syncRun = ExternalSyncRun(
            id = "sync-indexa-$startedAt-${Random.nextInt(1000, 9999)}",
            connectionId = connectionId,
            providerId = ExternalProviderId.INDEXA,
            startedAtEpochMs = startedAt,
            finishedAtEpochMs = startedAt,
            status = ExternalSyncStatus.IDLE,
            importedAccounts = 0,
            importedTransactions = 0,
            importedPositions = 0,
            message = "Indexa sync scaffolding is in place. The live HTTP client, secure storage, and import mapping are the next implementation step.",
        )

        externalConnectionsRepository.recordSyncRun(syncRun)
        connectionSecretStore.readSecret(
            providerId = ExternalProviderId.INDEXA,
            connectionId = connectionId,
        )

        return syncRun
    }

    private fun buildSuggestedConnectionName(fullName: String?): String =
        fullName?.trim()?.takeIf(String::isNotBlank)?.let { name ->
            "Indexa - $name"
        } ?: "Indexa Capital"
}
