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

enum class ExternalCredentialInputType {
    TEXT,
    SECRET,
    SELECT,
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
    val credentialFields: List<ExternalCredentialFieldDefinition> = emptyList(),
)

data class ExternalCredentialFieldDefinition(
    val id: String,
    val label: String,
    val supportingText: String? = null,
    val inputType: ExternalCredentialInputType = ExternalCredentialInputType.TEXT,
    val required: Boolean = true,
    val options: List<ExternalCredentialOption> = emptyList(),
)

data class ExternalCredentialOption(
    val value: String,
    val label: String,
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
            credentialFields = listOf(
                ExternalCredentialFieldDefinition(
                    id = "token",
                    label = "Indexa API token",
                    supportingText = "Start with a personal read-only token from your Indexa account settings.",
                    inputType = ExternalCredentialInputType.SECRET,
                ),
            ),
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
            credentialLabel = "Caja Ingenieros app credentials",
            credentialSupportingText = "Use the Sandbox or Production OAuth credentials created in the Caja Ingenieros developer portal.",
            credentialFields = listOf(
                ExternalCredentialFieldDefinition(
                    id = "environment",
                    label = "Environment",
                    supportingText = "Use Sandbox first while we finish the live OAuth/account-discovery flow.",
                    inputType = ExternalCredentialInputType.SELECT,
                    options = listOf(
                        ExternalCredentialOption(value = "sandbox", label = "Sandbox"),
                        ExternalCredentialOption(value = "production", label = "Production"),
                    ),
                ),
                ExternalCredentialFieldDefinition(
                    id = "consumerKey",
                    label = "Consumer key",
                    supportingText = "OAuth consumer key from the Caja Ingenieros API Market app.",
                ),
                ExternalCredentialFieldDefinition(
                    id = "consumerSecret",
                    label = "Consumer secret",
                    supportingText = "OAuth consumer secret generated for the Caja Ingenieros app.",
                    inputType = ExternalCredentialInputType.SECRET,
                ),
                ExternalCredentialFieldDefinition(
                    id = "appId",
                    label = "App ID",
                    supportingText = "Optional but useful for support and future live auth wiring.",
                    required = false,
                ),
            ),
        ),
    )
}
