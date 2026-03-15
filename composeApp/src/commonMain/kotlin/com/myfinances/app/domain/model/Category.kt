package com.myfinances.app.domain.model

data class Category(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val colorHex: String?,
    val iconKey: String?,
    val isSystem: Boolean,
    val isArchived: Boolean,
    val createdAtEpochMs: Long,
)

enum class CategoryKind {
    INCOME,
    EXPENSE,
    TRANSFER,
}

