package com.myfinances.app.data.integration

import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.repository.ConnectionSecretStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Properties

class DesktopConnectionSecretStore : ConnectionSecretStore {
    private val mutex = Mutex()
    private val directory = File(System.getProperty("user.home"), ".myfinances").apply { mkdirs() }
    private val file = File(directory, "connection-secrets.properties")

    override suspend fun saveSecret(
        providerId: ExternalProviderId,
        connectionId: String,
        secret: String,
    ) {
        mutex.withLock {
            val properties = loadProperties()
            properties.setProperty(key(providerId, connectionId), secret)
            storeProperties(properties)
        }
    }

    override suspend fun readSecret(
        providerId: ExternalProviderId,
        connectionId: String,
    ): String? = mutex.withLock {
        loadProperties().getProperty(key(providerId, connectionId))
    }

    override suspend fun deleteSecret(
        providerId: ExternalProviderId,
        connectionId: String,
    ) {
        mutex.withLock {
            val properties = loadProperties()
            properties.remove(key(providerId, connectionId))
            storeProperties(properties)
        }
    }

    private fun key(providerId: ExternalProviderId, connectionId: String): String =
        "${providerId.name}:$connectionId"

    private fun loadProperties(): Properties = Properties().apply {
        if (file.exists()) {
            file.inputStream().use(::load)
        }
    }

    private fun storeProperties(properties: Properties) {
        file.outputStream().use { stream ->
            properties.store(stream, "myFinances connection secrets")
        }
    }
}
