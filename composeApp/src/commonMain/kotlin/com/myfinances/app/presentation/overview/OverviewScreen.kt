package com.myfinances.app.presentation.overview

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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myfinances.app.domain.model.RecentTransaction
import com.myfinances.app.domain.repository.FinanceRepository

@Composable
fun OverviewRoute(
    financeRepository: FinanceRepository,
    overviewViewModel: OverviewViewModel = viewModel {
        OverviewViewModel(financeRepository)
    },
) {
    val uiState by overviewViewModel.uiState.collectAsState()
    OverviewScreen(uiState = uiState)
}

@Composable
fun OverviewScreen(uiState: OverviewUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "Overview",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        item {
            Text(
                text = "A clean starting point for the local-first finance flows we will build next.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            MetricHighlight(
                title = "Total balance",
                value = uiState.totalBalance,
                supporting = uiState.focusMessage,
            )
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricTile(
                    title = "Income",
                    value = uiState.monthlyIncome,
                )
                MetricTile(
                    title = "Expenses",
                    value = uiState.monthlyExpenses,
                )
                MetricTile(
                    title = "Savings",
                    value = uiState.savingsRate,
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

        items(uiState.recentTransactions) { transaction ->
            TransactionRow(transaction = transaction)
        }
    }
}

@Composable
private fun MetricHighlight(
    title: String,
    value: String,
    supporting: String,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun TransactionRow(transaction: RecentTransaction) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = transaction.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = transaction.category,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = transaction.amountLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (transaction.isExpense) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = transaction.dateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
