package com.myfinances.app.domain.repository

import com.myfinances.app.domain.model.OverviewSnapshot

interface FinanceRepository {
    fun loadOverview(): OverviewSnapshot
}

