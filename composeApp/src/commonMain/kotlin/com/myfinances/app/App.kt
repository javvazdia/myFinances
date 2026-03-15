package com.myfinances.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.myfinances.app.di.AppDependencies
import com.myfinances.app.presentation.MyFinancesApp
import com.myfinances.app.theme.MyFinancesTheme

@Composable
fun App(appDependencies: AppDependencies) {
    LaunchedEffect(appDependencies) {
        appDependencies.seedStarterData()
    }

    MyFinancesTheme {
        MyFinancesApp(appDependencies = appDependencies)
    }
}
