package com.myfinances.app.presentation.settings

import com.myfinances.app.domain.model.Category
import com.myfinances.app.domain.model.CategoryKind
import com.myfinances.app.domain.model.integration.ExternalAccountLink
import com.myfinances.app.domain.model.integration.ExternalConnection
import com.myfinances.app.domain.model.integration.ExternalDiscoveredAccountPreview
import com.myfinances.app.domain.model.integration.ExternalConnectionStatus
import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.model.integration.ExternalSyncRun
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

    @Test
    fun exposesSelectedConnectionFromState() {
        val connection = ExternalConnection(
            id = "conn-indexa-1",
            providerId = ExternalProviderId.INDEXA,
            displayName = "Indexa Capital",
            status = ExternalConnectionStatus.CONNECTED,
            externalUserId = "user@example.com",
            lastSuccessfulSyncEpochMs = null,
            lastSyncAttemptEpochMs = null,
            lastSyncStatus = ExternalSyncStatus.IDLE,
            lastErrorMessage = null,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )
        val uiState = SettingsUiState(
            connections = listOf(connection),
            selectedConnectionId = connection.id,
        )

        assertEquals(connection.id, uiState.selectedConnection?.id)
    }

    @Test
    fun keepsSyncRunsMappedByConnection() {
        val syncRun = ExternalSyncRun(
            id = "sync-1",
            connectionId = "conn-indexa-1",
            providerId = ExternalProviderId.INDEXA,
            startedAtEpochMs = 1L,
            finishedAtEpochMs = 2L,
            status = ExternalSyncStatus.SUCCESS,
            importedAccounts = 1,
            importedTransactions = 4,
            importedPositions = 2,
            message = "Imported data.",
        )
        val uiState = SettingsUiState(
            syncRunsByConnection = mapOf("conn-indexa-1" to listOf(syncRun)),
        )

        assertEquals(1, uiState.syncRunsByConnection["conn-indexa-1"]?.size)
        assertEquals("sync-1", uiState.syncRunsByConnection["conn-indexa-1"]?.first()?.id)
    }

    @Test
    fun keepsAccountLinksMappedByConnection() {
        val accountLink = ExternalAccountLink(
            connectionId = "conn-indexa-1",
            providerAccountId = "ACC-1",
            localAccountId = "account-1",
            accountDisplayName = "Indexa profile",
            accountTypeLabel = "Mutual",
            currencyCode = "EUR",
            lastImportedAtEpochMs = 10L,
        )
        val uiState = SettingsUiState(
            connections = listOf(
                ExternalConnection(
                    id = "conn-indexa-1",
                    providerId = ExternalProviderId.INDEXA,
                    displayName = "Indexa Capital",
                    status = ExternalConnectionStatus.CONNECTED,
                    externalUserId = null,
                    lastSuccessfulSyncEpochMs = null,
                    lastSyncAttemptEpochMs = null,
                    lastSyncStatus = ExternalSyncStatus.IDLE,
                    lastErrorMessage = null,
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L,
                ),
            ),
            selectedConnectionId = "conn-indexa-1",
            accountLinksByConnection = mapOf("conn-indexa-1" to listOf(accountLink)),
        )

        assertEquals(1, uiState.selectedConnectionAccountLinks.size)
        assertEquals("ACC-1", uiState.selectedConnectionAccountLinks.first().providerAccountId)
    }

    @Test
    fun formatsSyncRunSummaryCounts() {
        val syncRun = ExternalSyncRun(
            id = "sync-1",
            connectionId = "conn-indexa-1",
            providerId = ExternalProviderId.INDEXA,
            startedAtEpochMs = 1L,
            finishedAtEpochMs = 2L,
            status = ExternalSyncStatus.SUCCESS,
            importedAccounts = 1,
            importedTransactions = 3,
            importedPositions = 2,
            message = null,
        )

        assertEquals("1 account, 3 transactions, 2 positions", buildSyncRunSummary(syncRun))
    }

    @Test
    fun buildsLinkedAccountSubtitle() {
        val accountLink = ExternalAccountLink(
            connectionId = "conn-indexa-1",
            providerAccountId = "ACC-1",
            localAccountId = "account-1",
            accountDisplayName = "Indexa profile",
            accountTypeLabel = "Mutual",
            currencyCode = "EUR",
            lastImportedAtEpochMs = 10L,
        )

        assertEquals("Mutual | EUR | linked locally", buildLinkedAccountSubtitle(accountLink))
    }

    @Test
    fun formatsLinkedAccountImportLabelWhenMissingImport() {
        val accountLink = ExternalAccountLink(
            connectionId = "conn-indexa-1",
            providerAccountId = "ACC-1",
            localAccountId = null,
            accountDisplayName = "Indexa profile",
            accountTypeLabel = "Mutual",
            currencyCode = "EUR",
            lastImportedAtEpochMs = null,
        )

        assertEquals("Not imported yet", buildLinkedAccountImportLabel(accountLink))
    }

    @Test
    fun exposesDefaultProviderStateForAvailableProviders() {
        val providerState = SettingsUiState().providerState(ExternalProviderId.INDEXA)

        assertEquals("", providerState.draftSecret)
        assertEquals(false, providerState.isConnecting)
    }

    @Test
    fun buildsPreviewAccountSubtitleFromGenericProviderPreview() {
        val preview = ExternalDiscoveredAccountPreview(
            providerAccountId = "ACC-1",
            displayName = "Indexa profile",
            accountTypeLabel = "Mutual",
            currencyCode = "EUR",
            balanceLabel = "1234.56 EUR",
        )

        assertEquals("Mutual | EUR | approx. 1234.56 EUR", buildPreviewAccountSubtitle(preview))
    }
}
