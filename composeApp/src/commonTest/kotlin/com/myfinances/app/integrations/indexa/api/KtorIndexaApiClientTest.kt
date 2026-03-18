package com.myfinances.app.integrations.indexa.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KtorIndexaApiClientTest {
    @Test
    fun extractsPersonNameWhenPersonIsObject() {
        val person = Json.parseToJsonElement("""{"name":"Jane Doe"}""")

        assertEquals("Jane Doe", extractPersonName(person))
    }

    @Test
    fun ignoresPersonWhenIndexaReturnsEmptyArray() {
        val person = Json.parseToJsonElement("[]")

        assertNull(extractPersonName(person))
    }

    @Test
    fun mapsPortfolioPositionsFromIndexaResponse() {
        val response = Json.decodeFromString<PortfolioResponseForTest>(
            """
            {
              "portfolio": {
                "account_number": "INDEXA01",
                "date": "2017-03-07",
                "total_amount": 67471.4459556
              },
              "instrument_accounts": [
                {
                  "positions": [
                    {
                      "amount": 10633.078031,
                      "price": 21.9849,
                      "titles": 4841.19,
                      "instrument": {
                        "asset_class": "equity_north_america",
                        "isin_code": "IE0032126645",
                        "name": "Vanguard US 500 Stk Idx -Inst"
                      }
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val snapshot = response.toSnapshot()

        assertEquals("INDEXA01", snapshot.accountNumber)
        assertEquals("2017-03-07", snapshot.valuationDate)
        assertEquals(67471.4459556, snapshot.totalMarketValue)
        assertEquals(1, snapshot.positions.size)
        assertEquals("IE0032126645", snapshot.positions.first().isin)
        assertEquals("Vanguard US 500 Stk Idx -Inst", snapshot.positions.first().name)
    }
}

@kotlinx.serialization.Serializable
private data class PortfolioResponseForTest(
    val portfolio: PortfolioSummaryForTest? = null,
    @kotlinx.serialization.SerialName("instrument_accounts")
    val instrumentAccounts: List<InstrumentAccountForTest>? = null,
)

@kotlinx.serialization.Serializable
private data class PortfolioSummaryForTest(
    @kotlinx.serialization.SerialName("account_number")
    val accountNumber: String? = null,
    val date: String? = null,
    @kotlinx.serialization.SerialName("total_amount")
    val totalAmount: Double? = null,
)

@kotlinx.serialization.Serializable
private data class InstrumentAccountForTest(
    val positions: List<InstrumentPositionForTest>? = null,
)

@kotlinx.serialization.Serializable
private data class InstrumentPositionForTest(
    val amount: Double? = null,
    val price: Double? = null,
    val titles: Double? = null,
    val instrument: InstrumentForTest? = null,
)

@kotlinx.serialization.Serializable
private data class InstrumentForTest(
    @kotlinx.serialization.SerialName("isin_code")
    val isinCode: String? = null,
    val name: String? = null,
    @kotlinx.serialization.SerialName("asset_class")
    val assetClass: String? = null,
)

private fun PortfolioResponseForTest.toSnapshot() = com.myfinances.app.integrations.indexa.model.IndexaPortfolioSnapshot(
    accountNumber = portfolio?.accountNumber ?: "",
    valuationDate = portfolio?.date,
    totalMarketValue = portfolio?.totalAmount,
    positions = instrumentAccounts
        .orEmpty()
        .flatMap { account ->
            account.positions.orEmpty().map { position ->
                com.myfinances.app.integrations.indexa.model.IndexaPortfolioPosition(
                    isin = position.instrument?.isinCode,
                    name = position.instrument?.name ?: "Unknown instrument",
                    assetClass = position.instrument?.assetClass,
                    titles = position.titles,
                    price = position.price,
                    marketValue = position.amount,
                    costAmount = null,
                )
            }
        },
)
