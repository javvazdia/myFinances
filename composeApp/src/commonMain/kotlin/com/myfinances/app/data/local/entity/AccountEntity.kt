package com.myfinances.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.myfinances.app.domain.model.AccountSourceType
import com.myfinances.app.domain.model.AccountType

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "type")
    val type: AccountType,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "opening_balance_minor")
    val openingBalanceMinor: Long,
    @ColumnInfo(name = "source_type")
    val sourceType: AccountSourceType,
    @ColumnInfo(name = "source_provider")
    val sourceProvider: String? = null,
    @ColumnInfo(name = "external_account_id")
    val externalAccountId: String? = null,
    @ColumnInfo(name = "last_synced_at_epoch_ms")
    val lastSyncedAtEpochMs: Long? = null,
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)
