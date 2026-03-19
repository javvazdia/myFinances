package com.myfinances.app.integrations.cajaingenieros.api

import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosAccountSummary
import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosBalanceSnapshot
import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosRegistrationMetadata
import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosTransaction

interface CajaIngenierosApiClient {
    suspend fun fetchRegistrationMetadata(): CajaIngenierosRegistrationMetadata

    suspend fun fetchAccounts(accessToken: String): List<CajaIngenierosAccountSummary>

    suspend fun fetchBalance(
        accessToken: String,
        accountId: String,
    ): CajaIngenierosBalanceSnapshot

    suspend fun fetchTransactions(
        accessToken: String,
        accountId: String,
    ): List<CajaIngenierosTransaction>
}
