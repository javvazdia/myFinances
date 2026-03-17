package com.myfinances.app.presentation.settings

import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.CategoryKind
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsScreenTest {
    @Test
    fun normalizesCategoryNameWhitespace() {
        assertEquals("Dining Out", normalizeCategoryName("  Dining   Out  "))
        assertEquals("Freelance", normalizeCategoryName("Freelance"))
    }

    @Test
    fun generatesStableCategoryPrefixes() {
        val categoryId = generateCategoryId(
            name = "Dining Out",
            kind = CategoryKind.EXPENSE,
            timestampMs = 1234L,
        )

        assertEquals(true, categoryId.startsWith("category-expense-dining-out-1234-"))
    }

    @Test
    fun exposesDeleteConfirmationNameFromSelectedCategory() {
        val uiState = SettingsUiState(
            categories = listOf(
                Category(
                    id = "cat-custom-dining",
                    name = "Dining Out",
                    kind = CategoryKind.EXPENSE,
                    colorHex = null,
                    iconKey = null,
                    isSystem = false,
                    isArchived = false,
                    createdAtEpochMs = 1L,
                ),
            ),
            deleteConfirmationCategoryId = "cat-custom-dining",
        )

        assertEquals("Dining Out", uiState.deleteConfirmationCategoryName)
    }
}
