package com.myfinances.app.integrations.cajaingenieros.sync

import com.myfinances.app.domain.model.integration.ExternalConnection
import com.myfinances.app.domain.model.integration.ExternalConnectionPreview
import com.myfinances.app.domain.model.integration.ExternalDiscoveredAccountPreview
import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.model.integration.ExternalSyncRun
import com.myfinances.app.integrations.cajaingenieros.api.CajaIngenierosApiClient
import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosRegistrationMetadata

class ScaffoldedCajaIngenierosIntegrationService(
    private val apiClient: CajaIngenierosApiClient,
) : CajaIngenierosIntegrationService {
    override val providerId: ExternalProviderId
        get() = ExternalProviderId.CAJA_INGENIEROS

    override suspend fun fetchRegistrationMetadata(): CajaIngenierosRegistrationMetadata =
        apiClient.fetchRegistrationMetadata()

    override suspend fun testConnection(secret: String): ExternalConnectionPreview {
        val metadata = fetchRegistrationMetadata()
        return ExternalConnectionPreview(
            suggestedConnectionName = "Caja Ingenieros",
            ownerLabel = null,
            discoveredAccounts = emptyList<ExternalDiscoveredAccountPreview>(),
        ).copy(
            ownerLabel = metadata.onboardingMode,
        )
    }

    override suspend fun connect(secret: String): ExternalConnection {
        error(
            "Caja Ingenieros is scaffolded but not connected yet. The next implementation step is the live OAuth/PSD2 onboarding and account discovery flow.",
        )
    }

    override suspend fun runSync(connectionId: String): ExternalSyncRun {
        error(
            "Caja Ingenieros sync is not implemented yet. The next step is importing accounts, balances, and transactions from the live API.",
        )
    }

    override suspend fun disconnect(connectionId: String) {
        error(
            "Caja Ingenieros disconnect is not implemented yet because the live connection flow has not been added.",
        )
    }
}
