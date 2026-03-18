package com.myfinances.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "account_valuation_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["captured_at_epoch_ms"]),
    ],
)
data class AccountValuationSnapshotEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "account_id")
    val accountId: String,
    @ColumnInfo(name = "source_provider")
    val sourceProvider: String? = null,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "value_minor")
    val valueMinor: Long,
    @ColumnInfo(name = "valuation_date")
    val valuationDate: String? = null,
    @ColumnInfo(name = "captured_at_epoch_ms")
    val capturedAtEpochMs: Long,
)
