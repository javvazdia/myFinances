package com.myfinances.app.integrations.indexa.sync

import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.model.integration.ExternalSyncRun
import com.myfinances.app.domain.model.integration.ExternalSyncStatus
import com.myfinances.app.domain.repository.ConnectionSecretStore
import com.myfinances.app.domain.repository.ExternalConnectionsRepository
import com.myfinances.app.integrations.indexa.api.IndexaApiClient
import com.myfinances.app.integrations.indexa.model.IndexaConnectionPreview
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
