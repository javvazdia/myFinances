package com.myfinances.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.myfinances.app.data.local.entity.CategoryEntity
import com.myfinances.app.domain.model.CategoryKind
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE is_archived = 0 ORDER BY name ASC")
    fun observeActiveCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE is_archived = 0 AND kind = :kind ORDER BY name ASC")
    fun observeActiveCategoriesByKind(kind: CategoryKind): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :categoryId LIMIT 1")
    suspend fun getCategoryById(categoryId: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun countCategories(): Int

    @Upsert
    suspend fun upsertCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)
}
