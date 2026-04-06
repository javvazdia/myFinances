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
import com.myfinances.app.integrations.ExternalProviderConnector
import com.myfinances.app.integrations.statements.StatementImportService
import com.myfinances.app.integrations.cajaingenieros.api.CajaIngenierosApiClient
import com.myfinances.app.integrations.cajaingenieros.api.KtorCajaIngenierosApiClient
import com.myfinances.app.integrations.cajaingenieros.sync.CajaIngenierosIntegrationService
import com.myfinances.app.integrations.cajaingenieros.sync.ScaffoldedCajaIngenierosIntegrationService
import com.myfinances.app.integrations.indexa.api.IndexaApiClient
import com.myfinances.app.integrations.indexa.api.KtorIndexaApiClient
import com.myfinances.app.integrations.indexa.sync.IndexaIntegrationService
import com.myfinances.app.integrations.indexa.sync.StubIndexaIntegrationService
import com.myfinances.app.domain.model.integration.ExternalProviderId

data class AppDependencies(
    val financeRepository: FinanceRepository,
    val ledgerRepository: LedgerRepository,
    val externalConnectionsRepository: ExternalConnectionsRepository,
    val providerConnectors: Map<ExternalProviderId, ExternalProviderConnector>,
    val indexaIntegrationService: IndexaIntegrationService,
    val cajaIngenierosIntegrationService: CajaIngenierosIntegrationService,
    val statementImportService: StatementImportService,
    val seedStarterData: suspend () -> Unit,
)

fun buildAppDependencies(
    database: MyFinancesDatabase,
    connectionSecretStore: ConnectionSecretStore,
    statementImportServiceFactory: (LedgerRepository) -> StatementImportService,
): AppDependencies {
    val ledgerRepository = LocalLedgerRepository(database)
    val financeRepository = DefaultFinanceRepository(ledgerRepository)
    val externalConnectionsRepository: ExternalConnectionsRepository =
        RoomExternalConnectionsRepository(database)
    val indexaApiClient: IndexaApiClient = KtorIndexaApiClient()
    val indexaIntegrationService = StubIndexaIntegrationService(
        apiClient = indexaApiClient,
        ledgerRepository = ledgerRepository,
        externalConnectionsRepository = externalConnectionsRepository,
        connectionSecretStore = connectionSecretStore,
    )
    val cajaIngenierosApiClient: CajaIngenierosApiClient = KtorCajaIngenierosApiClient()
    val cajaIngenierosIntegrationService = ScaffoldedCajaIngenierosIntegrationService(
        apiClient = cajaIngenierosApiClient,
        externalConnectionsRepository = externalConnectionsRepository,
        connectionSecretStore = connectionSecretStore,
    )
    val statementImportService = statementImportServiceFactory(ledgerRepository)
    val providerConnectors: Map<ExternalProviderId, ExternalProviderConnector> = mapOf(
        ExternalProviderId.INDEXA to indexaIntegrationService,
        ExternalProviderId.CAJA_INGENIEROS to cajaIngenierosIntegrationService,
    )
    val seeder = StarterDataSeeder(
        database = database,
        ledgerRepository = ledgerRepository,
    )

    return AppDependencies(
        financeRepository = financeRepository,
        ledgerRepository = ledgerRepository,
        externalConnectionsRepository = externalConnectionsRepository,
        providerConnectors = providerConnectors,
        indexaIntegrationService = indexaIntegrationService,
        cajaIngenierosIntegrationService = cajaIngenierosIntegrationService,
        statementImportService = statementImportService,
        seedStarterData = { seeder.seedIfNeeded() },
    )
}
