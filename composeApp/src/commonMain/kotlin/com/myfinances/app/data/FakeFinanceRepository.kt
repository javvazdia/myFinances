package com.myfinances.app.data

import com.myfinances.app.domain.model.OverviewSnapshot
import com.myfinances.app.domain.model.RecentTransaction
import com.myfinances.app.domain.repository.FinanceRepository

class FakeFinanceRepository : FinanceRepository {
    override fun loadOverview(): OverviewSnapshot = OverviewSnapshot(
        totalBalance = "12,450.00 EUR",
        monthlyIncome = "4,300.00 EUR",
        monthlyExpenses = "2,175.00 EUR",
        savingsRate = "49%",
        focusMessage = "You are under budget this month. Next good milestone: categorize recurring payments.",
        recentTransactions = listOf(
            RecentTransaction(
                title = "Supermarket",
                category = "Groceries",
                amountLabel = "-82.45 EUR",
                dateLabel = "Today",
                isExpense = true,
            ),
            RecentTransaction(
                title = "Salary",
                category = "Income",
                amountLabel = "+3,950.00 EUR",
                dateLabel = "Mar 1",
                isExpense = false,
            ),
            RecentTransaction(
                title = "Electric bill",
                category = "Utilities",
                amountLabel = "-64.90 EUR",
                dateLabel = "Feb 28",
                isExpense = true,
            ),
        ),
    )
}

