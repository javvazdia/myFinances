package com.myfinances.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.CategoryKind
import com.myfinances.app.domain.model.integration.ExternalConnection
import com.myfinances.app.domain.model.integration.ExternalConnectionStatus
import com.myfinances.app.domain.model.integration.ExternalIntegrationStage
import com.myfinances.app.domain.model.integration.ExternalProviderCatalog
import com.myfinances.app.domain.model.integration.ExternalProviderDefinition
import com.myfinances.app.domain.model.integration.ExternalSyncRun
import com.myfinances.app.domain.model.integration.ExternalSyncStatus
import com.myfinances.app.domain.repository.ExternalConnectionsRepository
import com.myfinances.app.domain.repository.LedgerRepository
import com.myfinances.app.integrations.indexa.model.IndexaConnectionPreview
import com.myfinances.app.integrations.indexa.sync.IndexaIntegrationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
fun SettingsRoute(
    ledgerRepository: LedgerRepository,
    externalConnectionsRepository: ExternalConnectionsRepository,
    indexaIntegrationService: IndexaIntegrationService,
    settingsViewModel: SettingsViewModel = viewModel {
        SettingsViewModel(
            ledgerRepository = ledgerRepository,
            externalConnectionsRepository = externalConnectionsRepository,
            indexaIntegrationService = indexaIntegrationService,
        )
    },
) {
    val uiState by settingsViewModel.uiState.collectAsState()
    SettingsScreen(
        uiState = uiState,
        onIndexaTokenChange = settingsViewModel::onIndexaTokenChange,
        onTestIndexaConnection = settingsViewModel::testIndexaConnection,
        onConnectIndexa = settingsViewModel::connectIndexa,
        onRunIndexaSync = settingsViewModel::runIndexaSync,
        onNameChange = settingsViewModel::onNameChange,
        onKindSelected = settingsViewModel::onKindSelected,
        onSaveCategory = settingsViewModel::saveCategory,
        onEditCategory = settingsViewModel::editCategory,
        onCancelEditing = settingsViewModel::cancelEditing,
        onRequestDeleteCategory = settingsViewModel::requestDeleteCategory,
        onConfirmDeleteCategory = settingsViewModel::confirmDeleteCategory,
        onDismissDeleteDialog = settingsViewModel::dismissDeleteDialog,
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onIndexaTokenChange: (String) -> Unit,
    onTestIndexaConnection: () -> Unit,
    onConnectIndexa: () -> Unit,
    onRunIndexaSync: () -> Unit,
    onNameChange: (String) -> Unit,
    onKindSelected: (CategoryKind) -> Unit,
    onSaveCategory: () -> Unit,
    onEditCategory: (String) -> Unit,
    onCancelEditing: () -> Unit,
    onRequestDeleteCategory: (String) -> Unit,
    onConfirmDeleteCategory: () -> Unit,
    onDismissDeleteDialog: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.editingCategoryId) {
        if (uiState.editingCategoryId != null) {
            listState.animateScrollToItem(index = 3)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "Categories",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        item {
            Text(
                text = "Use categories to organize manual entries now and to classify imported transactions later when we connect external providers.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            ConnectionsOverviewCard(
                uiState = uiState,
                onIndexaTokenChange = onIndexaTokenChange,
                onTestIndexaConnection = onTestIndexaConnection,
                onConnectIndexa = onConnectIndexa,
                onRunIndexaSync = onRunIndexaSync,
            )
        }

        item {
            CategoryFormCard(
                uiState = uiState,
                onNameChange = onNameChange,
                onKindSelected = onKindSelected,
                onSaveCategory = onSaveCategory,
                onCancelEditing = onCancelEditing,
            )
        }

        item {
            Text(
                text = "Current categories",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (uiState.categories.isEmpty()) {
            item {
                Card {
                    Text(
                        text = "No categories yet. Create your first one above.",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            CategoryKind.entries.forEach { kind ->
                val categoriesForKind = uiState.categoriesByKind[kind].orEmpty()
                if (categoriesForKind.isNotEmpty()) {
                    item {
                        Text(
                            text = kind.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    items(
                        items = categoriesForKind,
                        key = { category -> category.id },
                    ) { category ->
                        CategoryCard(
                            category = category,
                            onEditCategory = onEditCategory,
                            onRequestDeleteCategory = onRequestDeleteCategory,
                            canInteract = !uiState.isBusy,
                            isEditing = uiState.editingCategoryId == category.id,
                            isDeleting = uiState.pendingDeleteCategoryId == category.id,
                        )
                    }
                }
            }
        }
    }

    if (uiState.deleteConfirmationCategoryId != null) {
        AlertDialog(
            onDismissRequest = onDismissDeleteDialog,
            title = {
                Text("Delete category?")
            },
            text = {
                Text(
                    "Delete ${uiState.deleteConfirmationCategoryName ?: "this category"}? Existing transactions will remain, but their category will be cleared.",
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDeleteCategory,
                    enabled = !uiState.isBusy,
                ) {
                    Text(if (uiState.pendingDeleteCategoryId != null) "Deleting..." else "Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissDeleteDialog,
                    enabled = !uiState.isBusy,
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ConnectionsOverviewCard(
    uiState: SettingsUiState,
    onIndexaTokenChange: (String) -> Unit,
    onTestIndexaConnection: () -> Unit,
    onConnectIndexa: () -> Unit,
    onRunIndexaSync: () -> Unit,
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
                )
            }

            IndexaSetupCard(
                uiState = uiState,
                onIndexaTokenChange = onIndexaTokenChange,
                onTestIndexaConnection = onTestIndexaConnection,
                onConnectIndexa = onConnectIndexa,
                onRunIndexaSync = onRunIndexaSync,
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
) {
    val indexaConnection = uiState.connections.firstOrNull { connection ->
        connection.providerId == com.myfinances.app.domain.model.integration.ExternalProviderId.INDEXA
    }
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

            SyncHistorySection(
                syncRuns = indexaSyncRuns,
                isSyncing = uiState.isSyncingIndexa,
            )
        }
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFormCard(
    uiState: SettingsUiState,
    onNameChange: (String) -> Unit,
    onKindSelected: (CategoryKind) -> Unit,
    onSaveCategory: () -> Unit,
    onCancelEditing: () -> Unit,
) {
    var kindMenuExpanded by remember { mutableStateOf(false) }
    val nameFocusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.editingCategoryId) {
        if (uiState.editingCategoryId != null) {
            nameFocusRequester.requestFocus()
        }
    }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (uiState.isEditing) "Edit category" else "Create category",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = if (uiState.isEditing) {
                    "You are updating a category in place, so existing transactions keep pointing to the same local category id."
                } else {
                    "This first flow creates custom categories locally. Later we can add icons, colors, rules, and sync mapping for imported transactions."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = uiState.draftName,
                onValueChange = onNameChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFocusRequester),
                label = { Text("Category name") },
                supportingText = { Text("Examples: Dining Out, Rent, Freelance") },
                singleLine = true,
                enabled = !uiState.isBusy,
            )

            ExposedDropdownMenuBox(
                expanded = kindMenuExpanded,
                onExpandedChange = { kindMenuExpanded = !kindMenuExpanded },
            ) {
                OutlinedTextField(
                    value = uiState.selectedKind.label,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    enabled = !uiState.isBusy,
                    label = { Text("Category type") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = kindMenuExpanded)
                    },
                )
                ExposedDropdownMenu(
                    expanded = kindMenuExpanded,
                    onDismissRequest = { kindMenuExpanded = false },
                ) {
                    CategoryKind.entries.forEach { kind ->
                        DropdownMenuItem(
                            text = { Text(kind.label) },
                            onClick = {
                                onKindSelected(kind)
                                kindMenuExpanded = false
                            },
                        )
                    }
                }
            }

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onSaveCategory,
                    enabled = !uiState.isBusy,
                ) {
                    Text(
                        when {
                            uiState.isSaving -> "Saving..."
                            uiState.isEditing -> "Save changes"
                            else -> "Create category"
                        },
                    )
                }

                if (uiState.isEditing) {
                    TextButton(
                        onClick = onCancelEditing,
                        enabled = !uiState.isBusy,
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    onEditCategory: (String) -> Unit,
    onRequestDeleteCategory: (String) -> Unit,
    canInteract: Boolean,
    isEditing: Boolean,
    isDeleting: Boolean,
) {
    Card(
        colors = if (isEditing) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.fillMaxWidth(0.7f)) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = category.kind.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = if (category.isSystem) "System" else "Custom",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (category.isSystem) {
                Text(
                    text = "System categories are seeded defaults and can’t be edited or deleted from the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { onEditCategory(category.id) },
                        enabled = canInteract,
                    ) {
                        Text(if (isEditing) "Editing" else "Edit")
                    }
                    TextButton(
                        onClick = { onRequestDeleteCategory(category.id) },
                        enabled = canInteract,
                    ) {
                        Text(
                            text = if (isDeleting) "Deleting..." else "Delete",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

data class SettingsUiState(
    val connections: List<ExternalConnection> = emptyList(),
    val syncRunsByConnection: Map<String, List<ExternalSyncRun>> = emptyMap(),
    val categories: List<Category> = emptyList(),
    val draftIndexaToken: String = "",
    val indexaPreview: IndexaConnectionPreview? = null,
    val isTestingIndexa: Boolean = false,
    val isConnectingIndexa: Boolean = false,
    val isSyncingIndexa: Boolean = false,
    val indexaConnectionMessage: String? = null,
    val indexaConnectionError: String? = null,
    val draftName: String = "",
    val selectedKind: CategoryKind = CategoryKind.EXPENSE,
    val editingCategoryId: String? = null,
    val deleteConfirmationCategoryId: String? = null,
    val isSaving: Boolean = false,
    val pendingDeleteCategoryId: String? = null,
    val errorMessage: String? = null,
) {
    val categoriesByKind: Map<CategoryKind, List<Category>>
        get() = categories.groupBy { category -> category.kind }

    val isEditing: Boolean
        get() = editingCategoryId != null

    val deleteConfirmationCategoryName: String?
        get() = categories.firstOrNull { category ->
            category.id == deleteConfirmationCategoryId
        }?.name

    val isBusy: Boolean
        get() = isSaving || pendingDeleteCategoryId != null
}

class SettingsViewModel(
    private val ledgerRepository: LedgerRepository,
    private val externalConnectionsRepository: ExternalConnectionsRepository,
    private val indexaIntegrationService: IndexaIntegrationService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        val connectionsFlow = externalConnectionsRepository.observeConnections()

        viewModelScope.launch {
            combine(
                ledgerRepository.observeCategories(),
                connectionsFlow,
                connectionsFlow.flatMapLatest { connections ->
                    if (connections.isEmpty()) {
                        flowOf(emptyMap())
                    } else {
                        combine(
                            connections.map { connection ->
                                externalConnectionsRepository.observeSyncRuns(connection.id)
                            },
                        ) { syncRunLists ->
                            connections.zip(syncRunLists).associate { (connection, syncRuns) ->
                                connection.id to syncRuns.sortedByDescending(ExternalSyncRun::startedAtEpochMs)
                            }
                        }
                    }
                },
            ) { categories, connections, syncRunsByConnection ->
                Triple(categories, connections, syncRunsByConnection)
            }.collect { (categories, connections, syncRunsByConnection) ->
                _uiState.update { currentState ->
                    currentState.copy(
                        connections = connections,
                        syncRunsByConnection = syncRunsByConnection,
                        categories = categories,
                        editingCategoryId = currentState.editingCategoryId
                            ?.takeIf { editingId ->
                                categories.any { category -> category.id == editingId }
                            },
                        deleteConfirmationCategoryId = currentState.deleteConfirmationCategoryId
                            ?.takeIf { categoryId ->
                                categories.any { category -> category.id == categoryId }
                            },
                        pendingDeleteCategoryId = currentState.pendingDeleteCategoryId
                            ?.takeIf { categoryId ->
                                categories.any { category -> category.id == categoryId }
                            },
                    )
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { currentState ->
            currentState.copy(
                draftName = value,
                errorMessage = null,
            )
        }
    }

    fun onIndexaTokenChange(value: String) {
        _uiState.update { currentState ->
            currentState.copy(
                draftIndexaToken = value,
                indexaPreview = null,
                indexaConnectionMessage = null,
                indexaConnectionError = null,
            )
        }
    }

    fun testIndexaConnection() {
        val token = uiState.value.draftIndexaToken.trim()
        if (token.isBlank()) {
            _uiState.update { currentState ->
                currentState.copy(
                    indexaConnectionError = "Paste an Indexa API token first.",
                    indexaConnectionMessage = null,
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    isTestingIndexa = true,
                    indexaConnectionError = null,
                    indexaConnectionMessage = null,
                )
            }

            runCatching {
                indexaIntegrationService.testConnection(token)
            }.onSuccess { preview ->
                _uiState.update { currentState ->
                    currentState.copy(
                        isTestingIndexa = false,
                        indexaPreview = preview,
                        indexaConnectionMessage = "Connection test succeeded. Review the discovered accounts and save the connection when ready.",
                        indexaConnectionError = null,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { currentState ->
                    currentState.copy(
                        isTestingIndexa = false,
                        indexaPreview = null,
                        indexaConnectionMessage = null,
                        indexaConnectionError = throwable.message ?: "Indexa connection test failed.",
                    )
                }
            }
        }
    }

    fun connectIndexa() {
        val token = uiState.value.draftIndexaToken.trim()
        if (token.isBlank()) {
            _uiState.update { currentState ->
                currentState.copy(
                    indexaConnectionError = "Paste an Indexa API token first.",
                    indexaConnectionMessage = null,
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    isConnectingIndexa = true,
                    indexaConnectionError = null,
                    indexaConnectionMessage = null,
                )
            }

            runCatching {
                indexaIntegrationService.connect(token)
            }.onSuccess { connection ->
                _uiState.update { currentState ->
                    currentState.copy(
                        draftIndexaToken = "",
                        isConnectingIndexa = false,
                        indexaConnectionMessage = "Saved ${connection.displayName}. Run Sync now to import those discovered Indexa accounts into the local ledger.",
                        indexaConnectionError = null,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { currentState ->
                    currentState.copy(
                        isConnectingIndexa = false,
                        indexaConnectionMessage = null,
                        indexaConnectionError = throwable.message ?: "Indexa connection setup failed.",
                    )
                }
            }
        }
    }

    fun runIndexaSync() {
        val connectionId = uiState.value.connections.firstOrNull { connection ->
            connection.providerId == com.myfinances.app.domain.model.integration.ExternalProviderId.INDEXA
        }?.id

        if (connectionId == null) {
            _uiState.update { currentState ->
                currentState.copy(
                    indexaConnectionError = "Save the Indexa connection before running sync.",
                    indexaConnectionMessage = null,
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    isSyncingIndexa = true,
                    indexaConnectionError = null,
                    indexaConnectionMessage = null,
                )
            }

            runCatching {
                indexaIntegrationService.runSync(connectionId)
            }.onSuccess { syncRun ->
                _uiState.update { currentState ->
                    currentState.copy(
                        isSyncingIndexa = false,
                        indexaConnectionMessage = syncRun.message
                            ?: "Indexa sync completed successfully.",
                        indexaConnectionError = null,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { currentState ->
                    currentState.copy(
                        isSyncingIndexa = false,
                        indexaConnectionMessage = null,
                        indexaConnectionError = throwable.message ?: "Indexa sync failed.",
                    )
                }
            }
        }
    }

    fun onKindSelected(value: CategoryKind) {
        _uiState.update { currentState ->
            currentState.copy(
                selectedKind = value,
                errorMessage = null,
            )
        }
    }

    fun editCategory(categoryId: String) {
        _uiState.update { currentState ->
            val category = currentState.categories.firstOrNull { item -> item.id == categoryId }
                ?: return@update currentState

            if (category.isSystem) {
                return@update currentState.copy(
                    errorMessage = "System categories can't be edited from the app.",
                )
            }

            currentState.copy(
                draftName = category.name,
                selectedKind = category.kind,
                editingCategoryId = category.id,
                deleteConfirmationCategoryId = null,
                pendingDeleteCategoryId = null,
                errorMessage = null,
            )
        }
    }

    fun cancelEditing() {
        _uiState.update { currentState ->
            currentState.toCreateMode()
        }
    }

    fun saveCategory() {
        val snapshot = uiState.value
        val normalizedName = normalizeCategoryName(snapshot.draftName)
        val existingCategory = snapshot.editingCategoryId?.let { editingId ->
            snapshot.categories.firstOrNull { category -> category.id == editingId }
        }

        val error = when {
            normalizedName.isBlank() -> "Category name is required."
            existingCategory?.isSystem == true -> "System categories can't be edited from the app."
            snapshot.categories.any { category ->
                category.id != snapshot.editingCategoryId &&
                    category.kind == snapshot.selectedKind &&
                    category.name.equals(normalizedName, ignoreCase = true)
            } -> "A ${snapshot.selectedKind.label.lowercase()} category with that name already exists."
            else -> null
        }

        if (error != null) {
            _uiState.update { currentState ->
                currentState.copy(errorMessage = error)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    isSaving = true,
                    errorMessage = null,
                )
            }

            val now = Clock.System.now().toEpochMilliseconds()
            ledgerRepository.upsertCategory(
                Category(
                    id = existingCategory?.id ?: generateCategoryId(normalizedName, snapshot.selectedKind, now),
                    name = normalizedName,
                    kind = snapshot.selectedKind,
                    colorHex = existingCategory?.colorHex,
                    iconKey = existingCategory?.iconKey,
                    isSystem = existingCategory?.isSystem ?: false,
                    isArchived = existingCategory?.isArchived ?: false,
                    createdAtEpochMs = existingCategory?.createdAtEpochMs ?: now,
                ),
            )

            _uiState.update { currentState ->
                currentState.toCreateMode().copy(
                    isSaving = false,
                )
            }
        }
    }

    fun requestDeleteCategory(categoryId: String) {
        _uiState.update { currentState ->
            val category = currentState.categories.firstOrNull { item -> item.id == categoryId }
                ?: return@update currentState

            if (category.isSystem) {
                return@update currentState.copy(
                    errorMessage = "System categories can't be deleted from the app.",
                )
            }

            currentState.copy(
                deleteConfirmationCategoryId = categoryId,
                errorMessage = null,
            )
        }
    }

    fun dismissDeleteDialog() {
        _uiState.update { currentState ->
            currentState.copy(
                deleteConfirmationCategoryId = null,
                errorMessage = null,
            )
        }
    }

    fun confirmDeleteCategory() {
        val snapshot = uiState.value
        val categoryId = snapshot.deleteConfirmationCategoryId ?: return
        val category = snapshot.categories.firstOrNull { item -> item.id == categoryId } ?: return

        if (category.isSystem) {
            _uiState.update { currentState ->
                currentState.copy(
                    deleteConfirmationCategoryId = null,
                    errorMessage = "System categories can't be deleted from the app.",
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    deleteConfirmationCategoryId = null,
                    pendingDeleteCategoryId = categoryId,
                    errorMessage = null,
                )
            }

            ledgerRepository.deleteCategory(categoryId)

            _uiState.update { currentState ->
                if (currentState.editingCategoryId == categoryId) {
                    currentState.toCreateMode()
                } else {
                    currentState.copy(
                        pendingDeleteCategoryId = null,
                        errorMessage = null,
                    )
                }
            }
        }
    }
}

internal val CategoryKind.label: String
    get() = name
        .lowercase()
        .replaceFirstChar { character -> character.uppercase() }

internal fun normalizeCategoryName(rawValue: String): String =
    rawValue
        .trim()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .joinToString(" ")

internal fun generateCategoryId(
    name: String,
    kind: CategoryKind,
    timestampMs: Long,
): String {
    val slug = normalizeCategoryName(name)
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "category" }

    return "category-${kind.name.lowercase()}-$slug-$timestampMs-${Random.nextInt(1000, 9999)}"
}

private fun SettingsUiState.toCreateMode(): SettingsUiState =
    copy(
        draftName = "",
        editingCategoryId = null,
        deleteConfirmationCategoryId = null,
        isSaving = false,
        pendingDeleteCategoryId = null,
        errorMessage = null,
    )

private fun buildPreviewAccountSubtitle(previewAccount: com.myfinances.app.integrations.indexa.model.IndexaAccountSummary): String {
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

internal fun formatSyncRunTimestamp(epochMs: Long): String {
    val localDateTime = Instant
        .fromEpochMilliseconds(epochMs)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val month = localDateTime.month.name
        .lowercase()
        .replaceFirstChar { character -> character.uppercase() }
        .take(3)
    val day = localDateTime.dayOfMonth
    val hour = localDateTime.hour.toString().padStart(2, '0')
    val minute = localDateTime.minute.toString().padStart(2, '0')
    return "$month $day, $hour:$minute"
}

internal fun buildSyncRunSummary(syncRun: ExternalSyncRun): String {
    val accountLabel = if (syncRun.importedAccounts == 1) "account" else "accounts"
    val transactionLabel = if (syncRun.importedTransactions == 1) "transaction" else "transactions"
    val positionLabel = if (syncRun.importedPositions == 1) "position" else "positions"

    return "${syncRun.importedAccounts} $accountLabel, ${syncRun.importedTransactions} $transactionLabel, ${syncRun.importedPositions} $positionLabel"
}
