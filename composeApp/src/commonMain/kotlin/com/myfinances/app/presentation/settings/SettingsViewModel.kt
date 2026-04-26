package com.myfinances.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.CategoryKind
import com.myfinances.app.domain.model.integration.ExternalAccountLink
import com.myfinances.app.domain.model.integration.ExternalConnection
import com.myfinances.app.domain.model.integration.ExternalConnectionPreview
import com.myfinances.app.domain.model.integration.ExternalConnectionStatus
import com.myfinances.app.domain.model.integration.ExternalProviderCatalog
import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.model.integration.ExternalSyncRun
import com.myfinances.app.domain.model.integration.ExternalSyncStatus
import com.myfinances.app.domain.repository.ExternalConnectionsRepository
import com.myfinances.app.domain.repository.LedgerRepository
import com.myfinances.app.integrations.ExternalProviderConnector
import com.myfinances.app.integrations.cajaingenieros.sync.CajaIngenierosBrowserSyncService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock

data class ProviderConnectionUiState(
    val draftFields: Map<String, String> = emptyMap(),
    val preview: ExternalConnectionPreview? = null,
    val isTesting: Boolean = false,
    val isConnecting: Boolean = false,
    val isSyncing: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

data class SettingsUiState(
    val connections: List<ExternalConnection> = emptyList(),
    val selectedConnectionId: String? = null,
    val accountLinksByConnection: Map<String, List<ExternalAccountLink>> = emptyMap(),
    val syncRunsByConnection: Map<String, List<ExternalSyncRun>> = emptyMap(),
    val providerStates: Map<ExternalProviderId, ProviderConnectionUiState> = defaultProviderStates(),
    val categories: List<Category> = emptyList(),
    val disconnectConfirmationConnectionId: String? = null,
    val pendingDisconnectConnectionId: String? = null,
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

    val selectedConnection: ExternalConnection?
        get() = connections.firstOrNull { connection -> connection.id == selectedConnectionId }

    val selectedConnectionAccountLinks: List<ExternalAccountLink>
        get() = selectedConnection
            ?.let { connection -> accountLinksByConnection[connection.id].orEmpty() }
            .orEmpty()

    val selectedConnectionSyncRuns: List<ExternalSyncRun>
        get() = selectedConnection
            ?.let { connection -> syncRunsByConnection[connection.id].orEmpty() }
            .orEmpty()

    val isEditing: Boolean
        get() = editingCategoryId != null

    val deleteConfirmationCategoryName: String?
        get() = categories.firstOrNull { category ->
            category.id == deleteConfirmationCategoryId
        }?.name

    val disconnectConfirmationConnectionName: String?
        get() = connections.firstOrNull { connection ->
            connection.id == disconnectConfirmationConnectionId
        }?.displayName

    val isBusy: Boolean
        get() = isSaving || pendingDeleteCategoryId != null
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val ledgerRepository: LedgerRepository,
    private val externalConnectionsRepository: ExternalConnectionsRepository,
    private val providerConnectors: Map<ExternalProviderId, ExternalProviderConnector>,
    private val cajaIngenierosBrowserSyncService: CajaIngenierosBrowserSyncService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        val connectionsFlow = externalConnectionsRepository.observeConnections()
        val accountLinksByConnectionFlow = connectionsFlow.flatMapLatest { connections ->
            if (connections.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    connections.map { connection ->
                        externalConnectionsRepository.observeAccountLinks(connection.id)
                    },
                ) { accountLinkLists ->
                    connections.zip(accountLinkLists).associate { (connection, links) ->
                        connection.id to links.sortedBy(ExternalAccountLink::accountDisplayName)
                    }
                }
            }
        }
        val syncRunsByConnectionFlow = connectionsFlow.flatMapLatest { connections ->
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
        }

        viewModelScope.launch {
            combine(
                ledgerRepository.observeCategories(),
                connectionsFlow,
                accountLinksByConnectionFlow,
                syncRunsByConnectionFlow,
            ) { categories, connections, accountLinksByConnection, syncRunsByConnection ->
                SettingsCombinedState(
                    categories = categories,
                    connections = connections,
                    accountLinksByConnection = accountLinksByConnection,
                    syncRunsByConnection = syncRunsByConnection,
                )
            }.collect { combinedState ->
                _uiState.update { currentState ->
                    val connections = combinedState.connections
                    currentState.copy(
                        connections = connections,
                        selectedConnectionId = currentState.selectedConnectionId
                            ?.takeIf { selectedId ->
                                connections.any { connection -> connection.id == selectedId }
                            }
                            ?: connections.firstOrNull()?.id,
                        accountLinksByConnection = combinedState.accountLinksByConnection,
                        syncRunsByConnection = combinedState.syncRunsByConnection,
                        providerStates = ensureAllProviderStates(currentState.providerStates),
                        categories = combinedState.categories,
                        editingCategoryId = currentState.editingCategoryId
                            ?.takeIf { editingId ->
                                combinedState.categories.any { category -> category.id == editingId }
                            },
                        deleteConfirmationCategoryId = currentState.deleteConfirmationCategoryId
                            ?.takeIf { categoryId ->
                                combinedState.categories.any { category -> category.id == categoryId }
                            },
                        pendingDeleteCategoryId = currentState.pendingDeleteCategoryId
                            ?.takeIf { categoryId ->
                                combinedState.categories.any { category -> category.id == categoryId }
                            },
                        disconnectConfirmationConnectionId = currentState.disconnectConfirmationConnectionId
                            ?.takeIf { connectionId ->
                                connections.any { connection -> connection.id == connectionId }
                            },
                        pendingDisconnectConnectionId = currentState.pendingDisconnectConnectionId
                            ?.takeIf { connectionId ->
                                connections.any { connection -> connection.id == connectionId }
                            },
                    )
                }
            }
        }
    }

    fun selectConnection(connectionId: String) {
        _uiState.update { currentState ->
            currentState.copy(selectedConnectionId = connectionId)
        }
    }

    fun onProviderFieldChange(
        providerId: ExternalProviderId,
        fieldId: String,
        value: String,
    ) {
        _uiState.update { currentState ->
            currentState.copy(
                providerStates = currentState.providerStates.updated(
                    providerId = providerId,
                ) { providerState ->
                    providerState.copy(
                        draftFields = providerState.draftFields + (fieldId to value),
                        preview = null,
                        message = null,
                        error = null,
                    )
                },
            )
        }
    }

    fun testProviderConnection(providerId: ExternalProviderId) {
        val connector = providerConnectors[providerId] ?: return
        val providerName = providerDisplayName(providerId)
        val credentials = uiState.value.providerState(providerId).draftFields
        if (!providerCredentialsReady(providerId, credentials)) {
            setProviderError(providerId, "Complete the required $providerName credential fields first.")
            return
        }

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    providerStates = currentState.providerStates.updated(providerId) { providerState ->
                        providerState.copy(
                            isTesting = true,
                            message = null,
                            error = null,
                        )
                    },
                )
            }

            runCatching {
                connector.testConnection(credentials)
            }.onSuccess { preview ->
                _uiState.update { currentState ->
                    currentState.copy(
                        providerStates = currentState.providerStates.updated(providerId) { providerState ->
                            providerState.copy(
                                isTesting = false,
                                preview = preview,
                                message = "Connection test succeeded. Review the discovered accounts and save the connection when ready.",
                                error = null,
                            )
                        },
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { currentState ->
                    currentState.copy(
                        providerStates = currentState.providerStates.updated(providerId) { providerState ->
                            providerState.copy(
                                isTesting = false,
                                preview = null,
                                message = null,
                                error = throwable.message ?: "$providerName connection test failed.",
                            )
                        },
                    )
                }
            }
        }
    }

    fun connectProvider(providerId: ExternalProviderId) {
        val connector = providerConnectors[providerId] ?: return
        val providerName = providerDisplayName(providerId)
        val credentials = uiState.value.providerState(providerId).draftFields
        if (!providerCredentialsReady(providerId, credentials)) {
            setProviderError(providerId, "Complete the required $providerName credential fields first.")
            return
        }

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    providerStates = currentState.providerStates.updated(providerId) { providerState ->
                        providerState.copy(
                            isConnecting = true,
                            message = null,
                            error = null,
                        )
                    },
                )
            }

            runCatching {
                connector.connect(credentials)
            }.onSuccess { connection ->
                _uiState.update { currentState ->
                    currentState.copy(
                        selectedConnectionId = connection.id,
                        providerStates = currentState.providerStates.updated(providerId) { providerState ->
                            providerState.copy(
                                draftFields = emptyMap(),
                                preview = null,
                                isConnecting = false,
                                message = "Saved ${connection.displayName}. Run Sync now to import the discovered provider accounts into the local ledger.",
                                error = null,
                            )
                        },
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { currentState ->
                    currentState.copy(
                        providerStates = currentState.providerStates.updated(providerId) { providerState ->
                            providerState.copy(
                                isConnecting = false,
                                message = null,
                                error = throwable.message ?: "$providerName connection setup failed.",
                            )
                        },
                    )
                }
            }
        }
    }

    fun runProviderSync(providerId: ExternalProviderId) {
        val connector = providerConnectors[providerId] ?: return
        val providerName = providerDisplayName(providerId)
        val connectionId = uiState.value.selectedConnection
            ?.takeIf { connection -> connection.providerId == providerId }
            ?.id
            ?: uiState.value.connections.firstOrNull { connection ->
                connection.providerId == providerId
            }?.id

        if (connectionId == null) {
            setProviderError(providerId, "Save the $providerName connection before running sync.")
            return
        }

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    providerStates = currentState.providerStates.updated(providerId) { providerState ->
                        providerState.copy(
                            isSyncing = true,
                            message = null,
                            error = null,
                        )
                    },
                )
            }

            runCatching {
                connector.runSync(connectionId)
            }.onSuccess { syncRun ->
                _uiState.update { currentState ->
                    currentState.copy(
                        providerStates = currentState.providerStates.updated(providerId) { providerState ->
                            providerState.copy(
                                isSyncing = false,
                                message = syncRun.message ?: "$providerName sync completed successfully.",
                                error = null,
                            )
                        },
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { currentState ->
                    currentState.copy(
                        providerStates = currentState.providerStates.updated(providerId) { providerState ->
                            providerState.copy(
                                isSyncing = false,
                                message = null,
                                error = throwable.message ?: "$providerName sync failed.",
                            )
                        },
                    )
                }
            }
        }
    }

    fun runCajaIngenierosBrowserSync() {
        val providerId = ExternalProviderId.CAJA_INGENIEROS
        if (!cajaIngenierosBrowserSyncService.isSupported) {
            setProviderError(providerId, "Browser-assisted Caja Ingenieros sync is only available on desktop right now.")
            return
        }

        viewModelScope.launch {
            val connectionId = ensureCajaIngenierosBrowserConnection()

            _uiState.update { currentState ->
                currentState.copy(
                    selectedConnectionId = connectionId,
                    providerStates = currentState.providerStates.updated(providerId) { providerState ->
                        providerState.copy(
                            isSyncing = true,
                            message = "Browser sync started. myFinances opened your default browser. Log in to Caja Ingenieros, navigate to your statement or movements page, and download a PDF into your normal Downloads folder. myFinances will import the first new PDF automatically.",
                            error = null,
                        )
                    },
                )
            }

            runCatching {
                cajaIngenierosBrowserSyncService.runAssistedStatementSync(connectionId)
            }.onSuccess { syncRun ->
                _uiState.update { currentState ->
                    currentState.copy(
                        providerStates = currentState.providerStates.updated(providerId) { providerState ->
                            providerState.copy(
                                isSyncing = false,
                                message = syncRun?.message
                                    ?: "Browser sync was canceled before a Caja Ingenieros PDF statement was downloaded.",
                                error = null,
                            )
                        },
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { currentState ->
                    currentState.copy(
                        providerStates = currentState.providerStates.updated(providerId) { providerState ->
                            providerState.copy(
                                isSyncing = false,
                                message = null,
                                error = throwable.message ?: "Caja Ingenieros browser-assisted sync failed.",
                            )
                        },
                    )
                }
            }
        }
    }

    private suspend fun ensureCajaIngenierosBrowserConnection(): String {
        val existingConnection = externalConnectionsRepository
            .observeConnections()
            .first()
            .firstOrNull { connection ->
                connection.providerId == ExternalProviderId.CAJA_INGENIEROS
            }
        if (existingConnection != null) {
            return existingConnection.id
        }

        val now = Clock.System.now().toEpochMilliseconds()
        val connection = ExternalConnection(
            id = "conn-caja-browser-$now-${Random.nextInt(1000, 9999)}",
            providerId = ExternalProviderId.CAJA_INGENIEROS,
            displayName = "Caja Ingenieros (Browser sync)",
            status = ExternalConnectionStatus.CONNECTED,
            externalUserId = null,
            lastSuccessfulSyncEpochMs = null,
            lastSyncAttemptEpochMs = null,
            lastSyncStatus = ExternalSyncStatus.IDLE,
            lastErrorMessage = null,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        externalConnectionsRepository.upsertConnection(connection)
        return connection.id
    }

    fun requestDisconnectConnection(connectionId: String) {
        _uiState.update { currentState ->
            val connection = currentState.connections.firstOrNull { item -> item.id == connectionId }
                ?: return@update currentState

            currentState.copy(
                selectedConnectionId = connection.id,
                disconnectConfirmationConnectionId = connection.id,
            )
        }
    }

    fun dismissDisconnectDialog() {
        _uiState.update { currentState ->
            currentState.copy(disconnectConfirmationConnectionId = null)
        }
    }

    fun confirmDisconnectConnection() {
        val snapshot = uiState.value
        val connectionId = snapshot.disconnectConfirmationConnectionId ?: return
        val connection = snapshot.connections.firstOrNull { item -> item.id == connectionId } ?: return
        val connector = providerConnectors[connection.providerId]

        if (connector == null) {
            setProviderError(
                providerId = connection.providerId,
                error = "Disconnect is not implemented for this provider yet.",
                clearDisconnectDialog = true,
            )
            return
        }

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    disconnectConfirmationConnectionId = null,
                    pendingDisconnectConnectionId = connectionId,
                    providerStates = currentState.providerStates.updated(connection.providerId) { providerState ->
                        providerState.copy(
                            message = null,
                            error = null,
                        )
                    },
                )
            }

            runCatching {
                connector.disconnect(connectionId)
            }.onSuccess {
                _uiState.update { currentState ->
                    currentState.copy(
                        pendingDisconnectConnectionId = null,
                        selectedConnectionId = currentState.connections
                            .firstOrNull { item -> item.id != connectionId }
                            ?.id,
                        providerStates = currentState.providerStates.updated(connection.providerId) { providerState ->
                            providerState.copy(
                                message = "Disconnected ${connection.displayName}. Imported local accounts and transactions were kept in the ledger.",
                                error = null,
                            )
                        },
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { currentState ->
                    currentState.copy(
                        pendingDisconnectConnectionId = null,
                        providerStates = currentState.providerStates.updated(connection.providerId) { providerState ->
                            providerState.copy(
                                message = null,
                                error = throwable.message ?: "Disconnect failed.",
                            )
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
                currentState.toCreateMode().copy(isSaving = false)
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

    private fun setProviderError(
        providerId: ExternalProviderId,
        error: String,
        clearDisconnectDialog: Boolean = false,
    ) {
        _uiState.update { currentState ->
            currentState.copy(
                disconnectConfirmationConnectionId = if (clearDisconnectDialog) null else currentState.disconnectConfirmationConnectionId,
                providerStates = currentState.providerStates.updated(providerId) { providerState ->
                    providerState.copy(
                        message = null,
                        error = error,
                    )
                },
            )
        }
    }
}

private data class SettingsCombinedState(
    val categories: List<Category>,
    val connections: List<ExternalConnection>,
    val accountLinksByConnection: Map<String, List<ExternalAccountLink>>,
    val syncRunsByConnection: Map<String, List<ExternalSyncRun>>,
)

private fun SettingsUiState.toCreateMode(): SettingsUiState =
    copy(
        draftName = "",
        editingCategoryId = null,
        deleteConfirmationCategoryId = null,
        isSaving = false,
        pendingDeleteCategoryId = null,
        errorMessage = null,
    )

private fun defaultProviderStates(): Map<ExternalProviderId, ProviderConnectionUiState> =
    ExternalProviderCatalog.availableProviders.associate { provider ->
        provider.id to ProviderConnectionUiState()
    }

private fun ensureAllProviderStates(
    currentStates: Map<ExternalProviderId, ProviderConnectionUiState>,
): Map<ExternalProviderId, ProviderConnectionUiState> =
    defaultProviderStates() + currentStates

private fun Map<ExternalProviderId, ProviderConnectionUiState>.updated(
    providerId: ExternalProviderId,
    update: (ProviderConnectionUiState) -> ProviderConnectionUiState,
): Map<ExternalProviderId, ProviderConnectionUiState> =
    this + (providerId to update(this[providerId] ?: ProviderConnectionUiState()))

internal fun SettingsUiState.providerState(
    providerId: ExternalProviderId,
): ProviderConnectionUiState = providerStates[providerId] ?: ProviderConnectionUiState()

internal fun ProviderConnectionUiState.fieldValue(fieldId: String): String =
    draftFields[fieldId].orEmpty()

private fun providerDisplayName(providerId: ExternalProviderId): String =
    ExternalProviderCatalog.availableProviders.firstOrNull { provider ->
        provider.id == providerId
    }?.displayName ?: providerId.name.lowercase().replaceFirstChar(Char::uppercase)

private fun providerCredentialsReady(
    providerId: ExternalProviderId,
    credentials: Map<String, String>,
): Boolean {
    val provider = ExternalProviderCatalog.availableProviders.firstOrNull { definition ->
        definition.id == providerId
    } ?: return false

    return provider.credentialFields
        .filter { field -> field.required }
        .all { field -> credentials[field.id]?.trim().isNullOrEmpty().not() }
}

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
