package com.myfinances.app.presentation.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfinances.app.domain.repository.FinanceRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OverviewViewModel(
    private val financeRepository: FinanceRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OverviewUiState())
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            financeRepository.observeOverview().collect { snapshot ->
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
    }
}
