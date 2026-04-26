package com.myfinances.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myfinances.app.domain.model.integration.ExternalAccountLink
import com.myfinances.app.domain.model.integration.ExternalConnection
import com.myfinances.app.domain.model.integration.ExternalConnectionPreview
import com.myfinances.app.domain.model.integration.ExternalConnectionStatus
import com.myfinances.app.domain.model.integration.ExternalCredentialFieldDefinition
import com.myfinances.app.domain.model.integration.ExternalCredentialInputType
import com.myfinances.app.domain.model.integration.ExternalDiscoveredAccountPreview
import com.myfinances.app.domain.model.integration.ExternalIntegrationStage
import com.myfinances.app.domain.model.integration.ExternalProviderCatalog
import com.myfinances.app.domain.model.integration.ExternalProviderDefinition
import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.model.integration.ExternalSyncRun
import com.myfinances.app.domain.model.integration.ExternalSyncStatus
import com.myfinances.app.presentation.shared.formatTimestampLabel

@Composable
internal fun ConnectionsOverviewCard(
    uiState: SettingsUiState,
    onSelectConnection: (String) -> Unit,
    onProviderFieldChange: (ExternalProviderId, String, String) -> Unit,
    onTestProviderConnection: (ExternalProviderId) -> Unit,
    onConnectProvider: (ExternalProviderId) -> Unit,
    onRunProviderSync: (ExternalProviderId) -> Unit,
    onRunCajaBrowserSync: () -> Unit,
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
                text = "The connection and sync workflow is now provider-oriented instead of provider-specific. Indexa is still the first live provider, but the same setup card and sync controls are ready for the next integration.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ExternalProviderCatalog.availableProviders.forEach { provider ->
                val connection = uiState.connections.firstOrNull { item ->
                    item.providerId == provider.id
                }
                val providerState = uiState.providerState(provider.id)

                ProviderCard(
                    provider = provider,
                    connection = connection,
                    onSelectConnection = onSelectConnection,
                    isSelected = uiState.selectedConnectionId == connection?.id,
                )

                if (provider.credentialFields.isNotEmpty()) {
                    ProviderSetupCard(
                        provider = provider,
                        providerState = providerState,
                        connection = connection,
                        isSelectedConnection = uiState.selectedConnection?.id == connection?.id,
                        accountLinks = connection
                            ?.let { activeConnection -> uiState.accountLinksByConnection[activeConnection.id].orEmpty() }
                            .orEmpty(),
                        syncRuns = connection
                            ?.let { activeConnection -> uiState.syncRunsByConnection[activeConnection.id].orEmpty() }
                            .orEmpty(),
                        pendingDisconnectConnectionId = uiState.pendingDisconnectConnectionId,
                        onFieldChange = onProviderFieldChange,
                        onTestConnection = onTestProviderConnection,
                        onConnect = onConnectProvider,
                        onRunSync = onRunProviderSync,
                        onRunCajaBrowserSync = onRunCajaBrowserSync,
                        onRequestDisconnectConnection = onRequestDisconnectConnection,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderSetupCard(
    provider: ExternalProviderDefinition,
    providerState: ProviderConnectionUiState,
    connection: ExternalConnection?,
    isSelectedConnection: Boolean,
    accountLinks: List<ExternalAccountLink>,
    syncRuns: List<ExternalSyncRun>,
    pendingDisconnectConnectionId: String?,
    onFieldChange: (ExternalProviderId, String, String) -> Unit,
    onTestConnection: (ExternalProviderId) -> Unit,
    onConnect: (ExternalProviderId) -> Unit,
    onRunSync: (ExternalProviderId) -> Unit,
    onRunCajaBrowserSync: () -> Unit,
    onRequestDisconnectConnection: (String) -> Unit,
) {
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
                text = "${provider.displayName} setup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = "Set up ${provider.displayName} through the shared provider connector flow. The ledger, links, sync history, and disconnect behavior stay generic while each provider keeps its own API logic behind the connector.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (provider.setupInstructions.isNotEmpty()) {
                ProviderSetupInstructions(provider = provider)
            }

            provider.credentialFields.forEach { field ->
                ProviderCredentialField(
                    providerId = provider.id,
                    field = field,
                    value = providerState.fieldValue(field.id),
                    enabled = !providerState.isTesting && !providerState.isConnecting && !providerState.isSyncing,
                    onFieldChange = onFieldChange,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onTestConnection(provider.id) },
                    enabled = !providerState.isTesting && !providerState.isConnecting && !providerState.isSyncing,
                ) {
                    Text(if (providerState.isTesting) "Testing..." else "Test connection")
                }

                Button(
                    onClick = { onConnect(provider.id) },
                    enabled = !providerState.isTesting &&
                        !providerState.isConnecting &&
                        !providerState.isSyncing &&
                        providerHasRequiredCredentials(provider, providerState),
                ) {
                    Text(if (providerState.isConnecting) "Connecting..." else "Save connection")
                }

                if (provider.id == ExternalProviderId.CAJA_INGENIEROS) {
                    Button(
                        onClick = onRunCajaBrowserSync,
                        enabled = !providerState.isTesting &&
                            !providerState.isConnecting &&
                            !providerState.isSyncing,
                    ) {
                        Text(
                            if (providerState.isSyncing) {
                                "Waiting for download..."
                            } else {
                                "Sync via browser"
                            },
                        )
                    }
                }

                if (connection != null) {
                    Button(
                        onClick = { onRunSync(provider.id) },
                        enabled = !providerState.isTesting &&
                            !providerState.isConnecting &&
                            !providerState.isSyncing,
                    ) {
                        Text(if (providerState.isSyncing) "Syncing..." else "Sync now")
                    }

                    Button(
                        onClick = { onRequestDisconnectConnection(connection.id) },
                        enabled = !providerState.isTesting &&
                            !providerState.isConnecting &&
                            !providerState.isSyncing &&
                            pendingDisconnectConnectionId == null,
                    ) {
                        Text(
                            if (pendingDisconnectConnectionId == connection.id) {
                                "Disconnecting..."
                            } else {
                                "Disconnect"
                            },
                        )
                    }
                }
            }

            providerState.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            providerState.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (provider.id == ExternalProviderId.CAJA_INGENIEROS && connection == null) {
                Text(
                    text = "Sync via browser is the desktop fallback when API access is unavailable. It creates a local Caja connection automatically, opens the browser, and imports the first PDF statement you download.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            providerState.preview?.let { preview ->
                ProviderPreviewSection(preview = preview)
            }

            if (connection != null && isSelectedConnection) {
                ConnectionDetailSection(
                    connection = connection,
                    accountLinks = accountLinks,
                    syncRuns = syncRuns,
                    isSyncing = providerState.isSyncing,
                )
            } else {
                SyncHistorySection(
                    syncRuns = syncRuns,
                    isSyncing = providerState.isSyncing,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderCredentialField(
    providerId: ExternalProviderId,
    field: ExternalCredentialFieldDefinition,
    value: String,
    enabled: Boolean,
    onFieldChange: (ExternalProviderId, String, String) -> Unit,
) {
    if (field.inputType == ExternalCredentialInputType.SELECT && field.options.isNotEmpty()) {
        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded && enabled },
        ) {
            OutlinedTextField(
                value = field.options.firstOrNull { option -> option.value == value }?.label ?: "Select ${field.label}",
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                readOnly = true,
                enabled = enabled,
                label = { Text(field.label) },
                supportingText = {
                    field.supportingText?.let { text ->
                        Text(text)
                    }
                },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                field.options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onFieldChange(providerId, field.id, option.value)
                            expanded = false
                        },
                    )
                }
            }
        }
        return
    }

    OutlinedTextField(
        value = value,
        onValueChange = { nextValue -> onFieldChange(providerId, field.id, nextValue) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(field.label) },
        supportingText = {
            field.supportingText?.let { text ->
                Text(text)
            }
        },
        singleLine = true,
        enabled = enabled,
    )
}

@Composable
private fun ProviderSetupInstructions(provider: ExternalProviderDefinition) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Setup notes",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            provider.setupInstructions.forEachIndexed { index, instruction ->
                Text(
                    text = "${index + 1}. $instruction",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!provider.documentationLabel.isNullOrBlank() && !provider.documentationUrl.isNullOrBlank()) {
                Text(
                    text = "${provider.documentationLabel}: ${provider.documentationUrl}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConnectionDetailSection(
    connection: ExternalConnection,
    accountLinks: List<ExternalAccountLink>,
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
    accountLinks: List<ExternalAccountLink>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Linked accounts",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )

        if (accountLinks.isEmpty()) {
            Text(
                text = "No linked provider accounts yet. Run a sync to import or refresh the discovered provider accounts.",
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
private fun ProviderPreviewSection(preview: ExternalConnectionPreview) {
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
        preview.ownerLabel?.let { ownerLabel ->
            Text(
                text = "Detected account owner: $ownerLabel",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        preview.discoveredAccounts.forEach { account ->
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
                        text = "Account: ${account.providerAccountId}",
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

internal fun buildPreviewAccountSubtitle(previewAccount: ExternalDiscoveredAccountPreview): String {
    val type = previewAccount.accountTypeLabel ?: "unknown type"
    val currency = previewAccount.currencyCode ?: "unknown currency"
    val balance = previewAccount.balanceLabel?.let { value -> " | approx. $value" }.orEmpty()

    return "$type | $currency$balance"
}

private fun providerHasRequiredCredentials(
    provider: ExternalProviderDefinition,
    providerState: ProviderConnectionUiState,
): Boolean = provider.credentialFields
    .filter { field -> field.required }
    .all { field -> providerState.fieldValue(field.id).isNotBlank() }

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
                "The shared connection and sync foundation is ready. The next step is plugging in the live provider connector."
        ExternalIntegrationStage.ACTIVE ->
            "This provider is ready to be connected through the shared workflow."
    }
}

    if (connection.status == ExternalConnectionStatus.NEEDS_ATTENTION && !connection.lastErrorMessage.isNullOrBlank()) {
        return connection.lastErrorMessage
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
    accountLink: ExternalAccountLink,
): String {
    val type = accountLink.accountTypeLabel ?: "Unknown type"
    val currency = accountLink.currencyCode ?: "Unknown currency"
    val localLink = accountLink.localAccountId?.let { " | linked locally" }.orEmpty()
    return "$type | $currency$localLink"
}

internal fun buildLinkedAccountImportLabel(
    accountLink: ExternalAccountLink,
): String = accountLink.lastImportedAtEpochMs
    ?.let { importedAt -> "Last imported ${formatSyncRunTimestamp(importedAt)}" }
    ?: "Not imported yet"
