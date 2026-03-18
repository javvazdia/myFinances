package com.myfinances.app.presentation.accounts

import com.myfinances.app.domain.model.InvestmentPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountsScreenTest {
    @Test
    fun normalizesCurrencyCode() {
        assertEquals("EUR", normalizeCurrencyCode(" eur "))
        assertNull(normalizeCurrencyCode("EU"))
        assertNull(normalizeCurrencyCode("EU1"))
    }

    @Test
    fun parsesOpeningBalanceIntoMinorUnits() {
        assertEquals(0L, parseAmountToMinor(""))
        assertEquals(123_45L, parseAmountToMinor("123.45"))
        assertEquals(-250_00L, parseAmountToMinor("-250"))
        assertEquals(12_30L, parseAmountToMinor("12,3"))
        assertNull(parseAmountToMinor("12.345"))
    }

    @Test
    fun formatsAccountAmountForEditingInput() {
        assertEquals("123.45", formatAccountAmountInput(123_45L))
        assertEquals("-250.00", formatAccountAmountInput(-250_00L))
    }

    @Test
    fun buildsReadablePositionSubtitle() {
        val subtitle = buildPositionSubtitle(
            InvestmentPosition(
                id = "position-1",
                accountId = "account-1",
                providerAccountId = "INDEXA01",
                instrumentIsin = "IE0032126645",
                instrumentName = "Vanguard US 500",
                assetClass = "equity_north_america",
                titles = 12.50,
                price = 244.10,
                marketValueMinor = 305_125,
                costAmountMinor = 280_000,
                valuationDate = "2026-03-18",
                updatedAtEpochMs = 1L,
            ),
        )

        assertEquals("equity_north_america | 12.5 units | @ 244.1", subtitle)
    }

    @Test
    fun buildsReadablePositionValuationLabel() {
        val label = buildPositionValuationLabel(
            position = InvestmentPosition(
                id = "position-1",
                accountId = "account-1",
                providerAccountId = "INDEXA01",
                instrumentIsin = "IE0032126645",
                instrumentName = "Vanguard US 500",
                assetClass = "equity_north_america",
                titles = 12.50,
                price = 244.10,
                marketValueMinor = 305_125,
                costAmountMinor = 280_000,
                valuationDate = "2026-03-18",
                updatedAtEpochMs = 1L,
            ),
            currencyCode = "EUR",
        )

        assertEquals("Market value 3051.25 EUR | Cost 2800.00 EUR", label)
    }
}
