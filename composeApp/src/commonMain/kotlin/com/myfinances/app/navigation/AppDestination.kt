package com.myfinances.app.navigation

enum class AppDestination(
    val route: String,
    val label: String,
    val badge: String,
) {
    Overview("overview", "Overview", "Ov"),
    Accounts("accounts", "Accounts", "Ac"),
    Transactions("transactions", "Transactions", "Tx"),
    Budgets("budgets", "Budgets", "Bd"),
    Settings("settings", "Settings", "St"),
}
