package com.myfinances.app.presentation.accounts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myfinances.app.domain.model.AccountType
import com.myfinances.app.domain.repository.LedgerRepository
import com.myfinances.app.integrations.indexa.sync.IndexaIntegrationService

@Composable
fun AccountsRoute(
    ledgerRepository: LedgerRepository,
    indexaIntegrationService: IndexaIntegrationService,
    accountsViewModel: AccountsViewModel = viewModel {
        AccountsViewModel(
            ledgerRepository = ledgerRepository,
            indexaIntegrationService = indexaIntegrationService,
        )
    },
) {
    val uiState by accountsViewModel.uiState.collectAsState()
    AccountsScreen(
        uiState = uiState,
        onShowCreateForm = accountsViewModel::showCreateForm,
        onHideAccountForm = accountsViewModel::hideAccountForm,
        onNameChange = accountsViewModel::onNameChange,
        onTypeSelected = accountsViewModel::onTypeSelected,
        onCurrencyCodeChange = accountsViewModel::onCurrencyCodeChange,
        onOpeningBalanceChange = accountsViewModel::onOpeningBalanceChange,
        onSaveAccount = accountsViewModel::saveAccount,
        onSelectAccount = accountsViewModel::selectAccount,
        onSelectAccountHistoryMode = accountsViewModel::selectAccountHistoryMode,
        onSelectAccountHistoryRange = accountsViewModel::selectAccountHistoryRange,
        onApplyCustomAccountHistoryRange = accountsViewModel::applyCustomAccountHistoryRange,
        onCloseAccountDetails = accountsViewModel::closeAccountDetails,
        onEditAccount = accountsViewModel::editAccount,
        onRequestDeleteAccount = accountsViewModel::requestDeleteAccount,
        onConfirmDeleteAccount = accountsViewModel::confirmDeleteAccount,
        onDismissDeleteDialog = accountsViewModel::dismissDeleteDialog,
    )
}
