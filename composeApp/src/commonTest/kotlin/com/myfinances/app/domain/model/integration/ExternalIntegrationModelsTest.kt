package com.myfinances.app.domain.model.integration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalIntegrationModelsTest {
    @Test
    fun providerCatalogIncludesIndexaAsScaffoldedProvider() {
        val indexa = ExternalProviderCatalog.availableProviders.first { provider ->
            provider.id == ExternalProviderId.INDEXA
        }

        assertEquals(ExternalIntegrationStage.SCAFFOLDED, indexa.stage)
        assertTrue(indexa.capabilities.contains(ExternalProviderCapability.ACCOUNT_DISCOVERY))
        assertTrue(indexa.capabilities.contains(ExternalProviderCapability.HOLDINGS_SYNC))
    }
}
