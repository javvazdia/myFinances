package com.myfinances.app.integrations.indexa.sync

import com.myfinances.app.domain.model.integration.ExternalSyncRun
import com.myfinances.app.integrations.indexa.model.IndexaConnectionPreview

interface IndexaIntegrationService {
    suspend fun testConnection(accessToken: String): IndexaConnectionPreview

    suspend fun runSync(connectionId: String): ExternalSyncRun
}
