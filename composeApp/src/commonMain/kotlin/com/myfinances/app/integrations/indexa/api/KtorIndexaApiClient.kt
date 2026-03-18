package com.myfinances.app.integrations.indexa.api

import com.myfinances.app.integrations.indexa.model.IndexaAccountSummary
import com.myfinances.app.integrations.indexa.model.IndexaCashTransaction
import com.myfinances.app.integrations.indexa.model.IndexaInstrumentTransaction
import com.myfinances.app.integrations.indexa.model.IndexaPerformanceHistory
import com.myfinances.app.integrations.indexa.model.IndexaPortfolioPosition
import com.myfinances.app.integrations.indexa.model.IndexaPortfolioSnapshot
import com.myfinances.app.integrations.indexa.model.IndexaUserProfile
import com.myfinances.app.logging.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val INDEXA_API_BASE_URL = "https://api.indexacapital.com"
private const val INDEXA_AUTH_HEADER = "X-AUTH-TOKEN"

class KtorIndexaApiClient(
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
    },
) : IndexaApiClient {
    override suspend fun fetchUserProfile(accessToken: String): IndexaUserProfile {
        val response = httpClient.get {
            url("$INDEXA_API_BASE_URL/users/me")
            header(INDEXA_AUTH_HEADER, accessToken)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }.body<IndexaUserProfileResponse>()

        return IndexaUserProfile(
            email = response.email ?: response.username,
            fullName = extractPersonName(response.person),
            documentId = response.document,
            accounts = response.accounts.orEmpty().map { account ->
                IndexaAccountSummary(
                    accountNumber = account.accountNumber,
                    displayName = buildAccountDisplayName(
                        accountNumber = account.accountNumber,
                        type = account.type,
                    ),
                    productType = account.type,
                    providerCode = null,
                    currencyCode = "EUR",
                    currentValuation = null,
                )
            },
        )
    }

    override suspend fun fetchAccounts(accessToken: String): List<IndexaAccountSummary> =
        fetchUserProfile(accessToken).accounts

    override suspend fun fetchPortfolio(
        accessToken: String,
        accountNumber: String,
    ): IndexaPortfolioSnapshot {
        val response = httpClient.get {
            url("$INDEXA_API_BASE_URL/accounts/$accountNumber/portfolio")
            header(INDEXA_AUTH_HEADER, accessToken)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }.body<IndexaPortfolioResponse>()

        return IndexaPortfolioSnapshot(
            accountNumber = response.portfolio?.accountNumber ?: accountNumber,
            valuationDate = response.portfolio?.date,
            totalMarketValue = response.portfolio?.totalAmount,
            positions = response.instrumentAccounts
                .orEmpty()
                .flatMap { account ->
                    account.positions.orEmpty().map { position ->
                        IndexaPortfolioPosition(
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
    }

    override suspend fun fetchPerformance(
        accessToken: String,
        accountNumber: String,
    ): IndexaPerformanceHistory {
        AppLogger.debug(
            tag = "IndexaApi",
            message = "Fetching performance history for provider account $accountNumber",
        )
        val response = httpClient.get {
            url("$INDEXA_API_BASE_URL/accounts/$accountNumber/performance")
            header(INDEXA_AUTH_HEADER, accessToken)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }.body<JsonElement>()

        return parsePerformanceHistoryPayload(
            accountNumber = accountNumber,
            payload = response,
        )
    }

    override suspend fun fetchCashTransactions(
        accessToken: String,
        accountNumber: String,
    ): List<IndexaCashTransaction> {
        val response = httpClient.get {
            url("$INDEXA_API_BASE_URL/accounts/$accountNumber/cash-transactions")
            header(INDEXA_AUTH_HEADER, accessToken)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }.body<List<IndexaCashTransactionResponse>>()

        return response.map { transaction ->
            IndexaCashTransaction(
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
    }

    override suspend fun fetchInstrumentTransactions(
        accessToken: String,
        accountNumber: String,
    ): List<IndexaInstrumentTransaction> {
        throw UnsupportedOperationException(
            "Live instrument transaction sync is the next implementation step after connection setup.",
        )
    }

    private fun buildAccountDisplayName(
        accountNumber: String,
        type: String?,
    ): String {
        val productLabel = when (type?.lowercase()) {
            "mutual" -> "Indexa mutual account"
            "pension" -> "Indexa pension account"
            else -> "Indexa account"
        }

        return "$productLabel $accountNumber"
    }
}

internal fun extractPersonName(person: JsonElement?): String? =
    (person as? JsonObject)
        ?.get("name")
        ?.jsonPrimitive
        ?.contentOrNull

@Serializable
private data class IndexaUserProfileResponse(
    val username: String,
    val email: String? = null,
    val document: String? = null,
    val accounts: List<IndexaAccountResponse>? = null,
    val person: JsonElement? = null,
)

@Serializable
private data class IndexaAccountResponse(
    @SerialName("account_number")
    val accountNumber: String,
    val status: String? = null,
    val type: String? = null,
)

@Serializable
private data class IndexaPortfolioResponse(
    val portfolio: IndexaPortfolioSummaryResponse? = null,
    @SerialName("instrument_accounts")
    val instrumentAccounts: List<IndexaInstrumentAccountResponse>? = null,
)

internal fun parsePerformanceHistoryPayload(
    accountNumber: String,
    payload: JsonElement,
): IndexaPerformanceHistory {
    val root = payload as? JsonObject ?: JsonObject(emptyMap())
    val performance = root["performance"] as? JsonObject
    val performanceInterval = root["performance_interval"] as? JsonObject
    val topLevelHistory = root["history"]

    val topLevelHistoryMap = parseHistoryMap(topLevelHistory)
    val topLevelHistoryEntries = parseHistoryEntries(topLevelHistory)
    val performanceHistoryEntries = parseHistoryEntries(performance?.get("history"))
    val intervalHistoryEntries = parseHistoryEntries(performanceInterval?.get("history"))

    val valueHistory = when {
        topLevelHistoryEntries.isNotEmpty() -> topLevelHistoryEntries.associate { entry -> entry.date to entry.totalAmount }
        performanceHistoryEntries.isNotEmpty() -> performanceHistoryEntries.associate { entry -> entry.date to entry.totalAmount }
        intervalHistoryEntries.isNotEmpty() -> intervalHistoryEntries.associate { entry -> entry.date to entry.totalAmount }
        else -> emptyMap()
    }

    val normalizedHistory = parseNormalizedSeries(performance)
        .ifEmpty { parseNormalizedSeries(performanceInterval) }
        .ifEmpty { topLevelHistoryMap }

    AppLogger.debug(
        tag = "IndexaApi",
        message = buildString {
            append("Parsed performance payload for account ")
            append(accountNumber)
            append(": rootKeys=")
            append(root.keys.sorted().joinToString(","))
            append(", topLevelHistoryShape=")
            append(historyShape(topLevelHistory))
            append(", performanceHistoryShape=")
            append(historyShape(performance?.get("history")))
            append(", intervalHistoryShape=")
            append(historyShape(performanceInterval?.get("history")))
            append(", valueHistory=")
            append(valueHistory.size)
            append(", normalizedHistory=")
            append(normalizedHistory.size)
        },
    )

    return IndexaPerformanceHistory(
        accountNumber = accountNumber,
        valueHistory = valueHistory,
        normalizedHistory = normalizedHistory,
    )
}

private data class IndexaPerformanceHistoryEntryResponse(
    val date: String,
    val totalAmount: Double,
)

private fun parseHistoryMap(history: JsonElement?): Map<String, Double> =
    (history as? JsonObject)
        ?.mapNotNull { (date, value) ->
            value.jsonPrimitive.doubleOrNull?.let { numericValue ->
                date to numericValue
            }
        }
        ?.toMap()
        .orEmpty()

private fun parseHistoryEntries(history: JsonElement?): List<IndexaPerformanceHistoryEntryResponse> =
    (history as? JsonArray)
        ?.mapNotNull { element ->
            runCatching {
                val jsonObject = element.jsonObject
                val date = jsonObject["date"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
                val totalAmount = jsonObject["total_amount"]?.jsonPrimitive?.doubleOrNull ?: return@runCatching null
                IndexaPerformanceHistoryEntryResponse(
                    date = date,
                    totalAmount = totalAmount,
                )
            }.getOrNull()
        }
        .orEmpty()

private fun parseNormalizedSeries(section: JsonObject?): Map<String, Double> {
    val period = (section?.get("period") as? JsonArray)
        ?.mapNotNull { element -> element.jsonPrimitive.contentOrNull }
        .orEmpty()
    val real = (section?.get("real") as? JsonArray)
        ?.mapNotNull { element -> element.jsonPrimitive.doubleOrNull }
        .orEmpty()

    return period
        .zip(real)
        .associate { (date, value) -> date to (value / 100.0) }
}

private fun historyShape(history: JsonElement?): String =
    when (history) {
        is JsonArray -> "array:${history.size}"
        is JsonObject -> "object:${history.size}"
        null -> "missing"
        else -> history::class.simpleName ?: "unknown"
    }

@Serializable
private data class IndexaPortfolioSummaryResponse(
    @SerialName("account_number")
    val accountNumber: String? = null,
    val date: String? = null,
    @SerialName("total_amount")
    val totalAmount: Double? = null,
)

@Serializable
private data class IndexaInstrumentAccountResponse(
    val positions: List<IndexaInstrumentPositionResponse>? = null,
)

@Serializable
private data class IndexaInstrumentPositionResponse(
    val amount: Double? = null,
    val price: Double? = null,
    val titles: Double? = null,
    val instrument: IndexaInstrumentResponse? = null,
)

@Serializable
private data class IndexaInstrumentResponse(
    @SerialName("isin_code")
    val isinCode: String? = null,
    val name: String? = null,
    @SerialName("asset_class")
    val assetClass: String? = null,
)

@Serializable
private data class IndexaCashTransactionResponse(
    @SerialName("account_number")
    val accountNumber: String,
    val amount: Double,
    val comments: String? = null,
    val currency: String,
    val date: String,
    val fees: Double? = null,
    @SerialName("operation_code")
    val operationCode: Int? = null,
    @SerialName("operation_type")
    val operationType: String? = null,
    val reference: String,
    val status: String? = null,
    @SerialName("instrument_transaction")
    val instrumentTransaction: JsonElement? = null,
    val document: JsonElement? = null,
)
