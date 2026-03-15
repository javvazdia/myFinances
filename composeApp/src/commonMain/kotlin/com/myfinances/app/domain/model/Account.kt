package com.myfinances.app.domain.model

data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val currencyCode: String,
    val openingBalanceMinor: Long,
    val sourceType: AccountSourceType,
    val sourceProvider: String?,
    val externalAccountId: String?,
    val lastSyncedAtEpochMs: Long?,
    val isArchived: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

enum class AccountType {
    CHECKING,
    SAVINGS,
    CASH,
    CREDIT_CARD,
    INVESTMENT,
    LOAN,
}

enum class AccountSourceType {
    MANUAL,
    API_SYNC,
    FILE_IMPORT,
}
