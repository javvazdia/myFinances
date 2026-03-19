package com.myfinances.app.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.repository.ExternalConnectionsRepository
import com.myfinances.app.domain.repository.LedgerRepository
import com.myfinances.app.integrations.ExternalProviderConnector

@Composable
fun SettingsRoute(
    ledgerRepository: LedgerRepository,
    externalConnectionsRepository: ExternalConnectionsRepository,
    providerConnectors: Map<ExternalProviderId, ExternalProviderConnector>,
    settingsViewModel: SettingsViewModel = viewModel {
        SettingsViewModel(
            ledgerRepository = ledgerRepository,
            externalConnectionsRepository = externalConnectionsRepository,
            providerConnectors = providerConnectors,
        )
    },
) {
    val uiState by settingsViewModel.uiState.collectAsState()
    SettingsScreen(
        uiState = uiState,
        onSelectConnection = settingsViewModel::selectConnection,
        onProviderSecretChange = settingsViewModel::onProviderSecretChange,
        onTestProviderConnection = settingsViewModel::testProviderConnection,
        onConnectProvider = settingsViewModel::connectProvider,
        onRunProviderSync = settingsViewModel::runProviderSync,
        onRequestDisconnectConnection = settingsViewModel::requestDisconnectConnection,
        onConfirmDisconnectConnection = settingsViewModel::confirmDisconnectConnection,
        onDismissDisconnectDialog = settingsViewModel::dismissDisconnectDialog,
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
