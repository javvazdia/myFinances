package com.myfinances.app.integrations

import com.myfinances.app.domain.model.integration.ExternalConnection
import com.myfinances.app.domain.model.integration.ExternalConnectionPreview
import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.model.integration.ExternalSyncRun

interface ExternalProviderConnector {
    val providerId: ExternalProviderId

    suspend fun testConnection(secret: String): ExternalConnectionPreview

    suspend fun connect(secret: String): ExternalConnection

    suspend fun runSync(connectionId: String): ExternalSyncRun

    suspend fun disconnect(connectionId: String)
}
