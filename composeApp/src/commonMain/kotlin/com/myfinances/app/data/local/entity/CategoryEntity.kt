package com.myfinances.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.myfinances.app.domain.model.CategoryKind

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["name", "kind"], unique = true),
    ],
)
data class CategoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "kind")
    val kind: CategoryKind,
    @ColumnInfo(name = "color_hex")
    val colorHex: String? = null,
    @ColumnInfo(name = "icon_key")
    val iconKey: String? = null,
    @ColumnInfo(name = "is_system")
    val isSystem: Boolean = false,
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
)

