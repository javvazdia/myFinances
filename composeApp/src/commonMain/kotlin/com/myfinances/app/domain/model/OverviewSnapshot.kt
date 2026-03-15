package com.myfinances.app.domain.model

data class OverviewSnapshot(
    val totalBalance: String,
    val monthlyIncome: String,
    val monthlyExpenses: String,
    val savingsRate: String,
    val focusMessage: String,
    val recentTransactions: List<RecentTransaction>,
)

data class RecentTransaction(
    val title: String,
    val category: String,
    val amountLabel: String,
    val dateLabel: String,
    val isExpense: Boolean,
)

