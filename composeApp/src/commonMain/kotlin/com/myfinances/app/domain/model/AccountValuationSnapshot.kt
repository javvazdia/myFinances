package com.myfinances.app.domain.model

data class AccountValuationSnapshot(
    val id: String,
    val accountId: String,
    val sourceProvider: String?,
    val currencyCode: String,
    val valueMinor: Long,
    val valuationDate: String?,
    val capturedAtEpochMs: Long,
)
