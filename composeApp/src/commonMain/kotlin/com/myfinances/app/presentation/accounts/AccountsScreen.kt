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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.AccountSourceType
import com.myfinances.app.domain.model.AccountType
import com.myfinances.app.domain.model.InvestmentPosition
import com.myfinances.app.domain.repository.LedgerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
fun AccountsRoute(
    ledgerRepository: LedgerRepository,
    accountsViewModel: AccountsViewModel = viewModel {
        AccountsViewModel(ledgerRepository)
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
        onCloseAccountDetails = accountsViewModel::closeAccountDetails,
        onEditAccount = accountsViewModel::editAccount,
        onRequestDeleteAccount = accountsViewModel::requestDeleteAccount,
        onConfirmDeleteAccount = accountsViewModel::confirmDeleteAccount,
        onDismissDeleteDialog = accountsViewModel::dismissDeleteDialog,
    )
}

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
            positions = uiState.selectedInvestmentPositions,
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

@Composable
private fun AccountActionsCard(
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
private fun AccountFormCard(
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
                        .menuAnchor(),
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
private fun AccountCard(
    account: Account,
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
                    text = formatMoney(account.openingBalanceMinor, account.currencyCode),
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
private fun AccountDetailScreen(
    account: Account,
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
            AccountSummaryCard(account = account)
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
private fun AccountSummaryCard(account: Account) {
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
                text = formatMoney(account.openingBalanceMinor, account.currencyCode),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
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

data class AccountsUiState(
    val accounts: List<Account> = emptyList(),
    val selectedAccountId: String? = null,
    val selectedInvestmentPositions: List<InvestmentPosition> = emptyList(),
    val draftName: String = "",
    val selectedType: AccountType = AccountType.CHECKING,
    val draftCurrencyCode: String = "EUR",
    val draftOpeningBalance: String = "",
    val isFormVisible: Boolean = false,
    val editingAccountId: String? = null,
    val deleteConfirmationAccountId: String? = null,
    val isSaving: Boolean = false,
    val pendingDeleteAccountId: String? = null,
    val errorMessage: String? = null,
) {
    val selectedAccount: Account?
        get() = accounts.firstOrNull { account -> account.id == selectedAccountId }

    val editingAccount: Account?
        get() = accounts.firstOrNull { account -> account.id == editingAccountId }

    val isEditing: Boolean
        get() = editingAccountId != null

    val deleteConfirmationAccountName: String?
        get() = accounts.firstOrNull { account ->
            account.id == deleteConfirmationAccountId
        }?.name

    val isBusy: Boolean
        get() = isSaving || pendingDeleteAccountId != null
}

class AccountsViewModel(
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()
    private var selectedPositionsJob: Job? = null

    init {
        viewModelScope.launch {
            ledgerRepository.observeAccounts().collect { accounts ->
                _uiState.update { currentState ->
                    currentState.copy(
                        accounts = accounts,
                        selectedAccountId = currentState.selectedAccountId
                            ?.takeIf { selectedId -> accounts.any { account -> account.id == selectedId } },
                        editingAccountId = currentState.editingAccountId
                            ?.takeIf { editingId -> accounts.any { account -> account.id == editingId } },
                        deleteConfirmationAccountId = currentState.deleteConfirmationAccountId
                            ?.takeIf { deleteId -> accounts.any { account -> account.id == deleteId } },
                        pendingDeleteAccountId = currentState.pendingDeleteAccountId
                            ?.takeIf { deletingId -> accounts.any { account -> account.id == deletingId } },
                    )
                }

                val selectedId = uiState.value.selectedAccountId
                if (selectedId != null && accounts.none { account -> account.id == selectedId }) {
                    selectedPositionsJob?.cancel()
                    _uiState.update { currentState ->
                        currentState.copy(
                            selectedAccountId = null,
                            selectedInvestmentPositions = emptyList(),
                        )
                    }
                }

                if (uiState.value.isEditing && uiState.value.editingAccount == null) {
                    hideAccountForm()
                }
            }
        }
    }

    fun showCreateForm() {
        _uiState.update { currentState ->
            currentState.toCreateMode().copy(
                isFormVisible = true,
                selectedAccountId = null,
                draftCurrencyCode = currentState.draftCurrencyCode,
            )
        }
    }

    fun hideAccountForm() {
        _uiState.update { currentState ->
            currentState.toCreateMode().copy(
                draftCurrencyCode = currentState.draftCurrencyCode,
            )
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

    fun onTypeSelected(value: AccountType) {
        _uiState.update { currentState ->
            currentState.copy(
                selectedType = value,
                errorMessage = null,
            )
        }
    }

    fun onCurrencyCodeChange(value: String) {
        _uiState.update { currentState ->
            currentState.copy(
                draftCurrencyCode = value,
                errorMessage = null,
            )
        }
    }

    fun onOpeningBalanceChange(value: String) {
        _uiState.update { currentState ->
            currentState.copy(
                draftOpeningBalance = value,
                errorMessage = null,
            )
        }
    }

    fun saveAccount() {
        val snapshot = uiState.value
        val existingAccount = snapshot.editingAccount
        val name = snapshot.draftName.trim()
        val currencyCode = normalizeCurrencyCode(snapshot.draftCurrencyCode)
        val openingBalanceMinor = parseAmountToMinor(snapshot.draftOpeningBalance)

        val error = when {
            name.isBlank() -> "Account name is required."
            currencyCode == null -> "Currency code must be 3 letters, like EUR or USD."
            openingBalanceMinor == null -> "Balance must be a valid number with up to 2 decimals."
            else -> null
        }

        if (error != null) {
            _uiState.update { currentState ->
                currentState.copy(errorMessage = error)
            }
            return
        }

        val validatedCurrencyCode = currencyCode ?: return
        val validatedOpeningBalanceMinor = openingBalanceMinor ?: return

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    isSaving = true,
                    errorMessage = null,
                )
            }

            val now = Clock.System.now().toEpochMilliseconds()
            val account = Account(
                id = existingAccount?.id ?: generateAccountId(name, now),
                name = name,
                type = snapshot.selectedType,
                currencyCode = validatedCurrencyCode,
                openingBalanceMinor = validatedOpeningBalanceMinor,
                sourceType = existingAccount?.sourceType ?: AccountSourceType.MANUAL,
                sourceProvider = existingAccount?.sourceProvider,
                externalAccountId = existingAccount?.externalAccountId,
                lastSyncedAtEpochMs = existingAccount?.lastSyncedAtEpochMs,
                isArchived = existingAccount?.isArchived ?: false,
                createdAtEpochMs = existingAccount?.createdAtEpochMs ?: now,
                updatedAtEpochMs = now,
            )

            ledgerRepository.upsertAccount(account)

            _uiState.update { currentState ->
                currentState.toCreateMode().copy(
                    draftCurrencyCode = validatedCurrencyCode,
                )
            }
        }
    }

    fun selectAccount(accountId: String) {
        _uiState.update { currentState ->
            currentState.toCreateMode().copy(
                selectedAccountId = accountId,
                draftCurrencyCode = currentState.draftCurrencyCode,
            )
        }

        selectedPositionsJob?.cancel()
        selectedPositionsJob = viewModelScope.launch {
            ledgerRepository.observeInvestmentPositions(accountId).collect { positions ->
                _uiState.update { currentState ->
                    if (currentState.selectedAccountId != accountId) {
                        currentState
                    } else {
                        currentState.copy(selectedInvestmentPositions = positions)
                    }
                }
            }
        }
    }

    fun closeAccountDetails() {
        selectedPositionsJob?.cancel()
        selectedPositionsJob = null
        _uiState.update { currentState ->
            currentState.copy(
                selectedAccountId = null,
                selectedInvestmentPositions = emptyList(),
            )
        }
    }

    fun editAccount(accountId: String) {
        closeAccountDetails()

        _uiState.update { currentState ->
            val account = currentState.accounts.firstOrNull { item -> item.id == accountId }
                ?: return@update currentState

            currentState.copy(
                draftName = account.name,
                selectedType = account.type,
                draftCurrencyCode = account.currencyCode,
                draftOpeningBalance = formatAccountAmountInput(account.openingBalanceMinor),
                isFormVisible = true,
                editingAccountId = account.id,
                deleteConfirmationAccountId = null,
                pendingDeleteAccountId = null,
                errorMessage = null,
            )
        }
    }

    fun requestDeleteAccount(accountId: String) {
        _uiState.update { currentState ->
            currentState.copy(
                deleteConfirmationAccountId = accountId,
                errorMessage = null,
            )
        }
    }

    fun dismissDeleteDialog() {
        _uiState.update { currentState ->
            currentState.copy(
                deleteConfirmationAccountId = null,
                errorMessage = null,
            )
        }
    }

    fun confirmDeleteAccount() {
        val snapshot = uiState.value
        val accountId = snapshot.deleteConfirmationAccountId ?: return
        val currentCurrency = snapshot.draftCurrencyCode

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    deleteConfirmationAccountId = null,
                    pendingDeleteAccountId = accountId,
                    errorMessage = null,
                )
            }

            ledgerRepository.deleteAccount(accountId)

            _uiState.update { currentState ->
                currentState.toCreateMode().copy(
                    draftCurrencyCode = currentCurrency,
                )
            }
        }
    }
}

internal val AccountType.label: String
    get() = name
        .lowercase()
        .split('_')
        .joinToString(" ") { part ->
            part.replaceFirstChar { character -> character.uppercase() }
        }

internal fun normalizeCurrencyCode(rawValue: String): String? {
    val normalized = rawValue.trim().uppercase()
    return normalized.takeIf { value ->
        value.length == 3 && value.all(Char::isLetter)
    }
}

internal fun parseAmountToMinor(rawValue: String): Long? {
    val normalized = rawValue.trim().replace(',', '.')
    if (normalized.isBlank()) return 0L

    val amountPattern = Regex("^-?\\d+(\\.\\d{0,2})?$")
    if (!amountPattern.matches(normalized)) return null

    val isNegative = normalized.startsWith('-')
    val unsignedValue = normalized.removePrefix("-")
    val parts = unsignedValue.split('.')
    val wholePart = parts.firstOrNull()?.toLongOrNull() ?: return null
    val decimalPart = parts.getOrNull(1)?.padEnd(2, '0') ?: "00"
    val minorAmount = (wholePart * 100) + decimalPart.toLong()

    return if (isNegative) -minorAmount else minorAmount
}

internal fun generateAccountId(name: String, timestampMs: Long): String {
    val slug = name
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "account" }

    return "account-$slug-$timestampMs-${Random.nextInt(1000, 9999)}"
}

internal fun formatMoney(amountMinor: Long, currencyCode: String): String {
    val sign = if (amountMinor < 0) "-" else ""
    val absoluteAmount = kotlin.math.abs(amountMinor)
    val major = absoluteAmount / 100
    val minor = absoluteAmount % 100
    return "$sign$major.${minor.toString().padStart(2, '0')} $currencyCode"
}

internal fun formatSyncTimestamp(epochMs: Long?): String {
    if (epochMs == null) return "Not synced yet"

    val localDateTime = Instant
        .fromEpochMilliseconds(epochMs)
        .toLocalDateTime(TimeZone.currentSystemDefault())

    val month = localDateTime.month.name
        .lowercase()
        .replaceFirstChar { character -> character.uppercase() }
        .take(3)

    return "$month ${localDateTime.day}, ${localDateTime.hour.toString().padStart(2, '0')}:${localDateTime.minute.toString().padStart(2, '0')}"
}

internal fun buildPositionSubtitle(position: InvestmentPosition): String {
    val assetClass = position.assetClass ?: "Portfolio holding"
    val quantity = position.titles?.let { titles ->
        "${formatHoldingDecimal(titles)} units"
    } ?: "Units unavailable"
    val price = position.price?.let { value ->
        "@ ${formatHoldingDecimal(value)}"
    }

    return listOfNotNull(assetClass, quantity, price).joinToString(" | ")
}

internal fun buildPositionValuationLabel(
    position: InvestmentPosition,
    currencyCode: String,
): String {
    val marketValue = position.marketValueMinor?.let { amount ->
        formatMoney(amount, currencyCode)
    } ?: "Unknown value"
    val costBasis = position.costAmountMinor?.let { amount ->
        "Cost ${formatMoney(amount, currencyCode)}"
    }

    return listOfNotNull("Market value $marketValue", costBasis).joinToString(" | ")
}

internal fun formatAccountAmountInput(amountMinor: Long): String {
    val absoluteAmount = kotlin.math.abs(amountMinor)
    val major = absoluteAmount / 100
    val minor = absoluteAmount % 100
    val prefix = if (amountMinor < 0) "-" else ""
    return "$prefix$major.${minor.toString().padStart(2, '0')}"
}

private fun formatHoldingDecimal(value: Double): String =
    value
        .toString()
        .trimEnd('0')
        .trimEnd('.')

private fun AccountsUiState.toCreateMode(): AccountsUiState =
    copy(
        selectedAccountId = null,
        selectedInvestmentPositions = emptyList(),
        draftName = "",
        selectedType = AccountType.CHECKING,
        draftOpeningBalance = "",
        isFormVisible = false,
        editingAccountId = null,
        deleteConfirmationAccountId = null,
        isSaving = false,
        pendingDeleteAccountId = null,
        errorMessage = null,
    )
