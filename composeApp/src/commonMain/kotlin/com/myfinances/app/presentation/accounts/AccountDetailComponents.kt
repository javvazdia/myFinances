package com.myfinances.app.presentation.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.AccountSourceType
import com.myfinances.app.domain.model.AccountType
import com.myfinances.app.domain.model.InvestmentPosition

@Composable
internal fun AccountDetailScreen(
    account: Account,
    currentBalanceMinor: Long,
    historyChart: AccountHistoryChart?,
    availableHistoryModes: List<AccountHistoryMode>,
    selectedHistoryMode: AccountHistoryMode,
    selectedHistoryRange: AccountHistoryRange,
    customHistoryStartEpochMs: Long?,
    customHistoryEndEpochMs: Long?,
    isLoadingHistory: Boolean,
    historyErrorMessage: String?,
    positions: List<InvestmentPosition>,
    onSelectHistoryMode: (AccountHistoryMode) -> Unit,
    onSelectHistoryRange: (AccountHistoryRange) -> Unit,
    onApplyCustomHistoryRange: (Long, Long) -> Unit,
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
            AccountHistoryCard(
                chart = historyChart,
                availableModes = availableHistoryModes,
                selectedMode = selectedHistoryMode,
                selectedRange = selectedHistoryRange,
                customStartEpochMs = customHistoryStartEpochMs,
                customEndEpochMs = customHistoryEndEpochMs,
                isLoading = isLoadingHistory,
                errorMessage = historyErrorMessage,
                onSelectMode = onSelectHistoryMode,
                onSelectRange = onSelectHistoryRange,
                onApplyCustomRange = onApplyCustomHistoryRange,
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
