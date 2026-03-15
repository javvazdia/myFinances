package com.myfinances.app.di

import com.myfinances.app.data.DefaultFinanceRepository
import com.myfinances.app.data.local.LocalLedgerRepository
import com.myfinances.app.data.local.StarterDataSeeder
import com.myfinances.app.data.local.db.MyFinancesDatabase
import com.myfinances.app.domain.repository.FinanceRepository
import com.myfinances.app.domain.repository.LedgerRepository

data class AppDependencies(
    val financeRepository: FinanceRepository,
    val ledgerRepository: LedgerRepository,
    val seedStarterData: suspend () -> Unit,
)

fun buildAppDependencies(
    database: MyFinancesDatabase,
): AppDependencies {
    val ledgerRepository = LocalLedgerRepository(database)
    val financeRepository = DefaultFinanceRepository(ledgerRepository)
    val seeder = StarterDataSeeder(
        database = database,
        ledgerRepository = ledgerRepository,
    )

    return AppDependencies(
        financeRepository = financeRepository,
        ledgerRepository = ledgerRepository,
        seedStarterData = { seeder.seedIfNeeded() },
    )
}
