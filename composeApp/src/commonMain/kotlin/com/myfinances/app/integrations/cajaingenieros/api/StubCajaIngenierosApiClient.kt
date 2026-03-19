package com.myfinances.app.integrations.cajaingenieros.api

import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosAccountSummary
import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosBalanceSnapshot
import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosRegistrationMetadata
import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosTransaction

class StubCajaIngenierosApiClient : CajaIngenierosApiClient {
    override suspend fun fetchRegistrationMetadata(): CajaIngenierosRegistrationMetadata =
        CajaIngenierosRegistrationMetadata(
            onboardingMode = "Developer portal + OAuth app credentials",
            supportsSandbox = true,
            notes = "Caja Ingenieros is scaffolded as a PSD2/open-banking style provider. Live auth and API calls are still pending.",
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
