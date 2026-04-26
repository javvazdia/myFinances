package com.myfinances.app.di

import com.myfinances.app.data.integration.DesktopConnectionSecretStore
import com.myfinances.app.data.local.db.createMyFinancesDatabase
import com.myfinances.app.integrations.cajaingenieros.sync.DesktopCajaIngenierosBrowserSyncService
import com.myfinances.app.integrations.statements.DesktopStatementImportService

fun createAppDependencies(): AppDependencies =
    buildAppDependencies(
        database = createMyFinancesDatabase(),
        connectionSecretStore = DesktopConnectionSecretStore(),
        statementImportServiceFactory = ::DesktopStatementImportService,
        cajaIngenierosBrowserSyncServiceFactory = ::DesktopCajaIngenierosBrowserSyncService,
    )
