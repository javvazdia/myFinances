package com.myfinances.app.di

import com.myfinances.app.data.DefaultFinanceRepository
import com.myfinances.app.data.integration.RoomExternalConnectionsRepository
import com.myfinances.app.data.local.LocalLedgerRepository
import com.myfinances.app.data.local.StarterDataSeeder
import com.myfinances.app.data.local.db.MyFinancesDatabase
import com.myfinances.app.domain.repository.ConnectionSecretStore
import com.myfinances.app.domain.repository.FinanceRepository
import com.myfinances.app.domain.repository.ExternalConnectionsRepository
import com.myfinances.app.domain.repository.LedgerRepository
import com.myfinances.app.integrations.indexa.api.IndexaApiClient
import com.myfinances.app.integrations.indexa.api.KtorIndexaApiClient
import com.myfinances.app.integrations.indexa.sync.IndexaIntegrationService
import com.myfinances.app.integrations.indexa.sync.StubIndexaIntegrationService

data class AppDependencies(
    val financeRepository: FinanceRepository,
    val ledgerRepository: LedgerRepository,
    val externalConnectionsRepository: ExternalConnectionsRepository,
    val indexaIntegrationService: IndexaIntegrationService,
    val seedStarterData: suspend () -> Unit,
)

fun buildAppDependencies(
    database: MyFinancesDatabase,
    connectionSecretStore: ConnectionSecretStore,
): AppDependencies {
    val ledgerRepository = LocalLedgerRepository(database)
    val financeRepository = DefaultFinanceRepository(ledgerRepository)
    val externalConnectionsRepository: ExternalConnectionsRepository =
        RoomExternalConnectionsRepository(database)
    val indexaApiClient: IndexaApiClient = KtorIndexaApiClient()
    val indexaIntegrationService = StubIndexaIntegrationService(
        apiClient = indexaApiClient,
        externalConnectionsRepository = externalConnectionsRepository,
        connectionSecretStore = connectionSecretStore,
    )
    val seeder = StarterDataSeeder(
        database = database,
        ledgerRepository = ledgerRepository,
    )

    return AppDependencies(
        financeRepository = financeRepository,
        ledgerRepository = ledgerRepository,
        externalConnectionsRepository = externalConnectionsRepository,
        indexaIntegrationService = indexaIntegrationService,
        seedStarterData = { seeder.seedIfNeeded() },
    )
}
