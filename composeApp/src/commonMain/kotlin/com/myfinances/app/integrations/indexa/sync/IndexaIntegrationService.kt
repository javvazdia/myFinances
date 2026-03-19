package com.myfinances.app.integrations.indexa.sync

import com.myfinances.app.domain.model.integration.ExternalConnection
import com.myfinances.app.domain.model.integration.ExternalConnectionPreview
import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.model.integration.ExternalSyncRun
import com.myfinances.app.integrations.ExternalProviderConnector
import com.myfinances.app.integrations.indexa.model.IndexaPerformanceHistory

interface IndexaIntegrationService : ExternalProviderConnector {
    override val providerId: ExternalProviderId
        get() = ExternalProviderId.INDEXA

    override suspend fun testConnection(secret: String): ExternalConnectionPreview

    override suspend fun connect(secret: String): ExternalConnection

    override suspend fun runSync(connectionId: String): ExternalSyncRun

    override suspend fun disconnect(connectionId: String)

    suspend fun fetchPerformanceHistory(localAccountId: String): IndexaPerformanceHistory?
}
