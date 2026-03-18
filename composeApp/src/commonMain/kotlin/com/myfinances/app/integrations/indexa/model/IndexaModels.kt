package com.myfinances.app.integrations.indexa.model

data class IndexaUserProfile(
    val email: String,
    val fullName: String?,
    val documentId: String?,
    val accounts: List<IndexaAccountSummary>,
)

data class IndexaAccountSummary(
    val accountNumber: String,
    val displayName: String,
    val productType: String?,
    val providerCode: String?,
    val currencyCode: String?,
    val currentValuation: Double?,
)

data class IndexaPortfolioSnapshot(
    val accountNumber: String,
    val valuationDate: String?,
    val totalMarketValue: Double?,
    val positions: List<IndexaPortfolioPosition>,
)

data class IndexaPerformanceHistory(
    val accountNumber: String,
    val valueHistory: Map<String, Double>,
    val normalizedHistory: Map<String, Double>,
)

data class IndexaPortfolioPosition(
    val isin: String?,
    val name: String,
    val assetClass: String?,
    val titles: Double?,
    val price: Double?,
    val marketValue: Double?,
    val costAmount: Double?,
)

data class IndexaCashTransaction(
    val reference: String,
    val accountNumber: String,
    val date: String,
    val amount: Double,
    val currencyCode: String,
    val fees: Double?,
    val operationCode: Int?,
    val operationType: String?,
    val comments: String?,
)

data class IndexaInstrumentTransaction(
    val reference: String,
    val accountNumber: String,
    val date: String,
    val amount: Double?,
    val currencyCode: String?,
    val operationType: String?,
    val instrumentName: String?,
    val instrumentIsin: String?,
    val titles: Double?,
    val price: Double?,
)

data class IndexaConnectionPreview(
    val profile: IndexaUserProfile,
    val suggestedConnectionName: String,
)
