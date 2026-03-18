package com.myfinances.app.integrations.indexa.sync

import com.myfinances.app.data.integration.InMemoryConnectionSecretStore
import com.myfinances.app.data.integration.InMemoryExternalConnectionsRepository
import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.CategoryKind
import com.myfinances.app.domain.model.FinanceTransaction
import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.model.integration.ExternalSyncStatus
import com.myfinances.app.domain.repository.LedgerRepository
import com.myfinances.app.integrations.indexa.api.StubIndexaApiClient
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
        val updatedConnection = connectionsRepository.observeConnections().first().first()
        val links = connectionsRepository.observeAccountLinks(connection.id).first()

        assertEquals(ExternalSyncStatus.SUCCESS, syncRun.status)
        assertEquals(1, syncRun.importedAccounts)
        assertEquals(1, importedAccounts.size)
        assertEquals("Indexa Capital", importedAccounts.first().sourceProvider)
        assertEquals(ExternalSyncStatus.SUCCESS, updatedConnection.lastSyncStatus)
        assertTrue(links.first().localAccountId != null)
    }
}

private class FakeLedgerRepository : LedgerRepository {
    private val accounts = MutableStateFlow<List<Account>>(emptyList())

    override fun observeAccounts(includeArchived: Boolean): Flow<List<Account>> = accounts

    override fun observeCategories(): Flow<List<Category>> = MutableStateFlow(emptyList())

    override fun observeCategories(kind: CategoryKind): Flow<List<Category>> = MutableStateFlow(emptyList())

    override fun observeRecentTransactions(limit: Int): Flow<List<FinanceTransaction>> =
        MutableStateFlow(emptyList())

    override fun observeAllTransactions(): Flow<List<FinanceTransaction>> = MutableStateFlow(emptyList())

    override fun observeTransactionsForAccount(accountId: String): Flow<List<FinanceTransaction>> =
        MutableStateFlow(emptyList())

    override suspend fun upsertAccount(account: Account) {
        accounts.value = accounts.value
            .filterNot { existing -> existing.id == account.id }
            .plus(account)
    }

    override suspend fun upsertCategory(category: Category) = Unit

    override suspend fun upsertTransaction(transaction: FinanceTransaction) = Unit

    override suspend fun deleteCategory(categoryId: String) = Unit

    override suspend fun deleteTransaction(transactionId: String) = Unit
}
