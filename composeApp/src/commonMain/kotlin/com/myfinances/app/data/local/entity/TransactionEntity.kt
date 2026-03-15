package com.myfinances.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.myfinances.app.domain.model.TransactionType

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["category_id"]),
        Index(value = ["posted_at_epoch_ms"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "account_id")
    val accountId: String,
    @ColumnInfo(name = "category_id")
    val categoryId: String? = null,
    @ColumnInfo(name = "type")
    val type: TransactionType,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "merchant_name")
    val merchantName: String? = null,
    @ColumnInfo(name = "note")
    val note: String? = null,
    @ColumnInfo(name = "source_provider")
    val sourceProvider: String? = null,
    @ColumnInfo(name = "external_transaction_id")
    val externalTransactionId: String? = null,
    @ColumnInfo(name = "posted_at_epoch_ms")
    val postedAtEpochMs: Long,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)
