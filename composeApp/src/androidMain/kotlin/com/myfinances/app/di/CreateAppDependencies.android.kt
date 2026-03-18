package com.myfinances.app.di

import android.content.Context
import com.myfinances.app.data.integration.AndroidConnectionSecretStore
import com.myfinances.app.data.local.db.createMyFinancesDatabase

fun createAppDependencies(context: Context): AppDependencies =
    buildAppDependencies(
        database = createMyFinancesDatabase(context),
        connectionSecretStore = AndroidConnectionSecretStore(context.applicationContext),
    )
