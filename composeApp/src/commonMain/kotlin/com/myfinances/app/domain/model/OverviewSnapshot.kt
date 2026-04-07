package com.myfinances.app.domain.model

data class OverviewSnapshot(
    val totalBalance: String,
    val income: String,
    val expenses: String,
    val savingsRate: String,
    val focusMessage: String,
    val recentTransactions: List<RecentTransaction>,
)

enum class OverviewPeriodFilter {
    ONE_MONTH,
    THREE_MONTHS,
    SIX_MONTHS,
    ONE_YEAR,
    CUSTOM,
    ALL,
}

data class RecentTransaction(
    val title: String,
    val category: String,
    val amountLabel: String,
    val dateLabel: String,
    val isExpense: Boolean,
)
