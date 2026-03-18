package com.myfinances.app.integrations.indexa.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KtorIndexaApiClientTest {
    private val testJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

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
        val response = testJson.decodeFromString<PortfolioResponseForTest>(
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

    @Test
    fun mapsCashTransactionsFromIndexaResponse() {
        val response = testJson.decodeFromString<List<CashTransactionResponseForTest>>(
            """
            [
              {
                "account_number": "INDEXA01",
                "amount": -3,
                "comments": "COMISI",
                "currency": "EUR",
                "date": "2016-03-31",
                "fees": 0,
                "operation_code": 5324,
                "operation_type": "COMISIÓN TRASPASO FONDO",
                "reference": "423999710",
                "status": "closed",
                "instrument_transaction": {},
                "document": []
              },
              {
                "account_number": "INDEXA01",
                "amount": 12,
                "comments": "RETROCESION COMISION MTTO C/C",
                "currency": "EUR",
                "date": "2016-12-23",
                "fees": 0,
                "operation_code": 4567,
                "operation_type": "RETROCESION COMISION MTTO C/C",
                "reference": "444621886",
                "status": "closed",
                "instrument_transaction": [],
                "document": []
              }
            ]
            """.trimIndent(),
        )

        val transactions = response.toCashTransactions()

        assertEquals(2, transactions.size)
        assertEquals("423999710", transactions.first().reference)
        assertEquals(-3.0, transactions.first().amount)
        assertEquals("COMISIÓN TRASPASO FONDO", transactions.first().operationType)
        assertEquals(4567, transactions.last().operationCode)
    }
    @Test
    fun mapsIndexaPerformanceResponseIntoValueAndNormalizedHistory() {
        val payload = testJson.parseToJsonElement(
            """
            {
              "performance": {
                "period": ["2024-01-31", "2024-02-29"],
                "real": [102, 105],
                "history": [
                  {
                    "date": "2024-01-31",
                    "total_amount": 12400.0
                  },
                  {
                    "date": "2024-02-29",
                    "total_amount": 12650.0
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        val history = parsePerformanceHistoryPayload(
            accountNumber = "INDEXA01",
            payload = payload,
        )

        assertEquals(12400.0, history.valueHistory["2024-01-31"])
        assertEquals(12650.0, history.valueHistory["2024-02-29"])
        assertEquals(1.02, history.normalizedHistory["2024-01-31"])
        assertEquals(1.05, history.normalizedHistory["2024-02-29"])
    }

    @Test
    fun mapsIndexaPerformanceIntervalShapeIntoValueAndNormalizedHistory() {
        val payload = testJson.parseToJsonElement(
            """
            {
              "history": [
                {
                  "date": "2024-01-31",
                  "total_amount": 12400.0
                },
                {
                  "date": "2024-02-29",
                  "total_amount": 12650.0
                }
              ],
              "performance_interval": {
                "period": ["2024-01-31", "2024-02-29"],
                "real": [102, 105]
              }
            }
            """.trimIndent(),
        )

        val history = parsePerformanceHistoryPayload(
            accountNumber = "INDEXA01",
            payload = payload,
        )

        assertEquals(12400.0, history.valueHistory["2024-01-31"])
        assertEquals(12650.0, history.valueHistory["2024-02-29"])
        assertEquals(1.02, history.normalizedHistory["2024-01-31"])
        assertEquals(1.05, history.normalizedHistory["2024-02-29"])
    }

    @Test
    fun mapsLiveIndexaObjectHistoryShapeIntoNormalizedHistory() {
        val payload = testJson.parseToJsonElement(
            """
            {
              "performance_interval": {
                "period": ["2024-09-30", "2024-10-31"],
                "real": [100.0, 101.5]
              },
              "history": {
                "2024-09-30": 1.0,
                "2024-10-31": 1.015
              }
            }
            """.trimIndent(),
        )

        val history = parsePerformanceHistoryPayload(
            accountNumber = "INDEXA01",
            payload = payload,
        )

        assertEquals(emptyMap(), history.valueHistory)
        assertEquals(1.0, history.normalizedHistory["2024-09-30"])
        assertEquals(1.015, history.normalizedHistory["2024-10-31"])
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

@kotlinx.serialization.Serializable
private data class CashTransactionResponseForTest(
    @kotlinx.serialization.SerialName("account_number")
    val accountNumber: String,
    val amount: Double,
    val comments: String? = null,
    val currency: String,
    val date: String,
    val fees: Double? = null,
    @kotlinx.serialization.SerialName("operation_code")
    val operationCode: Int? = null,
    @kotlinx.serialization.SerialName("operation_type")
    val operationType: String? = null,
    val reference: String,
)

private fun List<CashTransactionResponseForTest>.toCashTransactions() =
    map { transaction ->
        com.myfinances.app.integrations.indexa.model.IndexaCashTransaction(
            reference = transaction.reference,
            accountNumber = transaction.accountNumber,
            date = transaction.date,
            amount = transaction.amount,
            currencyCode = transaction.currency,
            fees = transaction.fees,
            operationCode = transaction.operationCode,
            operationType = transaction.operationType?.trim(),
            comments = transaction.comments?.trim(),
        )
    }
