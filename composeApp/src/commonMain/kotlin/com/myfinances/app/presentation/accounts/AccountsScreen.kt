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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.AccountSourceType
import com.myfinances.app.domain.repository.LedgerRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Composable
fun AccountsRoute(
    ledgerRepository: LedgerRepository,
    accountsViewModel: AccountsViewModel = viewModel {
        AccountsViewModel(ledgerRepository)
    },
) {
    val uiState by accountsViewModel.uiState.collectAsState()
    AccountsScreen(uiState = uiState)
}

@Composable
fun AccountsScreen(uiState: AccountsUiState) {
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
                text = "These accounts are stored locally today, and each record already carries source metadata so future bank/API sync can plug into the same ledger.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(uiState.accounts) { account ->
            AccountCard(account = account)
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
                Column {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = account.type.name.replace('_', ' '),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${account.openingBalanceMinor / 100}.${(account.openingBalanceMinor % 100).toString().padStart(2, '0')} ${account.currencyCode}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
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
)

class AccountsViewModel(
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ledgerRepository.observeAccounts().collect { accounts ->
                _uiState.value = AccountsUiState(accounts = accounts)
            }
        }
    }
}
