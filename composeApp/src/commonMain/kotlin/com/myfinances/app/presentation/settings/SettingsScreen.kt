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
import com.myfinances.app.domain.model.integration.ExternalSyncStatus
import com.myfinances.app.domain.repository.ExternalConnectionsRepository
import com.myfinances.app.domain.repository.LedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock

@Composable
fun SettingsRoute(
    ledgerRepository: LedgerRepository,
    externalConnectionsRepository: ExternalConnectionsRepository,
    settingsViewModel: SettingsViewModel = viewModel {
        SettingsViewModel(
            ledgerRepository = ledgerRepository,
            externalConnectionsRepository = externalConnectionsRepository,
        )
    },
) {
    val uiState by settingsViewModel.uiState.collectAsState()
    SettingsScreen(
        uiState = uiState,
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
            ConnectionsOverviewCard(uiState = uiState)
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
private fun ConnectionsOverviewCard(uiState: SettingsUiState) {
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
    val categories: List<Category> = emptyList(),
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                ledgerRepository.observeCategories(),
                externalConnectionsRepository.observeConnections(),
            ) { categories, connections ->
                categories to connections
            }.collect { (categories, connections) ->
                _uiState.update { currentState ->
                    currentState.copy(
                        connections = connections,
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
            "Last sync succeeded. The next step is to surface imported accounts, holdings, and sync history in the UI."
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
