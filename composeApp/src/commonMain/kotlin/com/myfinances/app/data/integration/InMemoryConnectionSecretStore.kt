package com.myfinances.app.data.integration

import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.repository.ConnectionSecretStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryConnectionSecretStore : ConnectionSecretStore {
    private val mutex = Mutex()
    private val secrets = mutableMapOf<String, String>()

    override suspend fun saveSecret(
        providerId: ExternalProviderId,
        connectionId: String,
        secret: String,
    ) {
        mutex.withLock {
            secrets[key(providerId, connectionId)] = secret
        }
    }

    override suspend fun readSecret(
        providerId: ExternalProviderId,
        connectionId: String,
    ): String? = mutex.withLock {
        secrets[key(providerId, connectionId)]
    }

    override suspend fun deleteSecret(
        providerId: ExternalProviderId,
        connectionId: String,
    ) {
        mutex.withLock {
            secrets.remove(key(providerId, connectionId))
        }
    }

    private fun key(
        providerId: ExternalProviderId,
        connectionId: String,
    ): String = "${providerId.name}:$connectionId"
}
