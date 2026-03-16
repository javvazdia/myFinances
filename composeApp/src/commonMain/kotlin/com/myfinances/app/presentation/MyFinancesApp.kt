package com.myfinances.app.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.myfinances.app.di.AppDependencies
import com.myfinances.app.navigation.AppDestination
import com.myfinances.app.platform.Platform
import com.myfinances.app.presentation.accounts.AccountsRoute
import com.myfinances.app.presentation.overview.OverviewRoute
import com.myfinances.app.presentation.transactions.TransactionsRoute
import com.myfinances.app.presentation.shared.PlaceholderScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyFinancesApp(appDependencies: AppDependencies) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("myFinances") },
                actions = {
                    Text(text = Platform.name)
                },
            )
        },
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Text(destination.badge)
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Overview.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppDestination.Overview.route) {
                OverviewRoute(financeRepository = appDependencies.financeRepository)
            }
            composable(AppDestination.Accounts.route) {
                AccountsRoute(
                    ledgerRepository = appDependencies.ledgerRepository,
                )
            }
            composable(AppDestination.Transactions.route) {
                TransactionsRoute(
                    ledgerRepository = appDependencies.ledgerRepository,
                )
            }
            composable(AppDestination.Budgets.route) {
                PlaceholderScreen(
                    title = "Budgets",
                    description = "Budget planning, monthly limits, and spending progress will be tracked here.",
                )
            }
            composable(AppDestination.Settings.route) {
                PlaceholderScreen(
                    title = "Settings",
                    description = "Use this area later for currency, categories, backups, and sync preferences.",
                )
            }
        }
    }
}
