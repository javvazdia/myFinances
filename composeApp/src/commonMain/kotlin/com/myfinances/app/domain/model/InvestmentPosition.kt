package com.myfinances.app.domain.model

data class InvestmentPosition(
    val id: String,
    val accountId: String,
    val providerAccountId: String,
    val instrumentIsin: String?,
    val instrumentName: String,
    val assetClass: String?,
    val titles: Double?,
    val price: Double?,
    val marketValueMinor: Long?,
    val costAmountMinor: Long?,
    val valuationDate: String?,
    val updatedAtEpochMs: Long,
)
