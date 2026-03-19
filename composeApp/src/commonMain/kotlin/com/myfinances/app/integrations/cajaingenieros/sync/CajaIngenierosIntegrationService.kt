package com.myfinances.app.integrations.cajaingenieros.sync

import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.integrations.ExternalProviderConnector
import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosRegistrationMetadata

interface CajaIngenierosIntegrationService : ExternalProviderConnector {
    override val providerId: ExternalProviderId
        get() = ExternalProviderId.CAJA_INGENIEROS

    suspend fun fetchRegistrationMetadata(): CajaIngenierosRegistrationMetadata
}
