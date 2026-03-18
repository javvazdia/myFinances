package com.myfinances.app.data.local

import com.myfinances.app.data.local.entity.AccountEntity
import com.myfinances.app.data.local.entity.CategoryEntity
import com.myfinances.app.data.local.entity.ExternalAccountLinkEntity
import com.myfinances.app.data.local.entity.ExternalConnectionEntity
import com.myfinances.app.data.local.entity.ExternalSyncRunEntity
import com.myfinances.app.data.local.entity.TransactionEntity
import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.FinanceTransaction
import com.myfinances.app.domain.model.integration.ExternalAccountLink
import com.myfinances.app.domain.model.integration.ExternalConnection
import com.myfinances.app.domain.model.integration.ExternalSyncRun

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

object ExternalConnectionEntityMapper {
    fun toDomain(entity: ExternalConnectionEntity): ExternalConnection = ExternalConnection(
        id = entity.id,
        providerId = entity.providerId,
        displayName = entity.displayName,
        status = entity.status,
        externalUserId = entity.externalUserId,
        lastSuccessfulSyncEpochMs = entity.lastSuccessfulSyncEpochMs,
        lastSyncAttemptEpochMs = entity.lastSyncAttemptEpochMs,
        lastSyncStatus = entity.lastSyncStatus,
        lastErrorMessage = entity.lastErrorMessage,
        createdAtEpochMs = entity.createdAtEpochMs,
        updatedAtEpochMs = entity.updatedAtEpochMs,
    )

    fun toEntity(model: ExternalConnection): ExternalConnectionEntity = ExternalConnectionEntity(
        id = model.id,
        providerId = model.providerId,
        displayName = model.displayName,
        status = model.status,
        externalUserId = model.externalUserId,
        lastSuccessfulSyncEpochMs = model.lastSuccessfulSyncEpochMs,
        lastSyncAttemptEpochMs = model.lastSyncAttemptEpochMs,
        lastSyncStatus = model.lastSyncStatus,
        lastErrorMessage = model.lastErrorMessage,
        createdAtEpochMs = model.createdAtEpochMs,
        updatedAtEpochMs = model.updatedAtEpochMs,
    )
}

object ExternalAccountLinkEntityMapper {
    fun toDomain(entity: ExternalAccountLinkEntity): ExternalAccountLink = ExternalAccountLink(
        connectionId = entity.connectionId,
        providerAccountId = entity.providerAccountId,
        localAccountId = entity.localAccountId,
        accountDisplayName = entity.accountDisplayName,
        accountTypeLabel = entity.accountTypeLabel,
        currencyCode = entity.currencyCode,
        lastImportedAtEpochMs = entity.lastImportedAtEpochMs,
    )

    fun toEntity(model: ExternalAccountLink): ExternalAccountLinkEntity = ExternalAccountLinkEntity(
        connectionId = model.connectionId,
        providerAccountId = model.providerAccountId,
        localAccountId = model.localAccountId,
        accountDisplayName = model.accountDisplayName,
        accountTypeLabel = model.accountTypeLabel,
        currencyCode = model.currencyCode,
        lastImportedAtEpochMs = model.lastImportedAtEpochMs,
    )
}

object ExternalSyncRunEntityMapper {
    fun toDomain(entity: ExternalSyncRunEntity): ExternalSyncRun = ExternalSyncRun(
        id = entity.id,
        connectionId = entity.connectionId,
        providerId = entity.providerId,
        startedAtEpochMs = entity.startedAtEpochMs,
        finishedAtEpochMs = entity.finishedAtEpochMs,
        status = entity.status,
        importedAccounts = entity.importedAccounts,
        importedTransactions = entity.importedTransactions,
        importedPositions = entity.importedPositions,
        message = entity.message,
    )

    fun toEntity(model: ExternalSyncRun): ExternalSyncRunEntity = ExternalSyncRunEntity(
        id = model.id,
        connectionId = model.connectionId,
        providerId = model.providerId,
        startedAtEpochMs = model.startedAtEpochMs,
        finishedAtEpochMs = model.finishedAtEpochMs,
        status = model.status,
        importedAccounts = model.importedAccounts,
        importedTransactions = model.importedTransactions,
        importedPositions = model.importedPositions,
        message = model.message,
    )
}
