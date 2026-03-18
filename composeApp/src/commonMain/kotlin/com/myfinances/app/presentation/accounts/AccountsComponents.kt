package com.myfinances.app.presentation.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.AccountSourceType
import com.myfinances.app.domain.model.AccountType
import com.myfinances.app.domain.model.InvestmentPosition

@Composable
internal fun AccountActionsCard(
    uiState: AccountsUiState,
    onShowCreateForm: () -> Unit,
    onHideAccountForm: () -> Unit,
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!uiState.isFormVisible) {
                Button(
                    onClick = onShowCreateForm,
                    enabled = !uiState.isBusy,
                ) {
                    Text("Create account")
                }
            } else {
                Button(
                    onClick = onHideAccountForm,
                    enabled = !uiState.isBusy,
                ) {
                    Text(if (uiState.isEditing) "Cancel editing" else "Hide form")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountFormCard(
    uiState: AccountsUiState,
    onNameChange: (String) -> Unit,
    onTypeSelected: (AccountType) -> Unit,
    onCurrencyCodeChange: (String) -> Unit,
    onOpeningBalanceChange: (String) -> Unit,
    onSaveAccount: () -> Unit,
    onHideAccountForm: () -> Unit,
) {
    var typeMenuExpanded by remember { mutableStateOf(false) }
    val nameFocusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.editingAccountId, uiState.isFormVisible) {
        if (uiState.isFormVisible) {
            nameFocusRequester.requestFocus()
        }
    }

    Card(
        colors = if (uiState.isEditing) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (uiState.isEditing) "Edit account" else "Create account",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = when {
                    uiState.isEditing && uiState.editingAccount?.sourceType == AccountSourceType.API_SYNC ->
                        "You can update the local presentation of this synced account, but a future provider sync may overwrite some fields like name or valuation."
                    uiState.isEditing ->
                        "You are updating this account in place, so existing transactions and holdings keep pointing to the same local account id."
                    else ->
                        "This form creates a local account. For synced providers, imported accounts will also land in this same ledger."
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
                label = { Text("Account name") },
                singleLine = true,
                enabled = !uiState.isBusy,
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
                    label = { Text("Account type") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded)
                    },
                )
                ExposedDropdownMenu(
                    expanded = typeMenuExpanded,
                    onDismissRequest = { typeMenuExpanded = false },
                ) {
                    AccountType.entries.forEach { type ->
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

            OutlinedTextField(
                value = uiState.draftCurrencyCode,
                onValueChange = onCurrencyCodeChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Currency code") },
                supportingText = { Text("Use a 3-letter code like EUR or USD.") },
                singleLine = true,
                enabled = !uiState.isBusy,
            )

            OutlinedTextField(
                value = uiState.draftOpeningBalance,
                onValueChange = onOpeningBalanceChange,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(if (uiState.editingAccount?.sourceType == AccountSourceType.API_SYNC) "Current synced value" else "Opening balance")
                },
                supportingText = {
                    Text("Examples: 1200, 1200.50, or -250.00")
                },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                singleLine = true,
                enabled = !uiState.isBusy,
            )

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onSaveAccount,
                    enabled = !uiState.isBusy,
                ) {
                    Text(
                        when {
                            uiState.isSaving -> "Saving..."
                            uiState.isEditing -> "Save changes"
                            else -> "Create account"
                        },
                    )
                }

                TextButton(
                    onClick = onHideAccountForm,
                    enabled = !uiState.isBusy,
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
internal fun AccountCard(
    account: Account,
    currentBalanceMinor: Long,
    onSelectAccount: (String) -> Unit,
    onEditAccount: (String) -> Unit,
    onRequestDeleteAccount: (String) -> Unit,
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.fillMaxWidth(0.7f)) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = account.type.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatMoney(currentBalanceMinor, account.currencyCode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when (account.sourceType) {
                    AccountSourceType.MANUAL -> "Source: Manual"
                    AccountSourceType.API_SYNC ->
                        "Source: Synced from ${account.sourceProvider ?: "external provider"}"
                    AccountSourceType.FILE_IMPORT ->
                        "Source: Imported from ${account.sourceProvider ?: "file"}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onSelectAccount(account.id) },
                    enabled = canInteract,
                ) {
                    Text("View details")
                }
                TextButton(
                    onClick = { onEditAccount(account.id) },
                    enabled = canInteract,
                ) {
                    Text(if (isEditing) "Editing" else "Edit")
                }
                TextButton(
                    onClick = { onRequestDeleteAccount(account.id) },
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

@Composable
internal fun AccountDetailScreen(
    account: Account,
    currentBalanceMinor: Long,
    positions: List<InvestmentPosition>,
    onBack: () -> Unit,
    onEditAccount: (String) -> Unit,
    onRequestDeleteAccount: (String) -> Unit,
    canInteract: Boolean,
    isDeleting: Boolean,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TextButton(onClick = onBack) {
                Text("Back to accounts")
            }
        }

        item {
            Text(
                text = "Account details",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        item {
            AccountSummaryCard(
                account = account,
                currentBalanceMinor = currentBalanceMinor,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    onClick = { onEditAccount(account.id) },
                    enabled = canInteract,
                ) {
                    Text("Edit account")
                }
                TextButton(
                    onClick = { onRequestDeleteAccount(account.id) },
                    enabled = canInteract,
                ) {
                    Text(
                        text = if (isDeleting) "Deleting..." else "Delete account",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        item {
            Text(
                text = if (account.type == AccountType.INVESTMENT) "Portfolio holdings" else "Holdings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        when {
            account.type != AccountType.INVESTMENT -> {
                item {
                    EmptyDetailCard(
                        message = "This account is not an investment account, so there are no portfolio holdings to show here.",
                    )
                }
            }
            account.sourceType != AccountSourceType.API_SYNC -> {
                item {
                    EmptyDetailCard(
                        message = "This investment account is local-only for now. Synced providers like Indexa will surface holdings here after import.",
                    )
                }
            }
            positions.isEmpty() -> {
                item {
                    EmptyDetailCard(
                        message = "No holdings have been imported for this account yet. Run sync again to refresh the latest portfolio positions.",
                    )
                }
            }
            else -> {
                items(
                    items = positions,
                    key = { position -> position.id },
                ) { position ->
                    InvestmentPositionCard(
                        position = position,
                        currencyCode = account.currencyCode,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountSummaryCard(
    account: Account,
    currentBalanceMinor: Long,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = account.type.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatMoney(currentBalanceMinor, account.currencyCode),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (account.sourceType == AccountSourceType.API_SYNC && account.type == AccountType.INVESTMENT) {
                    "Current synced portfolio value"
                } else {
                    "Current balance from opening balance plus tracked transactions"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = when (account.sourceType) {
                    AccountSourceType.MANUAL -> "Source: Manual"
                    AccountSourceType.API_SYNC -> "Source: Synced from ${account.sourceProvider ?: "external provider"}"
                    AccountSourceType.FILE_IMPORT -> "Source: Imported from ${account.sourceProvider ?: "file"}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Last sync: ${formatSyncTimestamp(account.lastSyncedAtEpochMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (account.externalAccountId != null) {
                Text(
                    text = "Provider account: ${account.externalAccountId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InvestmentPositionCard(
    position: InvestmentPosition,
    currencyCode: String,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = position.instrumentName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = buildPositionSubtitle(position),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = buildPositionValuationLabel(position, currencyCode),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (position.valuationDate != null) {
                Text(
                    text = "Valuation date: ${position.valuationDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyDetailCard(message: String) {
    Card {
        Text(
            text = message,
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
