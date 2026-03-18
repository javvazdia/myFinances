package com.myfinances.app.integrations.indexa.sync

import com.myfinances.app.data.integration.InMemoryConnectionSecretStore
import com.myfinances.app.data.integration.InMemoryExternalConnectionsRepository
import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.AccountValuationSnapshot
import com.myfinances.app.domain.model.AccountSourceType
import com.myfinances.app.domain.model.AccountType
import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.CategoryKind
import com.myfinances.app.domain.model.FinanceTransaction
import com.myfinances.app.domain.model.InvestmentPosition
import com.myfinances.app.domain.model.TransactionType
import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.model.integration.ExternalSyncStatus
import com.myfinances.app.domain.repository.LedgerRepository
import com.myfinances.app.integrations.indexa.api.IndexaApiClient
import com.myfinances.app.integrations.indexa.api.StubIndexaApiClient
import com.myfinances.app.integrations.indexa.model.IndexaAccountSummary
import com.myfinances.app.integrations.indexa.model.IndexaCashTransaction
import com.myfinances.app.integrations.indexa.model.IndexaInstrumentTransaction
import com.myfinances.app.integrations.indexa.model.IndexaPerformanceHistory
import com.myfinances.app.integrations.indexa.model.IndexaPortfolioPosition
import com.myfinances.app.integrations.indexa.model.IndexaPortfolioSnapshot
import com.myfinances.app.integrations.indexa.model.IndexaUserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StubIndexaIntegrationServiceTest {
    @Test
    fun connectCreatesConnectionAndStoresSecret() = runBlocking {
        val connectionsRepository = InMemoryExternalConnectionsRepository()
        val secretStore = InMemoryConnectionSecretStore()
        val ledgerRepository = FakeLedgerRepository()
        val service = StubIndexaIntegrationService(
            apiClient = StubIndexaApiClient(),
            ledgerRepository = ledgerRepository,
            externalConnectionsRepository = connectionsRepository,
            connectionSecretStore = secretStore,
        )

        val connection = service.connect("demo-token")

        val savedSecret = secretStore.readSecret(
            providerId = ExternalProviderId.INDEXA,
            connectionId = connection.id,
        )
        val savedConnections = connectionsRepository.observeConnections().first()
        val accountLinks = connectionsRepository.observeAccountLinks(connection.id).first()

        assertEquals(ExternalProviderId.INDEXA, connection.providerId)
        assertEquals(ExternalSyncStatus.IDLE, connection.lastSyncStatus)
        assertEquals(1, savedConnections.size)
        assertEquals(1, accountLinks.size)
        assertNotNull(savedSecret)
        assertEquals("demo-token", savedSecret)
    }

    @Test
    fun runSyncImportsLinkedAccountsIntoLedger() = runBlocking {
        val connectionsRepository = InMemoryExternalConnectionsRepository()
        val secretStore = InMemoryConnectionSecretStore()
        val ledgerRepository = FakeLedgerRepository()
        val service = StubIndexaIntegrationService(
            apiClient = StubIndexaApiClient(),
            ledgerRepository = ledgerRepository,
            externalConnectionsRepository = connectionsRepository,
            connectionSecretStore = secretStore,
        )

        val connection = service.connect("demo-token")

        val syncRun = service.runSync(connection.id)

        val importedAccounts = ledgerRepository.observeAccounts(includeArchived = true).first()
        val importedTransactions = ledgerRepository.observeAllTransactions().first()
        val importedSnapshots = ledgerRepository.observeAccountValuationSnapshots(importedAccounts.first().id).first()
        val importedCategories = ledgerRepository.observeCategories().first()
        val updatedConnection = connectionsRepository.observeConnections().first().first()
        val links = connectionsRepository.observeAccountLinks(connection.id).first()

        assertEquals(ExternalSyncStatus.SUCCESS, syncRun.status)
        assertEquals(1, syncRun.importedAccounts)
        assertEquals(1, syncRun.importedTransactions)
        assertEquals(1, syncRun.importedPositions)
        assertEquals(1, importedAccounts.size)
        assertEquals(1, importedTransactions.size)
        assertEquals(1, importedSnapshots.size)
        assertEquals(12_450_32L, importedSnapshots.first().valueMinor)
        assertTrue(importedCategories.any { category -> category.name == "Investment fees" })
        assertEquals("Indexa Capital", importedAccounts.first().sourceProvider)
        assertEquals("cash-demo-1", importedTransactions.first().externalTransactionId)
        assertEquals(TransactionType.EXPENSE, importedTransactions.first().type)
        assertEquals(300L, importedTransactions.first().amountMinor)
        assertEquals("cat-expense-indexa-investment-fees", importedTransactions.first().categoryId)
        assertEquals(ExternalSyncStatus.SUCCESS, updatedConnection.lastSyncStatus)
        assertTrue(links.first().localAccountId != null)
    }

    @Test
    fun runSyncMapsNonFeeCashMovementsAsTransfers() = runBlocking {
        val connectionsRepository = InMemoryExternalConnectionsRepository()
        val secretStore = InMemoryConnectionSecretStore()
        val ledgerRepository = FakeLedgerRepository()
        val service = StubIndexaIntegrationService(
            apiClient = TransferHeavyIndexaApiClient(),
            ledgerRepository = ledgerRepository,
            externalConnectionsRepository = connectionsRepository,
            connectionSecretStore = secretStore,
        )

        val connection = service.connect("demo-token")

        val syncRun = service.runSync(connection.id)
        val importedCategories = ledgerRepository.observeCategories().first()
        val importedTransactions = ledgerRepository.observeAllTransactions().first()

        assertEquals(3, syncRun.importedTransactions)
        assertEquals(3, importedTransactions.size)
        assertTrue(importedCategories.any { category -> category.name == "Investment contribution" })
        assertTrue(importedCategories.any { category -> category.name == "Investment fees" })
        assertEquals(TransactionType.TRANSFER, importedTransactions[0].type)
        assertEquals(-2500L, importedTransactions[0].amountMinor)
        assertEquals("cat-transfer-indexa-investment-contribution", importedTransactions[0].categoryId)
        assertEquals(TransactionType.TRANSFER, importedTransactions[1].type)
        assertEquals(15000L, importedTransactions[1].amountMinor)
        assertEquals("cat-transfer-indexa-transfer", importedTransactions[1].categoryId)
        assertEquals(TransactionType.EXPENSE, importedTransactions[2].type)
        assertEquals(200L, importedTransactions[2].amountMinor)
        assertEquals("cat-expense-indexa-investment-fees", importedTransactions[2].categoryId)
    }
}

private class FakeLedgerRepository : LedgerRepository {
    private val accounts = MutableStateFlow<List<Account>>(emptyList())
    private val positionsByAccount = mutableMapOf<String, MutableStateFlow<List<InvestmentPosition>>>()
    private val snapshotsByAccount = mutableMapOf<String, MutableStateFlow<List<AccountValuationSnapshot>>>()
    private val categories = MutableStateFlow<List<Category>>(emptyList())
    private val transactions = MutableStateFlow<List<FinanceTransaction>>(emptyList())

    override fun observeAccounts(includeArchived: Boolean): Flow<List<Account>> = accounts

    override fun observeInvestmentPositions(accountId: String): Flow<List<InvestmentPosition>> =
        positionsByAccount.getOrPut(accountId) { MutableStateFlow(emptyList()) }

    override fun observeAccountValuationSnapshots(accountId: String): Flow<List<AccountValuationSnapshot>> =
        snapshotsByAccount.getOrPut(accountId) { MutableStateFlow(emptyList()) }

    override fun observeCategories(): Flow<List<Category>> = categories

    override fun observeCategories(kind: CategoryKind): Flow<List<Category>> =
        MutableStateFlow(categories.value.filter { category -> category.kind == kind })

    override fun observeRecentTransactions(limit: Int): Flow<List<FinanceTransaction>> =
        MutableStateFlow(transactions.value.take(limit))

    override fun observeAllTransactions(): Flow<List<FinanceTransaction>> = transactions

    override fun observeTransactionsForAccount(accountId: String): Flow<List<FinanceTransaction>> =
        MutableStateFlow(transactions.value.filter { transaction -> transaction.accountId == accountId })

    override suspend fun upsertAccount(account: Account) {
        accounts.value = accounts.value
            .filterNot { existing -> existing.id == account.id }
            .plus(account)
    }

    override suspend fun replaceInvestmentPositions(
        accountId: String,
        positions: List<InvestmentPosition>,
    ) {
        positionsByAccount.getOrPut(accountId) { MutableStateFlow(emptyList()) }.value = positions
    }

    override suspend fun upsertCategory(category: Category) {
        categories.value = categories.value
            .filterNot { existing -> existing.id == category.id }
            .plus(category)
            .sortedBy(Category::name)
    }

    override suspend fun upsertTransaction(transaction: FinanceTransaction) {
        transactions.value = transactions.value
            .filterNot { existing -> existing.id == transaction.id }
            .plus(transaction)
            .sortedByDescending(FinanceTransaction::postedAtEpochMs)
    }

    override suspend fun upsertAccountValuationSnapshot(snapshot: AccountValuationSnapshot) {
        snapshotsByAccount.getOrPut(snapshot.accountId) { MutableStateFlow(emptyList()) }.value =
            snapshotsByAccount.getValue(snapshot.accountId).value
                .filterNot { existing -> existing.id == snapshot.id }
                .plus(snapshot)
                .sortedBy(AccountValuationSnapshot::capturedAtEpochMs)
    }

    override suspend fun deleteAccount(accountId: String) = Unit

    override suspend fun deleteCategory(categoryId: String) = Unit

    override suspend fun deleteTransaction(transactionId: String) = Unit
}

private class TransferHeavyIndexaApiClient : IndexaApiClient {
    override suspend fun fetchUserProfile(accessToken: String): IndexaUserProfile =
        IndexaUserProfile(
            email = "indexa@example.com",
            fullName = "Indexa Demo",
            documentId = null,
            accounts = fetchAccounts(accessToken),
        )

    override suspend fun fetchAccounts(accessToken: String): List<IndexaAccountSummary> = listOf(
        IndexaAccountSummary(
            accountNumber = "INDEXA-DEMO-02",
            displayName = "Indexa Transfer Portfolio",
            productType = "mutual",
            providerCode = "INV",
            currencyCode = "EUR",
            currentValuation = 9_500.0,
        ),
    )

    override suspend fun fetchPortfolio(
        accessToken: String,
        accountNumber: String,
    ): IndexaPortfolioSnapshot = IndexaPortfolioSnapshot(
        accountNumber = accountNumber,
        valuationDate = "2026-03-18",
        totalMarketValue = 9_500.0,
        positions = listOf(
            IndexaPortfolioPosition(
                isin = "IE0032126645",
                name = "Vanguard US 500 Stk Idx -Inst",
                assetClass = "equity_north_america",
                titles = 10.0,
                price = 250.0,
                marketValue = 2_500.0,
                costAmount = 2_100.0,
            ),
        ),
    )

    override suspend fun fetchCashTransactions(
        accessToken: String,
        accountNumber: String,
    ): List<IndexaCashTransaction> = listOf(
        IndexaCashTransaction(
            reference = "cash-transfer-1",
            accountNumber = accountNumber,
            date = "2026-03-04",
            amount = -25.0,
            currencyCode = "EUR",
            fees = 0.0,
            operationCode = 101,
            operationType = "SUSCRIPCION FONDOS INVERSION SF",
            comments = "Monthly contribution",
        ),
        IndexaCashTransaction(
            reference = "cash-transfer-2",
            accountNumber = accountNumber,
            date = "2026-03-03",
            amount = 150.0,
            currencyCode = "EUR",
            fees = 0.0,
            operationCode = 103,
            operationType = "TRANSFERENCIA SEPA",
            comments = "Cash transfer",
        ),
        IndexaCashTransaction(
            reference = "cash-fee-1",
            accountNumber = accountNumber,
            date = "2026-03-02",
            amount = -2.0,
            currencyCode = "EUR",
            fees = 0.0,
            operationCode = 102,
            operationType = "CARGO COMISIONES",
            comments = "Platform fee",
        ),
    )

    override suspend fun fetchPerformance(
        accessToken: String,
        accountNumber: String,
    ): IndexaPerformanceHistory = IndexaPerformanceHistory(
        accountNumber = accountNumber,
        valueHistory = mapOf(
            "2026-01-31" to 9_200.0,
            "2026-02-28" to 9_350.0,
            "2026-03-18" to 9_500.0,
        ),
        normalizedHistory = mapOf(
            "2026-01-31" to 1.01,
            "2026-02-28" to 1.03,
            "2026-03-18" to 1.05,
        ),
    )

    override suspend fun fetchInstrumentTransactions(
        accessToken: String,
        accountNumber: String,
    ): List<IndexaInstrumentTransaction> = emptyList()
}
