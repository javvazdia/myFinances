package com.myfinances.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myfinances.app.domain.model.integration.ExternalConnection
import com.myfinances.app.domain.model.integration.ExternalConnectionStatus
import com.myfinances.app.domain.model.integration.ExternalIntegrationStage
import com.myfinances.app.domain.model.integration.ExternalProviderCatalog
import com.myfinances.app.domain.model.integration.ExternalProviderDefinition
import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.model.integration.ExternalSyncRun
import com.myfinances.app.domain.model.integration.ExternalSyncStatus
import com.myfinances.app.integrations.indexa.model.IndexaAccountSummary
import com.myfinances.app.integrations.indexa.model.IndexaConnectionPreview
import com.myfinances.app.presentation.shared.formatTimestampLabel

@Composable
internal fun ConnectionsOverviewCard(
    uiState: SettingsUiState,
    onSelectConnection: (String) -> Unit,
    onIndexaTokenChange: (String) -> Unit,
    onTestIndexaConnection: () -> Unit,
    onConnectIndexa: () -> Unit,
    onRunIndexaSync: () -> Unit,
    onRequestDisconnectConnection: (String) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Connections",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = "This new foundation is provider-agnostic. Indexa is the first target, but the same connection and sync model is meant to support more brokers and banks later.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ExternalProviderCatalog.availableProviders.forEach { provider ->
                val connection = uiState.connections.firstOrNull { item ->
                    item.providerId == provider.id
                }
                ProviderCard(
                    provider = provider,
                    connection = connection,
                    onSelectConnection = onSelectConnection,
                    isSelected = uiState.selectedConnectionId == connection?.id,
                )
            }

            IndexaSetupCard(
                uiState = uiState,
                onIndexaTokenChange = onIndexaTokenChange,
                onTestIndexaConnection = onTestIndexaConnection,
                onConnectIndexa = onConnectIndexa,
                onRunIndexaSync = onRunIndexaSync,
                onRequestDisconnectConnection = onRequestDisconnectConnection,
            )
        }
    }
}

@Composable
private fun IndexaSetupCard(
    uiState: SettingsUiState,
    onIndexaTokenChange: (String) -> Unit,
    onTestIndexaConnection: () -> Unit,
    onConnectIndexa: () -> Unit,
    onRunIndexaSync: () -> Unit,
    onRequestDisconnectConnection: (String) -> Unit,
) {
    val indexaConnection = uiState.connections.firstOrNull { connection ->
        connection.providerId == ExternalProviderId.INDEXA
    }
    val isSelectedConnection = uiState.selectedConnection?.id == indexaConnection?.id
    val indexaSyncRuns = indexaConnection
        ?.let { connection -> uiState.syncRunsByConnection[connection.id].orEmpty() }
        .orEmpty()

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Indexa setup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = "Paste your read-only Indexa API token, test the connection, and then save the local connection. This token is kept behind the secret-store abstraction, not in the ledger database.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = uiState.draftIndexaToken,
                onValueChange = onIndexaTokenChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Indexa API token") },
                supportingText = { Text("Start with a personal read-only token from your Indexa account settings.") },
                singleLine = true,
                enabled = !uiState.isTestingIndexa && !uiState.isConnectingIndexa && !uiState.isSyncingIndexa,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onTestIndexaConnection,
                    enabled = !uiState.isTestingIndexa && !uiState.isConnectingIndexa && !uiState.isSyncingIndexa,
                ) {
                    Text(if (uiState.isTestingIndexa) "Testing..." else "Test connection")
                }

                Button(
                    onClick = onConnectIndexa,
                    enabled = !uiState.isTestingIndexa &&
                        !uiState.isConnectingIndexa &&
                        !uiState.isSyncingIndexa &&
                        uiState.draftIndexaToken.isNotBlank(),
                ) {
                    Text(if (uiState.isConnectingIndexa) "Connecting..." else "Save connection")
                }

                if (indexaConnection != null) {
                    Button(
                        onClick = onRunIndexaSync,
                        enabled = !uiState.isTestingIndexa &&
                            !uiState.isConnectingIndexa &&
                            !uiState.isSyncingIndexa,
                    ) {
                        Text(if (uiState.isSyncingIndexa) "Syncing..." else "Sync now")
                    }

                    Button(
                        onClick = { onRequestDisconnectConnection(indexaConnection.id) },
                        enabled = !uiState.isTestingIndexa &&
                            !uiState.isConnectingIndexa &&
                            !uiState.isSyncingIndexa &&
                            uiState.pendingDisconnectConnectionId == null,
                    ) {
                        Text(
                            if (uiState.pendingDisconnectConnectionId == indexaConnection.id) {
                                "Disconnecting..."
                            } else {
                                "Disconnect"
                            },
                        )
                    }
                }
            }

            if (uiState.indexaConnectionMessage != null) {
                Text(
                    text = uiState.indexaConnectionMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (uiState.indexaConnectionError != null) {
                Text(
                    text = uiState.indexaConnectionError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (uiState.indexaPreview != null) {
                IndexaPreviewSection(preview = uiState.indexaPreview)
            }

            if (indexaConnection != null && isSelectedConnection) {
                ConnectionDetailSection(
                    connection = indexaConnection,
                    accountLinks = uiState.selectedConnection
                        ?.takeIf { connection -> connection.id == indexaConnection.id }
                        ?.let { uiState.selectedConnectionAccountLinks }
                        ?: uiState.accountLinksByConnection[indexaConnection.id].orEmpty(),
                    syncRuns = indexaSyncRuns,
                    isSyncing = uiState.isSyncingIndexa,
                )
            } else {
                SyncHistorySection(
                    syncRuns = indexaSyncRuns,
                    isSyncing = uiState.isSyncingIndexa,
                )
            }
        }
    }
}

@Composable
private fun ConnectionDetailSection(
    connection: ExternalConnection,
    accountLinks: List<com.myfinances.app.domain.model.integration.ExternalAccountLink>,
    syncRuns: List<ExternalSyncRun>,
    isSyncing: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Connection details",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DetailLine(label = "Connection", value = connection.displayName)
                DetailLine(label = "Status", value = connection.status.label)
                DetailLine(
                    label = "Last successful sync",
                    value = connection.lastSuccessfulSyncEpochMs
                        ?.let(::formatSyncRunTimestamp)
                        ?: "No successful sync yet",
                )
                connection.externalUserId?.let { externalUserId ->
                    DetailLine(label = "External user", value = externalUserId)
                }
                DetailLine(
                    label = "Linked accounts",
                    value = accountLinks.size.toString(),
                )
            }
        }

        LinkedAccountsSection(accountLinks = accountLinks)
        SyncHistorySection(
            syncRuns = syncRuns,
            isSyncing = isSyncing,
        )
    }
}

@Composable
private fun SyncHistorySection(
    syncRuns: List<ExternalSyncRun>,
    isSyncing: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Recent sync history",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )

        if (syncRuns.isEmpty()) {
            Text(
                text = if (isSyncing) {
                    "A sync is running now. The first completed run will appear here."
                } else {
                    "No sync runs yet. Save the connection and use Sync now to start building sync history."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        syncRuns
            .sortedByDescending(ExternalSyncRun::startedAtEpochMs)
            .take(5)
            .forEach { syncRun ->
                SyncRunCard(syncRun = syncRun)
            }
    }
}

@Composable
private fun SyncRunCard(syncRun: ExternalSyncRun) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = syncRun.status.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = when (syncRun.status) {
                        ExternalSyncStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                        ExternalSyncStatus.FAILED -> MaterialTheme.colorScheme.error
                        ExternalSyncStatus.PARTIAL -> MaterialTheme.colorScheme.tertiary
                        ExternalSyncStatus.RUNNING -> MaterialTheme.colorScheme.primary
                        ExternalSyncStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = formatSyncRunTimestamp(syncRun.startedAtEpochMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = buildSyncRunSummary(syncRun),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (syncRun.finishedAtEpochMs != null) {
                Text(
                    text = "Finished ${formatSyncRunTimestamp(syncRun.finishedAtEpochMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!syncRun.message.isNullOrBlank()) {
                Text(
                    text = syncRun.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LinkedAccountsSection(
    accountLinks: List<com.myfinances.app.domain.model.integration.ExternalAccountLink>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Linked accounts",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )

        if (accountLinks.isEmpty()) {
            Text(
                text = "No linked provider accounts yet. Run a sync to import or refresh the discovered Indexa accounts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        accountLinks.forEach { accountLink ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = accountLink.accountDisplayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = buildLinkedAccountSubtitle(accountLink),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = buildLinkedAccountImportLabel(accountLink),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun IndexaPreviewSection(preview: IndexaConnectionPreview) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Connection preview",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = "Suggested name: ${preview.suggestedConnectionName}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Detected account owner: ${preview.profile.fullName ?: preview.profile.email}",
            style = MaterialTheme.typography.bodyMedium,
        )

        preview.profile.accounts.forEach { account ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = account.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Account: ${account.accountNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = buildPreviewAccountSubtitle(account),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: ExternalProviderDefinition,
    connection: ExternalConnection?,
    onSelectConnection: (String) -> Unit,
    isSelected: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.fillMaxWidth(0.7f)) {
                    Text(
                        text = provider.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = provider.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = connection?.status?.label ?: provider.stage.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Text(
                text = buildConnectionStatusMessage(provider, connection),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (connection != null) {
                Button(
                    onClick = { onSelectConnection(connection.id) },
                ) {
                    Text(if (isSelected) "Managing" else "Manage")
                }
            }
        }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun buildPreviewAccountSubtitle(previewAccount: IndexaAccountSummary): String {
    val type = previewAccount.productType ?: "unknown type"
    val currency = previewAccount.currencyCode ?: "unknown currency"
    val valuation = previewAccount.currentValuation?.let { value ->
        " | approx. $value $currency"
    }.orEmpty()

    return "$type | $currency$valuation"
}

private val ExternalIntegrationStage.label: String
    get() = when (this) {
        ExternalIntegrationStage.PLANNED -> "Planned"
        ExternalIntegrationStage.SCAFFOLDED -> "Scaffolded"
        ExternalIntegrationStage.ACTIVE -> "Active"
    }

private val ExternalConnectionStatus.label: String
    get() = when (this) {
        ExternalConnectionStatus.NOT_CONNECTED -> "Not connected"
        ExternalConnectionStatus.NEEDS_ATTENTION -> "Needs attention"
        ExternalConnectionStatus.CONNECTED -> "Connected"
        ExternalConnectionStatus.SYNCING -> "Syncing"
    }

private val ExternalSyncStatus.label: String
    get() = when (this) {
        ExternalSyncStatus.IDLE -> "Idle"
        ExternalSyncStatus.SUCCESS -> "Success"
        ExternalSyncStatus.FAILED -> "Failed"
        ExternalSyncStatus.PARTIAL -> "Partial"
        ExternalSyncStatus.RUNNING -> "Running"
    }

private fun buildConnectionStatusMessage(
    provider: ExternalProviderDefinition,
    connection: ExternalConnection?,
): String {
    if (connection == null) {
        return when (provider.stage) {
            ExternalIntegrationStage.PLANNED ->
                "This provider is on the roadmap but not scaffolded yet."
            ExternalIntegrationStage.SCAFFOLDED ->
                "The shared connection and sync foundation is ready. The next step is the live token-based setup and import flow."
            ExternalIntegrationStage.ACTIVE ->
                "This provider is ready to be connected."
        }
    }

    return when (connection.lastSyncStatus) {
        ExternalSyncStatus.SUCCESS ->
            "Last sync succeeded. Imported accounts should now be available in the Accounts tab."
        ExternalSyncStatus.FAILED ->
            connection.lastErrorMessage ?: "The last sync failed and needs attention."
        ExternalSyncStatus.PARTIAL ->
            connection.lastErrorMessage ?: "The last sync completed partially."
        ExternalSyncStatus.RUNNING ->
            "A sync is currently running for this connection."
        ExternalSyncStatus.IDLE ->
            "This connection exists in local state, but a live sync flow has not been completed yet."
    }
}

internal fun formatSyncRunTimestamp(epochMs: Long): String =
    formatTimestampLabel(epochMs)

internal fun buildSyncRunSummary(syncRun: ExternalSyncRun): String {
    val accountLabel = if (syncRun.importedAccounts == 1) "account" else "accounts"
    val transactionLabel = if (syncRun.importedTransactions == 1) "transaction" else "transactions"
    val positionLabel = if (syncRun.importedPositions == 1) "position" else "positions"

    return "${syncRun.importedAccounts} $accountLabel, ${syncRun.importedTransactions} $transactionLabel, ${syncRun.importedPositions} $positionLabel"
}

internal fun buildLinkedAccountSubtitle(
    accountLink: com.myfinances.app.domain.model.integration.ExternalAccountLink,
): String {
    val type = accountLink.accountTypeLabel ?: "Unknown type"
    val currency = accountLink.currencyCode ?: "Unknown currency"
    val localLink = accountLink.localAccountId?.let { " | linked locally" }.orEmpty()
    return "$type | $currency$localLink"
}

internal fun buildLinkedAccountImportLabel(
    accountLink: com.myfinances.app.domain.model.integration.ExternalAccountLink,
): String = accountLink.lastImportedAtEpochMs
    ?.let { importedAt -> "Last imported ${formatSyncRunTimestamp(importedAt)}" }
    ?: "Not imported yet"
