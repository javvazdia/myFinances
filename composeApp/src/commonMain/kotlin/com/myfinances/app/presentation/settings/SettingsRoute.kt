package com.myfinances.app.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myfinances.app.domain.model.CategoryKind
import com.myfinances.app.domain.repository.ExternalConnectionsRepository
import com.myfinances.app.domain.repository.LedgerRepository
import com.myfinances.app.integrations.indexa.sync.IndexaIntegrationService

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
