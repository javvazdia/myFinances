package com.myfinances.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.myfinances.app.domain.model.integration.ExternalConnectionStatus
import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.model.integration.ExternalSyncStatus

@Entity(tableName = "external_connections")
data class ExternalConnectionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "provider_id")
    val providerId: ExternalProviderId,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "status")
    val status: ExternalConnectionStatus,
    @ColumnInfo(name = "external_user_id")
    val externalUserId: String? = null,
    @ColumnInfo(name = "last_successful_sync_epoch_ms")
    val lastSuccessfulSyncEpochMs: Long? = null,
    @ColumnInfo(name = "last_sync_attempt_epoch_ms")
    val lastSyncAttemptEpochMs: Long? = null,
    @ColumnInfo(name = "last_sync_status")
    val lastSyncStatus: ExternalSyncStatus,
    @ColumnInfo(name = "last_error_message")
    val lastErrorMessage: String? = null,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)
