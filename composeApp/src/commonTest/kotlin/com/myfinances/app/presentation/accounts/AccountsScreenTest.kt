package com.myfinances.app.presentation.accounts

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
}
