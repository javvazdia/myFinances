package com.myfinances.app.presentation.transactions

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
import com.myfinances.app.domain.model.TransactionType

@Composable
fun TransactionsScreen(
    uiState: TransactionsUiState,
    onShowCreateForm: () -> Unit,
    onHideTransactionForm: () -> Unit,
    onTypeSelected: (TransactionType) -> Unit,
    onAccountSelected: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onMerchantChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSaveTransaction: () -> Unit,
    onShowTransactionDetails: (String) -> Unit,
    onDismissTransactionDetails: () -> Unit,
    onEditTransaction: (String) -> Unit,
    onRequestDeleteTransaction: (String) -> Unit,
    onConfirmDeleteTransaction: () -> Unit,
    onDismissDeleteDialog: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.editingTransactionId) {
        if (uiState.editingTransactionId != null) {
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
                text = "Transactions",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        item {
            Text(
                text = "This first transaction flow is manual and local-first. Later, imported bank transactions can land in the same ledger with provider metadata and reconciliation rules.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            TransactionActionsCard(
                uiState = uiState,
                onShowCreateForm = onShowCreateForm,
                onHideTransactionForm = onHideTransactionForm,
            )
        }

        if (uiState.isFormVisible) {
            item {
                TransactionFormCard(
                    uiState = uiState,
                    onTypeSelected = onTypeSelected,
                    onAccountSelected = onAccountSelected,
                    onCategorySelected = onCategorySelected,
                    onAmountChange = onAmountChange,
                    onMerchantChange = onMerchantChange,
                    onNoteChange = onNoteChange,
                    onSaveTransaction = onSaveTransaction,
                    onCancelEditing = onHideTransactionForm,
                )
            }
        }

        item {
            Text(
                text = "Recent transactions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (uiState.recentTransactions.isEmpty()) {
            item {
                Card {
                    Text(
                        text = "No transactions yet. Click Create transaction to add one.",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            items(uiState.recentTransactions) { transaction ->
                TransactionCard(
                    transaction = transaction,
                    onShowDetails = onShowTransactionDetails,
                    onEditTransaction = onEditTransaction,
                    onRequestDeleteTransaction = onRequestDeleteTransaction,
                    canInteract = !uiState.isBusy,
                    isEditing = uiState.editingTransactionId == transaction.id,
                    isDeleting = uiState.pendingDeleteTransactionId == transaction.id,
                )
            }
        }
    }

    uiState.selectedTransactionDetail?.let { transaction ->
        AlertDialog(
            onDismissRequest = onDismissTransactionDetails,
            title = {
                Text("Transaction details")
            },
            text = {
                TransactionDetailsCard(transaction = transaction)
            },
            confirmButton = {
                TextButton(
                    onClick = onDismissTransactionDetails,
                ) {
                    Text("Close")
                }
            },
        )
    }

    if (uiState.deleteConfirmationTransactionId != null) {
        AlertDialog(
            onDismissRequest = onDismissDeleteDialog,
            title = {
                Text("Delete transaction?")
            },
            text = {
                Text(
                    "Delete ${uiState.deleteConfirmationTransactionTitle ?: "this transaction"}? This action cannot be undone.",
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDeleteTransaction,
                    enabled = !uiState.isBusy,
                ) {
                    Text(if (uiState.pendingDeleteTransactionId != null) "Deleting..." else "Delete")
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
