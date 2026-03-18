package com.myfinances.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.CategoryKind
import com.myfinances.app.domain.model.integration.ExternalConnection
import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.model.integration.ExternalSyncRun
import com.myfinances.app.domain.repository.ExternalConnectionsRepository
import com.myfinances.app.domain.repository.LedgerRepository
import com.myfinances.app.integrations.indexa.model.IndexaConnectionPreview
import com.myfinances.app.integrations.indexa.sync.IndexaIntegrationService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock

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
        get() = categories.groupBy(Category::kind)

    val isEditing: Boolean
        get() = editingCategoryId != null

    val deleteConfirmationCategoryName: String?
        get() = categories.firstOrNull { category ->
            category.id == deleteConfirmationCategoryId
        }?.name

    val isBusy: Boolean
        get() = isSaving || pendingDeleteCategoryId != null
}

@OptIn(ExperimentalCoroutinesApi::class)
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
            connection.providerId == ExternalProviderId.INDEXA
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

private fun SettingsUiState.toCreateMode(): SettingsUiState =
    copy(
        draftName = "",
        editingCategoryId = null,
        deleteConfirmationCategoryId = null,
        isSaving = false,
        pendingDeleteCategoryId = null,
        errorMessage = null,
    )

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
