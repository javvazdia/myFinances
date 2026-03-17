package com.myfinances.app.integrations.indexa.api

import com.myfinances.app.integrations.indexa.model.IndexaAccountSummary
import com.myfinances.app.integrations.indexa.model.IndexaCashTransaction
import com.myfinances.app.integrations.indexa.model.IndexaInstrumentTransaction
import com.myfinances.app.integrations.indexa.model.IndexaPortfolioSnapshot
import com.myfinances.app.integrations.indexa.model.IndexaUserProfile

interface IndexaApiClient {
    suspend fun fetchUserProfile(accessToken: String): IndexaUserProfile

    suspend fun fetchAccounts(accessToken: String): List<IndexaAccountSummary>

    suspend fun fetchPortfolio(
        accessToken: String,
        accountNumber: String,
    ): IndexaPortfolioSnapshot

    suspend fun fetchCashTransactions(
        accessToken: String,
        accountNumber: String,
    ): List<IndexaCashTransaction>

    suspend fun fetchInstrumentTransactions(
        accessToken: String,
        accountNumber: String,
    ): List<IndexaInstrumentTransaction>
}
