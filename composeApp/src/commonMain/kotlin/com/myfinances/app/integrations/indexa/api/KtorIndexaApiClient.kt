package com.myfinances.app.integrations.indexa.api

import com.myfinances.app.integrations.indexa.model.IndexaAccountSummary
import com.myfinances.app.integrations.indexa.model.IndexaCashTransaction
import com.myfinances.app.integrations.indexa.model.IndexaInstrumentTransaction
import com.myfinances.app.integrations.indexa.model.IndexaPortfolioSnapshot
import com.myfinances.app.integrations.indexa.model.IndexaUserProfile
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json

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
        throw UnsupportedOperationException(
            "Live portfolio sync is the next implementation step after connection setup.",
        )
    }

    override suspend fun fetchCashTransactions(
        accessToken: String,
        accountNumber: String,
    ): List<IndexaCashTransaction> {
        throw UnsupportedOperationException(
            "Live cash transaction sync is the next implementation step after connection setup.",
        )
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
