package com.myfinances.app.presentation.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myfinances.app.domain.model.TransactionType

@Composable
internal fun TransactionActionsCard(
    uiState: TransactionsUiState,
    onShowCreateForm: () -> Unit,
    onHideTransactionForm: () -> Unit,
    onImportCajaIngenierosPdf: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!uiState.isFormVisible) {
                    Button(
                        onClick = onShowCreateForm,
                        enabled = !uiState.isBusy,
                    ) {
                        Text("Create transaction")
                    }
                } else {
                    Button(
                        onClick = onHideTransactionForm,
                        enabled = !uiState.isBusy,
                    ) {
                        Text(if (uiState.isEditing) "Cancel editing" else "Hide form")
                    }
                }

                if (uiState.isStatementImportSupported) {
                    Button(
                        onClick = onImportCajaIngenierosPdf,
                        enabled = !uiState.isBusy && !uiState.isImportingStatement,
                    ) {
                        Text(if (uiState.isImportingStatement) "Importing..." else "Import Caja PDF")
                    }
                }
            }

            uiState.importMessage?.let { importMessage ->
                Text(
                    text = importMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionFormCard(
    uiState: TransactionsUiState,
    onTypeSelected: (TransactionType) -> Unit,
    onAccountSelected: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onMerchantChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSaveTransaction: () -> Unit,
    onCancelEditing: () -> Unit,
) {
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    val amountFocusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.editingTransactionId) {
        if (uiState.editingTransactionId != null) {
            amountFocusRequester.requestFocus()
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
                text = if (uiState.isEditing) "Edit transaction" else "Create transaction",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = if (uiState.isEditing) {
                    "You are updating a manual transaction. This keeps the same local record stable for future sync and reconciliation work."
                } else {
                    "Create a manual transaction now. Later, synced transactions from external providers can flow through this same ledger."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ExposedDropdownMenuBox(
                expanded = typeMenuExpanded,
                onExpandedChange = { typeMenuExpanded = !typeMenuExpanded },
            ) {
                OutlinedTextField(
                    value = uiState.selectedType.label,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    readOnly = true,
                    enabled = !uiState.isBusy,
                    label = { Text("Transaction type") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded)
                    },
                )
                ExposedDropdownMenu(
                    expanded = typeMenuExpanded,
                    onDismissRequest = { typeMenuExpanded = false },
                ) {
                    manualTransactionTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label) },
                            onClick = {
                                onTypeSelected(type)
                                typeMenuExpanded = false
                            },
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = accountMenuExpanded,
                onExpandedChange = { accountMenuExpanded = !accountMenuExpanded },
            ) {
                OutlinedTextField(
                    value = uiState.selectedAccountName ?: "Select account",
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    readOnly = true,
                    enabled = !uiState.isBusy && uiState.accounts.isNotEmpty(),
                    label = { Text("Account") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuExpanded)
                    },
                )
                ExposedDropdownMenu(
                    expanded = accountMenuExpanded,
                    onDismissRequest = { accountMenuExpanded = false },
                ) {
                    uiState.accounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text(account.name) },
                            onClick = {
                                onAccountSelected(account.id)
                                accountMenuExpanded = false
                            },
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = categoryMenuExpanded,
                onExpandedChange = { categoryMenuExpanded = !categoryMenuExpanded },
            ) {
                OutlinedTextField(
                    value = uiState.selectedCategoryName ?: "Select category",
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    readOnly = true,
                    enabled = !uiState.isBusy && uiState.availableCategories.isNotEmpty(),
                    label = { Text("Category") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded)
                    },
                )
                ExposedDropdownMenu(
                    expanded = categoryMenuExpanded,
                    onDismissRequest = { categoryMenuExpanded = false },
                ) {
                    uiState.availableCategories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                onCategorySelected(category.id)
                                categoryMenuExpanded = false
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = uiState.draftAmount,
                onValueChange = onAmountChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(amountFocusRequester),
                label = { Text("Amount") },
                supportingText = { Text("Examples: 18.75 or 1200.00") },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                singleLine = true,
                enabled = !uiState.isBusy,
            )

            OutlinedTextField(
                value = uiState.draftMerchant,
                onValueChange = onMerchantChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Merchant or source") },
                singleLine = true,
                enabled = !uiState.isBusy,
            )

            OutlinedTextField(
                value = uiState.draftNote,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note") },
                minLines = 2,
                enabled = !uiState.isBusy,
            )

            uiState.errorMessage?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onSaveTransaction,
                    enabled = !uiState.isBusy && uiState.accounts.isNotEmpty(),
                ) {
                    Text(
                        when {
                            uiState.isSaving -> "Saving..."
                            uiState.isEditing -> "Save changes"
                            else -> "Create transaction"
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
