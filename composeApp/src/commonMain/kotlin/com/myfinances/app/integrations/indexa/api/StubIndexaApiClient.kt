package com.myfinances.app.integrations.indexa.api

import com.myfinances.app.integrations.indexa.model.IndexaAccountSummary
import com.myfinances.app.integrations.indexa.model.IndexaCashTransaction
import com.myfinances.app.integrations.indexa.model.IndexaInstrumentTransaction
import com.myfinances.app.integrations.indexa.model.IndexaPerformanceHistory
import com.myfinances.app.integrations.indexa.model.IndexaPortfolioPosition
import com.myfinances.app.integrations.indexa.model.IndexaPortfolioSnapshot
import com.myfinances.app.integrations.indexa.model.IndexaUserProfile

class StubIndexaApiClient : IndexaApiClient {
    override suspend fun fetchUserProfile(accessToken: String): IndexaUserProfile =
        IndexaUserProfile(
            email = "indexa@example.com",
            fullName = "Indexa Demo",
            documentId = null,
            accounts = fetchAccounts(accessToken),
        )

    override suspend fun fetchAccounts(accessToken: String): List<IndexaAccountSummary> = listOf(
        IndexaAccountSummary(
            accountNumber = "INDEXA-DEMO-01",
            displayName = "Indexa Global Portfolio",
            productType = "mutual",
            providerCode = "INV",
            currencyCode = "EUR",
            currentValuation = 12_450.32,
        ),
    )

    override suspend fun fetchPortfolio(
        accessToken: String,
        accountNumber: String,
    ): IndexaPortfolioSnapshot = IndexaPortfolioSnapshot(
        accountNumber = accountNumber,
        valuationDate = "2026-03-18",
        totalMarketValue = 12_450.32,
        positions = listOf(
            IndexaPortfolioPosition(
                isin = "IE0032126645",
                name = "Vanguard US 500 Stk Idx -Inst",
                assetClass = "equity_north_america",
                titles = 12.45,
                price = 244.1,
                marketValue = 3_038.05,
                costAmount = 2_800.0,
            ),
        ),
    )

    override suspend fun fetchPerformance(
        accessToken: String,
        accountNumber: String,
    ): IndexaPerformanceHistory = IndexaPerformanceHistory(
        accountNumber = accountNumber,
        valueHistory = linkedMapOf(
            "2025-10-31" to 11_980.0,
            "2025-11-30" to 12_110.0,
            "2025-12-31" to 12_220.0,
            "2026-01-31" to 12_320.0,
            "2026-02-28" to 12_390.0,
            "2026-03-18" to 12_450.32,
        ),
        normalizedHistory = linkedMapOf(
            "2025-10-31" to 0.98,
            "2025-11-30" to 1.01,
            "2025-12-31" to 1.03,
            "2026-01-31" to 1.05,
            "2026-02-28" to 1.07,
            "2026-03-18" to 1.09,
        ),
    )

    override suspend fun fetchCashTransactions(
        accessToken: String,
        accountNumber: String,
    ): List<IndexaCashTransaction> = listOf(
        IndexaCashTransaction(
            reference = "cash-demo-1",
            accountNumber = accountNumber,
            date = "2026-03-01",
            amount = -3.0,
            currencyCode = "EUR",
            fees = 0.0,
            operationCode = 224,
            operationType = "CARGO COMISIONES",
            comments = "Demo fee transaction",
        ),
    )

    override suspend fun fetchInstrumentTransactions(
        accessToken: String,
        accountNumber: String,
    ): List<IndexaInstrumentTransaction> = listOf(
        IndexaInstrumentTransaction(
            reference = "instrument-demo-1",
            accountNumber = accountNumber,
            date = "2026-03-01",
            amount = 500.0,
            currencyCode = "EUR",
            operationType = "SUSCRIPCION FONDOS",
            instrumentName = "Vanguard US 500 Stk Idx -Inst",
            instrumentIsin = "IE0032126645",
            titles = 2.05,
            price = 243.9,
        ),
    )
}
