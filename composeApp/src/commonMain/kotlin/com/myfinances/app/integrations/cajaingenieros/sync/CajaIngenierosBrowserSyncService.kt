package com.myfinances.app.integrations.cajaingenieros.sync

import com.myfinances.app.domain.model.integration.ExternalSyncRun

interface CajaIngenierosBrowserSyncService {
    val isSupported: Boolean

    suspend fun runAssistedStatementSync(connectionId: String): ExternalSyncRun?
}

object UnsupportedCajaIngenierosBrowserSyncService : CajaIngenierosBrowserSyncService {
    override val isSupported: Boolean = false

    override suspend fun runAssistedStatementSync(connectionId: String): ExternalSyncRun? =
        error("Browser-assisted Caja Ingenieros sync is not available on this platform yet.")
}
