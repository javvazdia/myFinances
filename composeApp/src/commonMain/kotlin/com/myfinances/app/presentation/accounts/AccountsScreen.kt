package com.myfinances.app.presentation.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myfinances.app.domain.model.AccountType

@Composable
fun AccountsScreen(
    uiState: AccountsUiState,
    onShowCreateForm: () -> Unit,
    onHideAccountForm: () -> Unit,
    onNameChange: (String) -> Unit,
    onTypeSelected: (AccountType) -> Unit,
    onCurrencyCodeChange: (String) -> Unit,
    onOpeningBalanceChange: (String) -> Unit,
    onSaveAccount: () -> Unit,
    onSelectAccount: (String) -> Unit,
    onSelectAccountHistoryMode: (AccountHistoryMode) -> Unit,
    onSelectAccountHistoryRange: (AccountHistoryRange) -> Unit,
    onApplyCustomAccountHistoryRange: (Long, Long) -> Unit,
    onCloseAccountDetails: () -> Unit,
    onEditAccount: (String) -> Unit,
    onRequestDeleteAccount: (String) -> Unit,
    onConfirmDeleteAccount: () -> Unit,
    onDismissDeleteDialog: () -> Unit,
) {
    val selectedAccount = uiState.selectedAccount
    if (selectedAccount != null) {
        AccountDetailScreen(
            account = selectedAccount,
            currentBalanceMinor = uiState.selectedAccountCurrentBalanceMinor
                ?: selectedAccount.openingBalanceMinor,
            historyChart = uiState.selectedAccountHistoryChart,
            availableHistoryModes = uiState.availableAccountHistoryModes,
            selectedHistoryMode = uiState.selectedAccountHistoryMode,
            selectedHistoryRange = uiState.selectedAccountHistoryRange,
            customHistoryStartEpochMs = uiState.customAccountHistoryStartEpochMs,
            customHistoryEndEpochMs = uiState.customAccountHistoryEndEpochMs,
            isLoadingHistory = uiState.isLoadingAccountHistory,
            historyErrorMessage = uiState.accountHistoryErrorMessage,
            positions = uiState.selectedInvestmentPositions,
            onSelectHistoryMode = onSelectAccountHistoryMode,
            onSelectHistoryRange = onSelectAccountHistoryRange,
            onApplyCustomHistoryRange = onApplyCustomAccountHistoryRange,
            onBack = onCloseAccountDetails,
            onEditAccount = onEditAccount,
            onRequestDeleteAccount = onRequestDeleteAccount,
            canInteract = !uiState.isBusy,
            isDeleting = uiState.pendingDeleteAccountId == selectedAccount.id,
        )
    } else {
        val listState = rememberLazyListState()

        LaunchedEffect(uiState.editingAccountId) {
            if (uiState.editingAccountId != null) {
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
                    text = "Accounts",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            item {
                Text(
                    text = "Accounts can now be created on demand, edited in place, and opened into a detail view with holdings for synced investment providers.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                AccountActionsCard(
                    uiState = uiState,
                    onShowCreateForm = onShowCreateForm,
                    onHideAccountForm = onHideAccountForm,
                )
            }

            if (uiState.isFormVisible) {
                item {
                    AccountFormCard(
                        uiState = uiState,
                        onNameChange = onNameChange,
                        onTypeSelected = onTypeSelected,
                        onCurrencyCodeChange = onCurrencyCodeChange,
                        onOpeningBalanceChange = onOpeningBalanceChange,
                        onSaveAccount = onSaveAccount,
                        onHideAccountForm = onHideAccountForm,
                    )
                }
            }

            item {
                Text(
                    text = "Current accounts",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (uiState.accounts.isEmpty()) {
                item {
                    Card {
                        Text(
                            text = "No accounts yet. Click Create account to add your first one.",
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                items(
                    items = uiState.accounts,
                    key = { account -> account.id },
                ) { account ->
                    AccountCard(
                        account = account,
                        currentBalanceMinor = uiState.currentBalanceMinorFor(account.id),
                        onSelectAccount = onSelectAccount,
                        onEditAccount = onEditAccount,
                        onRequestDeleteAccount = onRequestDeleteAccount,
                        canInteract = !uiState.isBusy,
                        isEditing = uiState.editingAccountId == account.id,
                        isDeleting = uiState.pendingDeleteAccountId == account.id,
                    )
                }
            }
        }
    }

    if (uiState.deleteConfirmationAccountId != null) {
        AlertDialog(
            onDismissRequest = onDismissDeleteDialog,
            title = {
                Text("Delete account?")
            },
            text = {
                Text(
                    "Delete ${uiState.deleteConfirmationAccountName ?: "this account"}? Any transactions and holdings linked to it will also be removed from the local ledger.",
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDeleteAccount,
                    enabled = !uiState.isBusy,
                ) {
                    Text(if (uiState.pendingDeleteAccountId != null) "Deleting..." else "Delete")
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
