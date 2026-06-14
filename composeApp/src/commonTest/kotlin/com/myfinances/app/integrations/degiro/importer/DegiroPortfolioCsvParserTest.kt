package com.myfinances.app.integrations.degiro.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DegiroPortfolioCsvParserTest {
    @Test
    fun parsesSpanishPortfolioExportWithCashAndPositions() {
        val portfolio = DegiroPortfolioCsvParser.parse(
            """
            Producto,Symbol/ISIN,Cantidad,Precio de,Valor local,,Valor en EUR
            CASH & CASH FUND & FTX CASH (EUR),,,,EUR,"2,89","2,89"
            AMUNDI MSCI EM MKTS SWAP UCITS E...,LU1681045453,3,"7,93",EUR,"23,80","23,80"
            ISHARES CORE MSCI WORLD UCITS ET...,IE00B4L5Y983,1,"123,90",EUR,"123,90","123,90"
            """.trimIndent(),
        )

        assertEquals("EUR", portfolio.currencyCode)
        assertEquals(3, portfolio.rows.size)
        assertEquals(2, portfolio.positions.size)
        assertEquals(2_89L, portfolio.cashBalanceMinor)
        assertEquals(150_59L, portfolio.totalValueMinor)
        assertTrue(portfolio.rows.first().isCash)
        assertEquals("LU1681045453", portfolio.positions.first().isin)
        assertEquals(3.0, portfolio.positions.first().quantity)
        assertEquals(7.93, portfolio.positions.first().price)
        assertEquals(23_80L, portfolio.positions.first().valueMinor)
    }

    @Test
    fun parsesNegativeMinorAmountsWithCents() {
        val portfolio = DegiroPortfolioCsvParser.parse(
            """
            Producto,Symbol/ISIN,Cantidad,Precio de,Valor local,,Valor en EUR
            CASH & CASH FUND & FTX CASH (EUR),,,,EUR,"-1,23","-1,23"
            """.trimIndent(),
        )

        assertEquals(-1_23L, portfolio.cashBalanceMinor)
        assertEquals(-1_23L, portfolio.totalValueMinor)
    }
}
