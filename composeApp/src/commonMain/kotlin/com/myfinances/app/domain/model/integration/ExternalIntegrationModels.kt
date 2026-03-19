package com.myfinances.app.domain.model.integration

enum class ExternalProviderId {
    INDEXA,
    CAJA_INGENIEROS,
}

enum class ExternalProviderCapability {
    ACCOUNT_DISCOVERY,
    HOLDINGS_SYNC,
    CASH_TRANSACTIONS_SYNC,
    INVESTMENT_TRANSACTIONS_SYNC,
    PERFORMANCE_SYNC,
    MANUAL_SYNC,
}

enum class ExternalConnectionStatus {
    NOT_CONNECTED,
    NEEDS_ATTENTION,
    CONNECTED,
    SYNCING,
}

enum class ExternalSyncStatus {
    IDLE,
    SUCCESS,
    FAILED,
    PARTIAL,
    RUNNING,
}

enum class ExternalIntegrationStage {
    PLANNED,
    SCAFFOLDED,
    ACTIVE,
}

data class ExternalProviderDefinition(
    val id: ExternalProviderId,
    val displayName: String,
    val summary: String,
    val capabilities: Set<ExternalProviderCapability>,
    val stage: ExternalIntegrationStage,
    val credentialLabel: String? = null,
    val credentialSupportingText: String? = null,
)

data class ExternalConnectionPreview(
    val suggestedConnectionName: String,
    val ownerLabel: String?,
    val discoveredAccounts: List<ExternalDiscoveredAccountPreview>,
)

data class ExternalDiscoveredAccountPreview(
    val providerAccountId: String,
    val displayName: String,
    val accountTypeLabel: String?,
    val currencyCode: String?,
    val balanceLabel: String? = null,
)

data class ExternalConnection(
    val id: String,
    val providerId: ExternalProviderId,
    val displayName: String,
    val status: ExternalConnectionStatus,
    val externalUserId: String?,
    val lastSuccessfulSyncEpochMs: Long?,
    val lastSyncAttemptEpochMs: Long?,
    val lastSyncStatus: ExternalSyncStatus,
    val lastErrorMessage: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

data class ExternalAccountLink(
    val connectionId: String,
    val providerAccountId: String,
    val localAccountId: String?,
    val accountDisplayName: String,
    val accountTypeLabel: String?,
    val currencyCode: String?,
    val lastImportedAtEpochMs: Long?,
)

data class ExternalSyncRun(
    val id: String,
    val connectionId: String,
    val providerId: ExternalProviderId,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long?,
    val status: ExternalSyncStatus,
    val importedAccounts: Int,
    val importedTransactions: Int,
    val importedPositions: Int,
    val message: String?,
)

object ExternalProviderCatalog {
    val availableProviders: List<ExternalProviderDefinition> = listOf(
        ExternalProviderDefinition(
            id = ExternalProviderId.INDEXA,
            displayName = "Indexa Capital",
            summary = "Read-only portfolio sync for accounts, holdings, and investment transactions.",
            capabilities = setOf(
                ExternalProviderCapability.ACCOUNT_DISCOVERY,
                ExternalProviderCapability.HOLDINGS_SYNC,
                ExternalProviderCapability.CASH_TRANSACTIONS_SYNC,
                ExternalProviderCapability.INVESTMENT_TRANSACTIONS_SYNC,
                ExternalProviderCapability.PERFORMANCE_SYNC,
                ExternalProviderCapability.MANUAL_SYNC,
            ),
            stage = ExternalIntegrationStage.ACTIVE,
            credentialLabel = "Indexa API token",
            credentialSupportingText = "Start with a personal read-only token from your Indexa account settings.",
        ),
        ExternalProviderDefinition(
            id = ExternalProviderId.CAJA_INGENIEROS,
            displayName = "Caja Ingenieros",
            summary = "PSD2-style banking sync for accounts, balances, and transactions.",
            capabilities = setOf(
                ExternalProviderCapability.ACCOUNT_DISCOVERY,
                ExternalProviderCapability.CASH_TRANSACTIONS_SYNC,
                ExternalProviderCapability.MANUAL_SYNC,
            ),
            stage = ExternalIntegrationStage.SCAFFOLDED,
            credentialLabel = null,
            credentialSupportingText = null,
        ),
    )
}
