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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.myfinances.app.domain.repository.LedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock

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
        onNameChange = accountsViewModel::onNameChange,
        onTypeSelected = accountsViewModel::onTypeSelected,
        onCurrencyCodeChange = accountsViewModel::onCurrencyCodeChange,
        onOpeningBalanceChange = accountsViewModel::onOpeningBalanceChange,
        onCreateAccount = accountsViewModel::createAccount,
    )
}

@Composable
fun AccountsScreen(
    uiState: AccountsUiState,
    onNameChange: (String) -> Unit,
    onTypeSelected: (AccountType) -> Unit,
    onCurrencyCodeChange: (String) -> Unit,
    onOpeningBalanceChange: (String) -> Unit,
    onCreateAccount: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
                text = "Start with manual accounts now. The model already leaves room for future bank and provider integrations to sync into this same ledger.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            CreateAccountCard(
                uiState = uiState,
                onNameChange = onNameChange,
                onTypeSelected = onTypeSelected,
                onCurrencyCodeChange = onCurrencyCodeChange,
                onOpeningBalanceChange = onOpeningBalanceChange,
                onCreateAccount = onCreateAccount,
            )
        }

        if (uiState.accounts.isEmpty()) {
            item {
                Card {
                    Text(
                        text = "No accounts yet. Create your first one above to start tracking balances and transactions.",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            items(uiState.accounts) { account ->
                AccountCard(account = account)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateAccountCard(
    uiState: AccountsUiState,
    onNameChange: (String) -> Unit,
    onTypeSelected: (AccountType) -> Unit,
    onCurrencyCodeChange: (String) -> Unit,
    onOpeningBalanceChange: (String) -> Unit,
    onCreateAccount: () -> Unit,
) {
    var typeMenuExpanded by remember { mutableStateOf(false) }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Create account",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = "This first flow creates a local manual account. Later we can add synced providers without changing the ledger model.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = uiState.draftName,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Account name") },
                singleLine = true,
                enabled = !uiState.isSaving,
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
                    enabled = !uiState.isSaving,
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
                enabled = !uiState.isSaving,
            )

            OutlinedTextField(
                value = uiState.draftOpeningBalance,
                onValueChange = onOpeningBalanceChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Opening balance") },
                supportingText = { Text("Examples: 1200, 1200.50, or -250.00") },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                singleLine = true,
                enabled = !uiState.isSaving,
            )

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = onCreateAccount,
                enabled = !uiState.isSaving,
            ) {
                Text(if (uiState.isSaving) "Saving..." else "Create account")
            }
        }
    }
}

@Composable
private fun AccountCard(account: Account) {
    Card {
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
        }
    }
}

data class AccountsUiState(
    val accounts: List<Account> = emptyList(),
    val draftName: String = "",
    val selectedType: AccountType = AccountType.CHECKING,
    val draftCurrencyCode: String = "EUR",
    val draftOpeningBalance: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

class AccountsViewModel(
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ledgerRepository.observeAccounts().collect { accounts ->
                _uiState.update { currentState ->
                    currentState.copy(accounts = accounts)
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

    fun createAccount() {
        val snapshot = uiState.value
        val name = snapshot.draftName.trim()
        val currencyCode = normalizeCurrencyCode(snapshot.draftCurrencyCode)
        val openingBalanceMinor = parseAmountToMinor(snapshot.draftOpeningBalance)

        val error = when {
            name.isBlank() -> "Account name is required."
            currencyCode == null -> "Currency code must be 3 letters, like EUR or USD."
            openingBalanceMinor == null -> "Opening balance must be a valid number with up to 2 decimals."
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
                id = generateAccountId(name, now),
                name = name,
                type = snapshot.selectedType,
                currencyCode = validatedCurrencyCode,
                openingBalanceMinor = validatedOpeningBalanceMinor,
                sourceType = AccountSourceType.MANUAL,
                sourceProvider = null,
                externalAccountId = null,
                lastSyncedAtEpochMs = null,
                isArchived = false,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            )

            ledgerRepository.upsertAccount(account)

            _uiState.update { currentState ->
                currentState.copy(
                    draftName = "",
                    selectedType = AccountType.CHECKING,
                    draftCurrencyCode = validatedCurrencyCode,
                    draftOpeningBalance = "",
                    isSaving = false,
                    errorMessage = null,
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
