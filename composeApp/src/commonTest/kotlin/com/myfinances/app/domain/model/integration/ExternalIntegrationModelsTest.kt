package com.myfinances.app.domain.model.integration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalIntegrationModelsTest {
    @Test
    fun providerCatalogIncludesIndexaAsActiveProvider() {
        val indexa = ExternalProviderCatalog.availableProviders.first { provider ->
            provider.id == ExternalProviderId.INDEXA
        }

        assertEquals(ExternalIntegrationStage.ACTIVE, indexa.stage)
        assertTrue(indexa.capabilities.contains(ExternalProviderCapability.ACCOUNT_DISCOVERY))
        assertTrue(indexa.capabilities.contains(ExternalProviderCapability.HOLDINGS_SYNC))
    }
}
