package com.myfinances.app.data.integration

import android.content.Context
import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.repository.ConnectionSecretStore

class AndroidConnectionSecretStore(
    context: Context,
) : ConnectionSecretStore {
    private val preferences = context.getSharedPreferences("connection_secrets", Context.MODE_PRIVATE)

    override suspend fun saveSecret(
        providerId: ExternalProviderId,
        connectionId: String,
        secret: String,
    ) {
        preferences.edit()
            .putString(key(providerId, connectionId), secret)
            .apply()
    }

    override suspend fun readSecret(
        providerId: ExternalProviderId,
        connectionId: String,
    ): String? = preferences.getString(key(providerId, connectionId), null)

    override suspend fun deleteSecret(
        providerId: ExternalProviderId,
        connectionId: String,
    ) {
        preferences.edit()
            .remove(key(providerId, connectionId))
            .apply()
    }

    private fun key(providerId: ExternalProviderId, connectionId: String): String =
        "${providerId.name}:$connectionId"
}
