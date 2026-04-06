package com.myfinances.app.integrations.cajaingenieros.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CajaIngenierosPdfStatementParserTest {
    @Test
    fun parsesCajaIngenierosStatementWithSingleLineAndMultilineTransactions() {
        val statement = CajaIngenierosPdfStatementParser.parse(sampleStatementText)

        assertEquals("ES7930250007771433268464", statement.iban)
        assertEquals("JAVIER VAZQUEZ DIAZ", statement.holderName)
        assertEquals(7_297_969L, statement.endingBalanceMinor)
        assertEquals("EUR", statement.currencyCode)
        assertTrue(statement.transactions.size >= 4)

        val firstTransaction = statement.transactions.first()
        assertEquals("06/04/2026", firstTransaction.operationDate)
        assertEquals("TARJETA *3024 UBER", firstTransaction.description)
        assertEquals("01/04/2026", firstTransaction.valueDate)
        assertEquals(-2_213L, firstTransaction.amountMinor)
        assertEquals(7_297_969L, firstTransaction.resultingBalanceMinor)

        val multilineTransaction = statement.transactions.first { transaction ->
            transaction.description.contains("DEVOLUCION TARJ.")
        }
        assertEquals(
            "DEVOLUCION TARJ. *3024 UBER EATS AMSTERD",
            multilineTransaction.description,
        )
        assertEquals(158L, multilineTransaction.amountMinor)
        assertEquals(7_472_740L, multilineTransaction.resultingBalanceMinor)

        val payrollTransaction = statement.transactions.first { transaction ->
            transaction.description.contains("NOMINA CTA DE")
        }
        assertEquals(512_790L, payrollTransaction.amountMinor)
        assertTrue(payrollTransaction.description.contains("R.G.T DESARROLLO INFORMAT"))
    }

    @Test
    fun parsesEuropeanAmountsIntoMinorUnits() {
        assertEquals(7_297_969L, parseEuropeanAmountToMinor("72.979,69"))
        assertEquals(-2_213L, parseEuropeanAmountToMinor("-22,13"))
        assertEquals(158L, parseEuropeanAmountToMinor("1,58"))
        assertEquals(512_790L, parseEuropeanAmountToMinor("5.127,90"))
    }
}

private val sampleStatementText = """
    Estás en: Cuentas » Consultas » Movimientos
    Consulta de movimientos
    Número de cuenta (IBAN):
    BIC entidad:
    Titulares:
    Saldo:
    ES79 3025 0007 7714 3326 8464
    CDENESBBXXX
    JAVIER VAZQUEZ DIAZ
    72.979,69 EUR
    FECHA DE OPERACIÓN CONCEPTO FECHA VALOR IMPORTE SALDO
    06/04/2026 TARJETA *3024 UBER 01/04/2026 -22,13 EUR 72.979,69 EUR
    06/04/2026 TARJETA *3024 FORKLABFOODS, S.L. 06/04/2026 -8,50 EUR 73.001,82 EUR
    06/04/2026 TARJETA *3024 DIA 7631 05/04/2026 -5,07 EUR 73.010,32 EUR
    30/03/2026
    DEVOLUCION TARJ. *3024 UBER EATS
    AMSTERD
    28/03/2026 1,58 EUR 74.727,40 EUR
    25/03/2026
    NOMINA CTA DE: R.G.T DESARROLLO
    INFORMAT
    25/03/2026 5.127,90 EUR 74.872,07 EUR
    24/03/2026 TARJETA *3024 MERCADONA AVDA. EUROPA 24/03/2026 -50,13 EUR 69.744,17 EUR
    Consulta de movimientos de cuentas : Banca Electrónica - Caja Ingenieros
""".trimIndent()
