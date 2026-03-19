package com.myfinances.app.integrations.cajaingenieros.sync

import com.myfinances.app.integrations.cajaingenieros.api.StubCajaIngenierosApiClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScaffoldedCajaIngenierosIntegrationServiceTest {
    private val service = ScaffoldedCajaIngenierosIntegrationService(
        apiClient = StubCajaIngenierosApiClient(),
    )

    @Test
    fun exposesScaffoldedPreviewMetadata() = kotlinx.coroutines.test.runTest {
        val preview = service.testConnection("sandbox-placeholder")

        assertEquals("Caja Ingenieros", preview.suggestedConnectionName)
        assertTrue(preview.ownerLabel?.contains("OAuth", ignoreCase = true) == true)
        assertTrue(preview.discoveredAccounts.isEmpty())
    }

    @Test
    fun connectExplainsThatLiveOnboardingIsStillPending() = kotlinx.coroutines.test.runTest {
        val error = assertFailsWith<IllegalStateException> {
            service.connect("sandbox-placeholder")
        }

        assertTrue(error.message?.contains("not connected yet", ignoreCase = true) == true)
    }
}
