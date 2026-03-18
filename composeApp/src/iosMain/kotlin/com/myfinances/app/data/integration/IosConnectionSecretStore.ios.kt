package com.myfinances.app.data.integration

import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.repository.ConnectionSecretStore
import platform.Foundation.NSUserDefaults

class IosConnectionSecretStore : ConnectionSecretStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun saveSecret(
        providerId: ExternalProviderId,
        connectionId: String,
        secret: String,
    ) {
        defaults.setObject(secret, forKey = key(providerId, connectionId))
    }

    override suspend fun readSecret(
        providerId: ExternalProviderId,
        connectionId: String,
    ): String? = defaults.stringForKey(key(providerId, connectionId))

    override suspend fun deleteSecret(
        providerId: ExternalProviderId,
        connectionId: String,
    ) {
        defaults.removeObjectForKey(key(providerId, connectionId))
    }

    private fun key(providerId: ExternalProviderId, connectionId: String): String =
        "${providerId.name}:$connectionId"
}
