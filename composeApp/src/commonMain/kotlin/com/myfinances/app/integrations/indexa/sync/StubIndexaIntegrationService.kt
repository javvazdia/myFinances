package com.myfinances.app.integrations.indexa.sync

import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.AccountValuationSnapshot
import com.myfinances.app.domain.model.AccountSourceType
import com.myfinances.app.domain.model.AccountType
import com.myfinances.app.domain.model.InvestmentPosition
import com.myfinances.app.domain.model.integration.ExternalAccountLink
import com.myfinances.app.domain.model.integration.ExternalConnection
import com.myfinances.app.domain.model.integration.ExternalConnectionStatus
import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.model.integration.ExternalSyncRun
import com.myfinances.app.domain.model.integration.ExternalSyncStatus
import com.myfinances.app.domain.repository.ConnectionSecretStore
import com.myfinances.app.domain.repository.ExternalConnectionsRepository
import com.myfinances.app.domain.repository.LedgerRepository
import com.myfinances.app.integrations.indexa.api.IndexaApiClient
import com.myfinances.app.integrations.indexa.model.IndexaAccountSummary
import com.myfinances.app.integrations.indexa.model.IndexaConnectionPreview
import com.myfinances.app.integrations.indexa.model.IndexaPerformanceHistory
import com.myfinances.app.integrations.indexa.model.IndexaPortfolioSnapshot
import com.myfinances.app.integrations.indexa.model.IndexaPortfolioPosition
import com.myfinances.app.logging.AppLogger
import kotlinx.coroutines.flow.first
import kotlin.random.Random
import kotlin.time.Clock

class StubIndexaIntegrationService(
    private val apiClient: IndexaApiClient,
    private val ledgerRepository: LedgerRepository,
    private val externalConnectionsRepository: ExternalConnectionsRepository,
    private val connectionSecretStore: ConnectionSecretStore,
) : IndexaIntegrationService {
    override suspend fun testConnection(accessToken: String): IndexaConnectionPreview {
        val profile = apiClient.fetchUserProfile(accessToken)
        return IndexaConnectionPreview(
            profile = profile,
            suggestedConnectionName = buildSuggestedConnectionName(profile.fullName),
        )
    }

    override suspend fun connect(accessToken: String): ExternalConnection {
        val preview = testConnection(accessToken)
        val now = Clock.System.now().toEpochMilliseconds()
        val existingConnection = externalConnectionsRepository
            .observeConnections()
            .first()
            .firstOrNull { connection -> connection.providerId == ExternalProviderId.INDEXA }

        val connection = ExternalConnection(
            id = existingConnection?.id ?: "conn-indexa-$now-${Random.nextInt(1000, 9999)}",
            providerId = ExternalProviderId.INDEXA,
            displayName = preview.suggestedConnectionName,
            status = ExternalConnectionStatus.CONNECTED,
            externalUserId = preview.profile.email,
            lastSuccessfulSyncEpochMs = null,
            lastSyncAttemptEpochMs = now,
            lastSyncStatus = ExternalSyncStatus.IDLE,
            lastErrorMessage = null,
            createdAtEpochMs = existingConnection?.createdAtEpochMs ?: now,
            updatedAtEpochMs = now,
        )

        externalConnectionsRepository.upsertConnection(connection)
        externalConnectionsRepository.replaceAccountLinks(
            connectionId = connection.id,
            links = preview.profile.accounts.map { account ->
                ExternalAccountLink(
                    connectionId = connection.id,
                    providerAccountId = account.accountNumber,
                    localAccountId = null,
                    accountDisplayName = account.displayName,
                    accountTypeLabel = account.productType,
                    currencyCode = account.currencyCode,
                    lastImportedAtEpochMs = null,
                )
            },
        )
        connectionSecretStore.saveSecret(
            providerId = ExternalProviderId.INDEXA,
            connectionId = connection.id,
            secret = accessToken,
        )

        return connection
    }

    override suspend fun runSync(connectionId: String): ExternalSyncRun {
        val startedAt = Clock.System.now().toEpochMilliseconds()
        val syncRunId = "sync-indexa-$startedAt-${Random.nextInt(1000, 9999)}"
        val connection = externalConnectionsRepository
            .observeConnections()
            .first()
            .firstOrNull { item -> item.id == connectionId && item.providerId == ExternalProviderId.INDEXA }
            ?: error("Indexa connection not found.")
        val accessToken = connectionSecretStore.readSecret(
            providerId = ExternalProviderId.INDEXA,
            connectionId = connectionId,
        ) ?: error("Indexa token not found for this connection. Save the connection again before syncing.")

        externalConnectionsRepository.upsertConnection(
            connection.copy(
                status = ExternalConnectionStatus.SYNCING,
                lastSyncAttemptEpochMs = startedAt,
                lastSyncStatus = ExternalSyncStatus.RUNNING,
                lastErrorMessage = null,
                updatedAtEpochMs = startedAt,
            ),
        )

        return runCatching {
            val providerAccounts = apiClient.fetchAccounts(accessToken)
            val existingAccounts = ledgerRepository.observeAccounts(includeArchived = true).first()
            val existingLinks = externalConnectionsRepository.observeAccountLinks(connectionId).first()
            val importedAt = Clock.System.now().toEpochMilliseconds()
            val categoryLookup = ensureIndexaCategories(
                ledgerRepository = ledgerRepository,
                timestampMs = importedAt,
            )
            var importedPositionsCount = 0
            var importedTransactionsCount = 0

            val updatedLinks = providerAccounts.mapIndexed { index, providerAccount ->
                val existingLink = existingLinks.firstOrNull { link ->
                    link.providerAccountId == providerAccount.accountNumber
                }
                val existingAccount = resolveExistingAccount(
                    existingAccounts = existingAccounts,
                    existingLink = existingLink,
                    providerAccount = providerAccount,
                )
                val localAccountId = existingAccount?.id
                    ?: buildSyncedAccountId(providerAccount.accountNumber, importedAt, index)
                val portfolio = apiClient.fetchPortfolio(
                    accessToken = accessToken,
                    accountNumber = providerAccount.accountNumber,
                )
                val cashTransactions = apiClient.fetchCashTransactions(
                    accessToken = accessToken,
                    accountNumber = providerAccount.accountNumber,
                )
                val updatedAccount = providerAccount.toLocalAccount(
                    localAccountId = localAccountId,
                    existingAccount = existingAccount,
                    syncedAtEpochMs = importedAt,
                    portfolio = portfolio,
                )
                val positions = portfolio.toInvestmentPositions(
                    localAccountId = localAccountId,
                    syncedAtEpochMs = importedAt,
                )
                val ledgerTransactions = cashTransactions.mapNotNull { transaction ->
                    transaction.toLedgerTransaction(
                        localAccountId = localAccountId,
                        fallbackCurrencyCode = updatedAccount.currencyCode,
                        syncedAtEpochMs = importedAt,
                        categoryLookup = categoryLookup,
                    )
                }

                ledgerRepository.upsertAccount(updatedAccount)
                portfolio.toAccountValuationSnapshot(
                    account = updatedAccount,
                    capturedAtEpochMs = importedAt,
                )?.let { snapshot ->
                    ledgerRepository.upsertAccountValuationSnapshot(snapshot)
                }
                ledgerRepository.replaceInvestmentPositions(
                    accountId = localAccountId,
                    positions = positions,
                )
                ledgerTransactions.forEach { transaction ->
                    ledgerRepository.upsertTransaction(transaction)
                }
                importedPositionsCount += positions.size
                importedTransactionsCount += ledgerTransactions.size

                ExternalAccountLink(
                    connectionId = connectionId,
                    providerAccountId = providerAccount.accountNumber,
                    localAccountId = localAccountId,
                    accountDisplayName = providerAccount.displayName,
                    accountTypeLabel = providerAccount.productType,
                    currencyCode = normalizeIndexaCurrencyCode(providerAccount.currencyCode)
                        ?: existingAccount?.currencyCode
                        ?: DEFAULT_INDEXA_CURRENCY_CODE,
                    lastImportedAtEpochMs = importedAt,
                )
            }

            externalConnectionsRepository.replaceAccountLinks(
                connectionId = connectionId,
                links = updatedLinks,
            )

            val completedAt = Clock.System.now().toEpochMilliseconds()
            val successfulConnection = connection.copy(
                status = ExternalConnectionStatus.CONNECTED,
                lastSuccessfulSyncEpochMs = completedAt,
                lastSyncAttemptEpochMs = startedAt,
                lastSyncStatus = ExternalSyncStatus.SUCCESS,
                lastErrorMessage = null,
                updatedAtEpochMs = completedAt,
            )
            val syncRun = ExternalSyncRun(
                id = syncRunId,
                connectionId = connectionId,
                providerId = ExternalProviderId.INDEXA,
                startedAtEpochMs = startedAt,
                finishedAtEpochMs = completedAt,
                status = ExternalSyncStatus.SUCCESS,
                importedAccounts = updatedLinks.size,
                importedTransactions = importedTransactionsCount,
                importedPositions = importedPositionsCount,
                message = buildSyncSuccessMessage(
                    importedAccounts = updatedLinks.size,
                    importedTransactions = importedTransactionsCount,
                    importedPositions = importedPositionsCount,
                ),
            )

            externalConnectionsRepository.upsertConnection(successfulConnection)
            externalConnectionsRepository.recordSyncRun(syncRun)
            syncRun
        }.getOrElse { throwable ->
            val failedAt = Clock.System.now().toEpochMilliseconds()
            val failedConnection = connection.copy(
                status = ExternalConnectionStatus.NEEDS_ATTENTION,
                lastSyncAttemptEpochMs = startedAt,
                lastSyncStatus = ExternalSyncStatus.FAILED,
                lastErrorMessage = throwable.message ?: "Indexa sync failed.",
                updatedAtEpochMs = failedAt,
            )
            val failedRun = ExternalSyncRun(
                id = syncRunId,
                connectionId = connectionId,
                providerId = ExternalProviderId.INDEXA,
                startedAtEpochMs = startedAt,
                finishedAtEpochMs = failedAt,
                status = ExternalSyncStatus.FAILED,
                importedAccounts = 0,
                importedTransactions = 0,
                importedPositions = 0,
                message = throwable.message ?: "Indexa sync failed.",
            )

            externalConnectionsRepository.upsertConnection(failedConnection)
            externalConnectionsRepository.recordSyncRun(failedRun)
            throw throwable
        }
    }

    override suspend fun disconnect(connectionId: String) {
        val connection = externalConnectionsRepository
            .observeConnections()
            .first()
            .firstOrNull { item -> item.id == connectionId && item.providerId == ExternalProviderId.INDEXA }
            ?: return

        connectionSecretStore.deleteSecret(
            providerId = ExternalProviderId.INDEXA,
            connectionId = connection.id,
        )
        externalConnectionsRepository.deleteConnection(connection.id)
    }

    override suspend fun fetchPerformanceHistory(localAccountId: String): IndexaPerformanceHistory? {
        val localAccount = ledgerRepository.observeAccounts(includeArchived = true)
            .first()
            .firstOrNull { account -> account.id == localAccountId }
            ?: run {
                AppLogger.debug(
                    tag = "IndexaSync",
                    message = "No local account found for performance request: $localAccountId",
                )
                return null
            }
        AppLogger.debug(
            tag = "IndexaSync",
            message = "Fetching performance history for local account ${localAccount.id} (${localAccount.name}), provider account ${localAccount.externalAccountId ?: "unknown"}",
        )
        val connections = externalConnectionsRepository.observeConnections().first()
            .filter { connection -> connection.providerId == ExternalProviderId.INDEXA }
        AppLogger.debug(
            tag = "IndexaSync",
            message = "Found ${connections.size} Indexa connection(s) while resolving performance history",
        )
        val linkedConnection = connections.firstNotNullOfOrNull { connection ->
            val link = externalConnectionsRepository.observeAccountLinks(connection.id)
                .first()
                .firstOrNull { accountLink -> accountLink.localAccountId == localAccountId }
            if (link != null) {
                connection to link
            } else {
                null
            }
        } ?: connections.firstOrNull()?.let { connection ->
            val providerAccountId = localAccount.externalAccountId ?: return@let null
            connection to ExternalAccountLink(
                connectionId = connection.id,
                providerAccountId = providerAccountId,
                localAccountId = localAccount.id,
                accountDisplayName = localAccount.name,
                accountTypeLabel = localAccount.type.name,
                currencyCode = localAccount.currencyCode,
                lastImportedAtEpochMs = localAccount.lastSyncedAtEpochMs,
            )
        } ?: run {
            AppLogger.debug(
                tag = "IndexaSync",
                message = "Could not resolve an Indexa connection/account link for local account ${localAccount.id}",
            )
            return null
        }

        val accessToken = connectionSecretStore.readSecret(
            providerId = ExternalProviderId.INDEXA,
            connectionId = linkedConnection.first.id,
        ) ?: run {
            AppLogger.debug(
                tag = "IndexaSync",
                message = "No stored Indexa token found for connection ${linkedConnection.first.id}",
            )
            return null
        }

        return runCatching {
            apiClient.fetchPerformance(
                accessToken = accessToken,
                accountNumber = linkedConnection.second.providerAccountId,
            )
        }.onSuccess { history ->
            AppLogger.debug(
                tag = "IndexaSync",
                message = "Fetched Indexa performance history for ${linkedConnection.second.providerAccountId}: valueHistory=${history.valueHistory.size}, normalizedHistory=${history.normalizedHistory.size}",
            )
        }.onFailure { throwable ->
            AppLogger.error(
                tag = "IndexaSync",
                message = "Indexa performance fetch failed for ${linkedConnection.second.providerAccountId}: ${throwable.message}",
                throwable = throwable,
            )
        }.getOrNull()
    }

    private fun buildSuggestedConnectionName(fullName: String?): String =
        fullName?.trim()?.takeIf(String::isNotBlank)?.let { name ->
            "Indexa - $name"
        } ?: "Indexa Capital"

    private fun resolveExistingAccount(
        existingAccounts: List<Account>,
        existingLink: ExternalAccountLink?,
        providerAccount: IndexaAccountSummary,
    ): Account? {
        val linkedAccountId = existingLink?.localAccountId
        return existingAccounts.firstOrNull { account ->
            account.id == linkedAccountId
        } ?: existingAccounts.firstOrNull { account ->
            account.sourceType == AccountSourceType.API_SYNC &&
                account.sourceProvider == INDEXA_PROVIDER_NAME &&
                account.externalAccountId == providerAccount.accountNumber
        }
    }
}

private fun IndexaAccountSummary.toLocalAccount(
    localAccountId: String,
    existingAccount: Account?,
    syncedAtEpochMs: Long,
    portfolio: IndexaPortfolioSnapshot?,
): Account = Account(
    id = localAccountId,
    name = displayName,
    type = mapIndexaAccountType(productType),
    currencyCode = normalizeIndexaCurrencyCode(currencyCode)
        ?: existingAccount?.currencyCode
        ?: DEFAULT_INDEXA_CURRENCY_CODE,
    openingBalanceMinor = portfolio?.totalMarketValue.toMinorAmount()
        ?: currentValuation.toMinorAmount()
        ?: existingAccount?.openingBalanceMinor
        ?: 0L,
    sourceType = AccountSourceType.API_SYNC,
    sourceProvider = INDEXA_PROVIDER_NAME,
    externalAccountId = accountNumber,
    lastSyncedAtEpochMs = syncedAtEpochMs,
    isArchived = existingAccount?.isArchived ?: false,
    createdAtEpochMs = existingAccount?.createdAtEpochMs ?: syncedAtEpochMs,
    updatedAtEpochMs = syncedAtEpochMs,
)

private fun mapIndexaAccountType(productType: String?): AccountType = when (productType?.lowercase()) {
    "mutual", "pension" -> AccountType.INVESTMENT
    else -> AccountType.INVESTMENT
}

private fun buildSyncedAccountId(
    providerAccountId: String,
    timestampMs: Long,
    index: Int,
): String = "account-indexa-${providerAccountId.lowercase()}-$timestampMs-$index"

private fun IndexaPortfolioSnapshot.toInvestmentPositions(
    localAccountId: String,
    syncedAtEpochMs: Long,
): List<InvestmentPosition> = positions.mapIndexed { index, position ->
    position.toInvestmentPosition(
        accountId = localAccountId,
        providerAccountId = accountNumber,
        valuationDate = valuationDate,
        syncedAtEpochMs = syncedAtEpochMs,
        index = index,
    )
}

private fun IndexaPortfolioSnapshot.toAccountValuationSnapshot(
    account: Account,
    capturedAtEpochMs: Long,
): AccountValuationSnapshot? {
    val valueMinor = totalMarketValue.toMinorAmount() ?: return null
    return AccountValuationSnapshot(
        id = "snapshot-${account.id}-$capturedAtEpochMs",
        accountId = account.id,
        sourceProvider = INDEXA_PROVIDER_NAME,
        currencyCode = account.currencyCode,
        valueMinor = valueMinor,
        valuationDate = valuationDate,
        capturedAtEpochMs = capturedAtEpochMs,
    )
}

private fun IndexaPortfolioPosition.toInvestmentPosition(
    accountId: String,
    providerAccountId: String,
    valuationDate: String?,
    syncedAtEpochMs: Long,
    index: Int,
): InvestmentPosition = InvestmentPosition(
    id = buildInvestmentPositionId(
        accountId = accountId,
        instrumentIsin = isin,
        instrumentName = name,
        index = index,
    ),
    accountId = accountId,
    providerAccountId = providerAccountId,
    instrumentIsin = isin,
    instrumentName = name,
    assetClass = assetClass,
    titles = titles,
    price = price,
    marketValueMinor = marketValue.toMinorAmount(),
    costAmountMinor = costAmount.toMinorAmount(),
    valuationDate = valuationDate,
    updatedAtEpochMs = syncedAtEpochMs,
)

private fun buildSyncSuccessMessage(
    importedAccounts: Int,
    importedTransactions: Int,
    importedPositions: Int,
): String {
    val accountLabel = if (importedAccounts == 1) "account" else "accounts"
    val transactionLabel = if (importedTransactions == 1) "cash transaction" else "cash transactions"
    val positionLabel = if (importedPositions == 1) "position" else "positions"
    return "Imported $importedAccounts Indexa $accountLabel, $importedTransactions $transactionLabel, and $importedPositions portfolio $positionLabel into the local ledger."
}

private fun buildInvestmentPositionId(
    accountId: String,
    instrumentIsin: String?,
    instrumentName: String,
    index: Int,
): String {
    val identifier = instrumentIsin?.takeIf(String::isNotBlank) ?: instrumentName
    val slug = identifier
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "position" }

    return "position-$accountId-$slug-$index"
}
