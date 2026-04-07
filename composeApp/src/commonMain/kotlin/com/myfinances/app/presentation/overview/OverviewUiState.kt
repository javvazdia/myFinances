package com.myfinances.app.presentation.overview

import com.myfinances.app.domain.model.OverviewPeriodFilter
import com.myfinances.app.domain.model.RecentTransaction

data class OverviewUiState(
    val totalBalance: String = "--",
    val income: String = "--",
    val expenses: String = "--",
    val savingsRate: String = "--",
    val selectedPeriod: OverviewPeriodFilter = OverviewPeriodFilter.ONE_MONTH,
    val customStartEpochMs: Long? = null,
    val customEndEpochMs: Long? = null,
    val focusMessage: String = "",
    val recentTransactions: List<RecentTransaction> = emptyList(),
)
