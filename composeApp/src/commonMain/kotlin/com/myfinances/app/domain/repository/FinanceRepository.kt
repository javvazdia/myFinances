package com.myfinances.app.domain.repository

import com.myfinances.app.domain.model.OverviewSnapshot
import kotlinx.coroutines.flow.Flow

interface FinanceRepository {
    fun observeOverview(): Flow<OverviewSnapshot>
}
