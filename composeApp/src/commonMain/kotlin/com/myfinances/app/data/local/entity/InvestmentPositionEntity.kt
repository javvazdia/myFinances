package com.myfinances.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "investment_positions",
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
        Index(value = ["provider_account_id"]),
        Index(value = ["instrument_isin"]),
    ],
)
data class InvestmentPositionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "account_id")
    val accountId: String,
    @ColumnInfo(name = "provider_account_id")
    val providerAccountId: String,
    @ColumnInfo(name = "instrument_isin")
    val instrumentIsin: String? = null,
    @ColumnInfo(name = "instrument_name")
    val instrumentName: String,
    @ColumnInfo(name = "asset_class")
    val assetClass: String? = null,
    @ColumnInfo(name = "titles")
    val titles: Double? = null,
    @ColumnInfo(name = "price")
    val price: Double? = null,
    @ColumnInfo(name = "market_value_minor")
    val marketValueMinor: Long? = null,
    @ColumnInfo(name = "cost_amount_minor")
    val costAmountMinor: Long? = null,
    @ColumnInfo(name = "valuation_date")
    val valuationDate: String? = null,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)
