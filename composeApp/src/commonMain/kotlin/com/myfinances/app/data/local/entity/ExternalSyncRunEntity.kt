package com.myfinances.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.model.integration.ExternalSyncStatus

@Entity(
    tableName = "external_sync_runs",
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
        Index(value = ["provider_id"]),
        Index(value = ["started_at_epoch_ms"]),
    ],
)
data class ExternalSyncRunEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "connection_id")
    val connectionId: String,
    @ColumnInfo(name = "provider_id")
    val providerId: ExternalProviderId,
    @ColumnInfo(name = "started_at_epoch_ms")
    val startedAtEpochMs: Long,
    @ColumnInfo(name = "finished_at_epoch_ms")
    val finishedAtEpochMs: Long? = null,
    @ColumnInfo(name = "status")
    val status: ExternalSyncStatus,
    @ColumnInfo(name = "imported_accounts")
    val importedAccounts: Int,
    @ColumnInfo(name = "imported_transactions")
    val importedTransactions: Int,
    @ColumnInfo(name = "imported_positions")
    val importedPositions: Int,
    @ColumnInfo(name = "message")
    val message: String? = null,
)
