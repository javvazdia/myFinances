package com.myfinances.app.presentation.settings

import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.CategoryKind
import com.myfinances.app.domain.model.integration.ExternalConnection
import com.myfinances.app.domain.model.integration.ExternalConnectionStatus
import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.model.integration.ExternalSyncStatus
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

    @Test
    fun keepsConnectionsAlongsideCategoryState() {
        val uiState = SettingsUiState(
            connections = listOf(
                ExternalConnection(
                    id = "conn-indexa-1",
                    providerId = ExternalProviderId.INDEXA,
                    displayName = "Indexa Capital",
                    status = ExternalConnectionStatus.NOT_CONNECTED,
                    externalUserId = null,
                    lastSuccessfulSyncEpochMs = null,
                    lastSyncAttemptEpochMs = null,
                    lastSyncStatus = ExternalSyncStatus.IDLE,
                    lastErrorMessage = null,
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L,
                ),
            ),
        )

        assertEquals(1, uiState.connections.size)
        assertEquals(ExternalProviderId.INDEXA, uiState.connections.first().providerId)
    }
}
