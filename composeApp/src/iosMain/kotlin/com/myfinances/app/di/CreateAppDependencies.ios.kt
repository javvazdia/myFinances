package com.myfinances.app.di

import com.myfinances.app.data.integration.IosConnectionSecretStore
import com.myfinances.app.data.local.db.createMyFinancesDatabase

fun createAppDependencies(): AppDependencies =
    buildAppDependencies(
        database = createMyFinancesDatabase(),
        connectionSecretStore = IosConnectionSecretStore(),
    )
