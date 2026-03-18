package com.myfinances.app.data.local.db

import androidx.room.TypeConverter
import com.myfinances.app.domain.model.AccountSourceType
import com.myfinances.app.domain.model.AccountType
import com.myfinances.app.domain.model.CategoryKind
import com.myfinances.app.domain.model.TransactionType
import com.myfinances.app.domain.model.integration.ExternalConnectionStatus
import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.model.integration.ExternalSyncStatus

class FinanceTypeConverters {
    @TypeConverter
    fun fromAccountType(value: AccountType): String = value.name

    @TypeConverter
    fun toAccountType(value: String): AccountType = AccountType.valueOf(value)

    @TypeConverter
    fun fromAccountSourceType(value: AccountSourceType): String = value.name

    @TypeConverter
    fun toAccountSourceType(value: String): AccountSourceType = AccountSourceType.valueOf(value)

    @TypeConverter
    fun fromCategoryKind(value: CategoryKind): String = value.name

    @TypeConverter
    fun toCategoryKind(value: String): CategoryKind = CategoryKind.valueOf(value)

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromExternalProviderId(value: ExternalProviderId): String = value.name

    @TypeConverter
    fun toExternalProviderId(value: String): ExternalProviderId = ExternalProviderId.valueOf(value)

    @TypeConverter
    fun fromExternalConnectionStatus(value: ExternalConnectionStatus): String = value.name

    @TypeConverter
    fun toExternalConnectionStatus(value: String): ExternalConnectionStatus =
        ExternalConnectionStatus.valueOf(value)

    @TypeConverter
    fun fromExternalSyncStatus(value: ExternalSyncStatus): String = value.name

    @TypeConverter
    fun toExternalSyncStatus(value: String): ExternalSyncStatus = ExternalSyncStatus.valueOf(value)
}
