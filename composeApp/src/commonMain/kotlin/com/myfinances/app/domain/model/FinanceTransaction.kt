package com.myfinances.app.domain.model

data class FinanceTransaction(
    val id: String,
    val accountId: String,
    val categoryId: String?,
    val type: TransactionType,
    val amountMinor: Long,
    val currencyCode: String,
    val merchantName: String?,
    val note: String?,
    val sourceProvider: String?,
    val externalTransactionId: String?,
    val postedAtEpochMs: Long,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

enum class TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER,
    ADJUSTMENT,
}
