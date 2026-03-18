package com.myfinances.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "external_account_links",
    primaryKeys = ["connection_id", "provider_account_id"],
    foreignKeys = [
        ForeignKey(
            entity = ExternalConnectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["connection_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["connection_id"]),
        Index(value = ["local_account_id"]),
    ],
)
data class ExternalAccountLinkEntity(
    @ColumnInfo(name = "connection_id")
    val connectionId: String,
    @ColumnInfo(name = "provider_account_id")
    val providerAccountId: String,
    @ColumnInfo(name = "local_account_id")
    val localAccountId: String? = null,
    @ColumnInfo(name = "account_display_name")
    val accountDisplayName: String,
    @ColumnInfo(name = "account_type_label")
    val accountTypeLabel: String? = null,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String? = null,
    @ColumnInfo(name = "last_imported_at_epoch_ms")
    val lastImportedAtEpochMs: Long? = null,
)
