package com.myfinances.app.presentation.overview

import com.myfinances.app.domain.model.RecentTransaction

data class OverviewUiState(
    val totalBalance: String = "--",
    val monthlyIncome: String = "--",
    val monthlyExpenses: String = "--",
    val savingsRate: String = "--",
    val focusMessage: String = "",
    val recentTransactions: List<RecentTransaction> = emptyList(),
)

