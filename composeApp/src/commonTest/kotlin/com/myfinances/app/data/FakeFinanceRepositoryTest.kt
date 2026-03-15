package com.myfinances.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeFinanceRepositoryTest {
    @Test
    fun overviewContainsStarterData() {
        val snapshot = FakeFinanceRepository().loadOverview()

        assertEquals("12,450.00 EUR", snapshot.totalBalance)
        assertTrue(snapshot.recentTransactions.isNotEmpty())
    }
}

