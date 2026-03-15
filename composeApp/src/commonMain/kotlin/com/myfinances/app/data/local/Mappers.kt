package com.myfinances.app.data.local

import com.myfinances.app.data.local.entity.AccountEntity
import com.myfinances.app.data.local.entity.CategoryEntity
import com.myfinances.app.data.local.entity.TransactionEntity
import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.FinanceTransaction

object AccountEntityMapper {
    fun toDomain(entity: AccountEntity): Account = Account(
        id = entity.id,
        name = entity.name,
        type = entity.type,
        currencyCode = entity.currencyCode,
        openingBalanceMinor = entity.openingBalanceMinor,
        sourceType = entity.sourceType,
        sourceProvider = entity.sourceProvider,
        externalAccountId = entity.externalAccountId,
        lastSyncedAtEpochMs = entity.lastSyncedAtEpochMs,
        isArchived = entity.isArchived,
        createdAtEpochMs = entity.createdAtEpochMs,
        updatedAtEpochMs = entity.updatedAtEpochMs,
    )

    fun toEntity(model: Account): AccountEntity = AccountEntity(
        id = model.id,
        name = model.name,
        type = model.type,
        currencyCode = model.currencyCode,
        openingBalanceMinor = model.openingBalanceMinor,
        sourceType = model.sourceType,
        sourceProvider = model.sourceProvider,
        externalAccountId = model.externalAccountId,
        lastSyncedAtEpochMs = model.lastSyncedAtEpochMs,
        isArchived = model.isArchived,
        createdAtEpochMs = model.createdAtEpochMs,
        updatedAtEpochMs = model.updatedAtEpochMs,
    )
}

object CategoryEntityMapper {
    fun toDomain(entity: CategoryEntity): Category = Category(
        id = entity.id,
        name = entity.name,
        kind = entity.kind,
        colorHex = entity.colorHex,
        iconKey = entity.iconKey,
        isSystem = entity.isSystem,
        isArchived = entity.isArchived,
        createdAtEpochMs = entity.createdAtEpochMs,
    )

    fun toEntity(model: Category): CategoryEntity = CategoryEntity(
        id = model.id,
        name = model.name,
        kind = model.kind,
        colorHex = model.colorHex,
        iconKey = model.iconKey,
        isSystem = model.isSystem,
        isArchived = model.isArchived,
        createdAtEpochMs = model.createdAtEpochMs,
    )
}

object TransactionEntityMapper {
    fun toDomain(entity: TransactionEntity): FinanceTransaction = FinanceTransaction(
        id = entity.id,
        accountId = entity.accountId,
        categoryId = entity.categoryId,
        type = entity.type,
        amountMinor = entity.amountMinor,
        currencyCode = entity.currencyCode,
        merchantName = entity.merchantName,
        note = entity.note,
        sourceProvider = entity.sourceProvider,
        externalTransactionId = entity.externalTransactionId,
        postedAtEpochMs = entity.postedAtEpochMs,
        createdAtEpochMs = entity.createdAtEpochMs,
        updatedAtEpochMs = entity.updatedAtEpochMs,
    )

    fun toEntity(model: FinanceTransaction): TransactionEntity = TransactionEntity(
        id = model.id,
        accountId = model.accountId,
        categoryId = model.categoryId,
        type = model.type,
        amountMinor = model.amountMinor,
        currencyCode = model.currencyCode,
        merchantName = model.merchantName,
        note = model.note,
        sourceProvider = model.sourceProvider,
        externalTransactionId = model.externalTransactionId,
        postedAtEpochMs = model.postedAtEpochMs,
        createdAtEpochMs = model.createdAtEpochMs,
        updatedAtEpochMs = model.updatedAtEpochMs,
    )
}
