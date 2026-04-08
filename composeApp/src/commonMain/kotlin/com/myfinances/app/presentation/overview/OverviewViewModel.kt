package com.myfinances.app.presentation.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfinances.app.domain.model.OverviewPeriodFilter
import com.myfinances.app.domain.repository.FinanceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModel(
    private val financeRepository: FinanceRepository,
) : ViewModel() {
    private val selectedPeriod = MutableStateFlow(OverviewPeriodFilter.ONE_MONTH)
    private val customStartEpochMs = MutableStateFlow<Long?>(null)
    private val customEndEpochMs = MutableStateFlow<Long?>(null)
    private val _uiState = MutableStateFlow(OverviewUiState())
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                selectedPeriod,
                customStartEpochMs,
                customEndEpochMs,
            ) { period, startEpochMs, endEpochMs ->
                OverviewFilterSelection(
                    period = period,
                    customStartEpochMs = startEpochMs,
                    customEndEpochMs = endEpochMs,
                )
            }.flatMapLatest { selection ->
                financeRepository.observeOverview(
                    period = selection.period,
                    customStartEpochMs = selection.customStartEpochMs,
                    customEndEpochMs = selection.customEndEpochMs,
                )
            }.collect { snapshot ->
                _uiState.value = OverviewUiState(
                    totalBalance = snapshot.totalBalance,
                    income = snapshot.income,
                    expenses = snapshot.expenses,
                    savingsRate = snapshot.savingsRate,
                    selectedPeriod = selectedPeriod.value,
                    customStartEpochMs = customStartEpochMs.value,
                    customEndEpochMs = customEndEpochMs.value,
                    focusMessage = snapshot.focusMessage,
                    history = snapshot.history,
                    recentTransactions = snapshot.recentTransactions,
                )
            }
        }
    }

    fun selectPeriod(period: OverviewPeriodFilter) {
        if (selectedPeriod.value == period) return
        selectedPeriod.value = period
    }

    fun applyCustomPeriod(
        startEpochMs: Long,
        endEpochMs: Long,
    ) {
        customStartEpochMs.value = startEpochMs
        customEndEpochMs.value = endEpochMs
        selectedPeriod.value = OverviewPeriodFilter.CUSTOM
    }
}

private data class OverviewFilterSelection(
    val period: OverviewPeriodFilter,
    val customStartEpochMs: Long?,
    val customEndEpochMs: Long?,
)
