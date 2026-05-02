package com.myfinances.app.di

import com.myfinances.app.data.integration.IosConnectionSecretStore
import com.myfinances.app.data.local.db.createMyFinancesDatabase
import com.myfinances.app.integrations.cajaingenieros.sync.UnsupportedCajaIngenierosBrowserSyncService
import com.myfinances.app.integrations.statements.UnsupportedStatementImportService
import com.myfinances.app.platform.UnsupportedDirectoryPickerService

fun createAppDependencies(): AppDependencies =
    buildAppDependencies(
        database = createMyFinancesDatabase(),
        connectionSecretStore = IosConnectionSecretStore(),
        statementImportServiceFactory = { UnsupportedStatementImportService },
        cajaIngenierosBrowserSyncServiceFactory = { _, _ -> UnsupportedCajaIngenierosBrowserSyncService },
        directoryPickerService = UnsupportedDirectoryPickerService,
    )
