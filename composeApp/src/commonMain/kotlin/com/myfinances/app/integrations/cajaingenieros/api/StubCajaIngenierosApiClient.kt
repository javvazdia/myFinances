package com.myfinances.app.integrations.cajaingenieros.api

import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosAccountSummary
import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosAccessToken
import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosBalanceSnapshot
import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosCredentialBundle
import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosRegistrationMetadata
import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosTransaction

class StubCajaIngenierosApiClient : CajaIngenierosApiClient {
    override suspend fun fetchRegistrationMetadata(): CajaIngenierosRegistrationMetadata =
        CajaIngenierosRegistrationMetadata(
            onboardingMode = "Developer portal + OAuth app credentials",
            supportsSandbox = true,
            notes = "Caja Ingenieros uses published XS2A endpoints on api.caixaenginyers.com and an OAuth token endpoint, but signed AIS requests and PSU consent are still pending.",
        )

    override suspend fun exchangeClientCredentialsToken(
        credentials: CajaIngenierosCredentialBundle,
    ): CajaIngenierosAccessToken = CajaIngenierosAccessToken(
        accessToken = "stub-caja-token-${credentials.environment.name.lowercase()}",
        tokenType = "Bearer",
        expiresInSeconds = 3600,
        scope = "accounts_list accounts_balances accounts_transactions",
    )

    override suspend fun fetchAccounts(accessToken: String): List<CajaIngenierosAccountSummary> =
        emptyList()

    override suspend fun fetchBalance(
        accessToken: String,
        accountId: String,
    ): CajaIngenierosBalanceSnapshot = CajaIngenierosBalanceSnapshot(
        accountId = accountId,
        currencyCode = "EUR",
        availableBalance = null,
        currentBalance = null,
        capturedAtEpochMs = null,
    )

    override suspend fun fetchTransactions(
        accessToken: String,
        accountId: String,
    ): List<CajaIngenierosTransaction> = emptyList()
}
