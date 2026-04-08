package com.myfinances.app.presentation.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myfinances.app.domain.model.OverviewPeriodFilter
import com.myfinances.app.domain.repository.FinanceRepository
import com.myfinances.app.presentation.shared.MyFinancesDateRangePickerDialog

@Composable
fun OverviewRoute(
    financeRepository: FinanceRepository,
    overviewViewModel: OverviewViewModel = viewModel {
        OverviewViewModel(financeRepository)
    },
) {
    val uiState by overviewViewModel.uiState.collectAsState()
    OverviewScreen(
        uiState = uiState,
        onSelectPeriod = overviewViewModel::selectPeriod,
        onApplyCustomPeriod = overviewViewModel::applyCustomPeriod,
    )
}

@Composable
fun OverviewScreen(
    uiState: OverviewUiState,
    onSelectPeriod: (OverviewPeriodFilter) -> Unit,
    onApplyCustomPeriod: (Long, Long) -> Unit,
) {
    var isCustomRangePickerVisible by remember { mutableStateOf(false) }

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
                text = "A clean starting point for the local-first finance flows we will build next. The cash flow cards below are calculated from the selected time period.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            OverviewPeriodFilterCard(
                selectedPeriod = uiState.selectedPeriod,
                customStartEpochMs = uiState.customStartEpochMs,
                customEndEpochMs = uiState.customEndEpochMs,
                onSelectPeriod = { period ->
                    if (period == OverviewPeriodFilter.CUSTOM) {
                        isCustomRangePickerVisible = true
                    } else {
                        onSelectPeriod(period)
                    }
                },
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
                    value = uiState.income,
                )
                MetricTile(
                    title = "Expenses",
                    value = uiState.expenses,
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

    if (isCustomRangePickerVisible) {
        MyFinancesDateRangePickerDialog(
            initialStartEpochMs = uiState.customStartEpochMs,
            initialEndEpochMs = uiState.customEndEpochMs,
            onDismiss = { isCustomRangePickerVisible = false },
            onConfirm = { startEpochMs, endEpochMs ->
                isCustomRangePickerVisible = false
                onApplyCustomPeriod(startEpochMs, endEpochMs)
            },
        )
    }
}
