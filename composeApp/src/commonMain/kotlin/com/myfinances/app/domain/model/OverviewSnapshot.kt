package com.myfinances.app.domain.model

data class OverviewSnapshot(
    val totalBalance: String,
    val income: String,
    val expenses: String,
    val savingsRate: String,
    val focusMessage: String,
    val history: OverviewHistory?,
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

data class OverviewHistory(
    val currencyCode: String,
    val lines: List<OverviewHistoryLine>,
    val minimumLabel: String,
    val maximumLabel: String,
    val startLabel: String,
    val endLabel: String,
)

data class OverviewHistoryLine(
    val id: String,
    val label: String,
    val points: List<OverviewHistoryPoint>,
    val isTotal: Boolean,
)

data class OverviewHistoryPoint(
    val timestampEpochMs: Long,
    val valueMinor: Long,
    val axisLabel: String,
    val detailLabel: String,
)
