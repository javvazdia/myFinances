package com.myfinances.app.integrations.indexa.sync

import com.myfinances.app.domain.model.integration.ExternalConnection
import com.myfinances.app.domain.model.integration.ExternalSyncRun
import com.myfinances.app.integrations.indexa.model.IndexaConnectionPreview

interface IndexaIntegrationService {
    suspend fun testConnection(accessToken: String): IndexaConnectionPreview

    suspend fun connect(accessToken: String): ExternalConnection

    suspend fun runSync(connectionId: String): ExternalSyncRun

    suspend fun disconnect(connectionId: String)
}
