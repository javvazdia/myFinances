package com.myfinances.app.domain.repository

import com.myfinances.app.domain.model.integration.ExternalProviderId

interface ConnectionSecretStore {
    suspend fun saveSecret(
        providerId: ExternalProviderId,
        connectionId: String,
        secret: String,
    )

    suspend fun readSecret(
        providerId: ExternalProviderId,
        connectionId: String,
    ): String?

    suspend fun deleteSecret(
        providerId: ExternalProviderId,
        connectionId: String,
    )
}
