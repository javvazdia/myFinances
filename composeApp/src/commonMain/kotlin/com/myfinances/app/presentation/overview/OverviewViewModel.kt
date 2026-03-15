package com.myfinances.app.presentation.overview

import androidx.lifecycle.ViewModel
import com.myfinances.app.domain.repository.FinanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OverviewViewModel(
    private val financeRepository: FinanceRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OverviewUiState())
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    init {
        val snapshot = financeRepository.loadOverview()
        _uiState.value = OverviewUiState(
            totalBalance = snapshot.totalBalance,
            monthlyIncome = snapshot.monthlyIncome,
            monthlyExpenses = snapshot.monthlyExpenses,
            savingsRate = snapshot.savingsRate,
            focusMessage = snapshot.focusMessage,
            recentTransactions = snapshot.recentTransactions,
        )
    }
}

