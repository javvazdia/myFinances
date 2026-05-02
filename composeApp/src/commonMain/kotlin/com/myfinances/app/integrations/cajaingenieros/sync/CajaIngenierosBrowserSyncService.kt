package com.myfinances.app.integrations.cajaingenieros.sync

import com.myfinances.app.domain.model.integration.ExternalSyncRun

interface CajaIngenierosBrowserSyncService {
    val isSupported: Boolean

    suspend fun runAssistedStatementSync(
        connectionId: String,
        downloadsDirectory: String? = null,
        onProgress: suspend (String) -> Unit = {},
    ): ExternalSyncRun?
}

object UnsupportedCajaIngenierosBrowserSyncService : CajaIngenierosBrowserSyncService {
    override val isSupported: Boolean = false

    override suspend fun runAssistedStatementSync(
        connectionId: String,
        downloadsDirectory: String?,
        onProgress: suspend (String) -> Unit,
    ): ExternalSyncRun? =
        error("Browser-assisted Caja Ingenieros sync is not available on this platform yet.")
}
