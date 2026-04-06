package com.myfinances.app.integrations.cajaingenieros.api

import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosAccessToken
import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosAccountSummary
import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosBalanceSnapshot
import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosCredentialBundle
import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosRegistrationMetadata
import com.myfinances.app.integrations.cajaingenieros.model.CajaIngenierosTransaction
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.URLProtocol
import io.ktor.serialization.kotlinx.json.json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val CAJA_INGENIEROS_API_HOST = "api.caixaenginyers.com"
private const val CAJA_INGENIEROS_XS2A_BASE_PATH = "/xs2a/2.0.0"
private const val CAJA_INGENIEROS_TOKEN_PATH = "/oauth2/token"

class KtorCajaIngenierosApiClient(
    private val httpClient: HttpClient = HttpClient(CIO) {
        defaultRequest {
            url {
                protocol = URLProtocol.HTTPS
                host = CAJA_INGENIEROS_API_HOST
            }
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
    },
) : CajaIngenierosApiClient {
    override suspend fun fetchRegistrationMetadata(): CajaIngenierosRegistrationMetadata =
        CajaIngenierosRegistrationMetadata(
            onboardingMode = "Developer portal + OAuth app credentials",
            supportsSandbox = true,
            notes = "Published XS2A endpoints use https://$CAJA_INGENIEROS_API_HOST$CAJA_INGENIEROS_XS2A_BASE_PATH and require Bearer, X-Request-ID, Digest, Signature, and date headers on AIS calls.",
        )

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun exchangeClientCredentialsToken(
        credentials: CajaIngenierosCredentialBundle,
    ): CajaIngenierosAccessToken {
        val basicAuthValue = "${credentials.consumerKey}:${credentials.consumerSecret}"
            .encodeToByteArray()
            .let(Base64.Default::encode)
        val response = httpClient.submitForm(
            url = CAJA_INGENIEROS_TOKEN_PATH,
            formParameters = Parameters.build {
                append("grant_type", "client_credentials")
            },
        ) {
            header(HttpHeaders.Authorization, "Basic $basicAuthValue")
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }.body<CajaIngenierosTokenResponse>()

        return CajaIngenierosAccessToken(
            accessToken = response.accessToken,
            tokenType = response.tokenType,
            expiresInSeconds = response.expiresIn,
            scope = response.scope,
        )
    }

    override suspend fun fetchAccounts(accessToken: String): List<CajaIngenierosAccountSummary> =
        error(
            "Caja Ingenieros account discovery still needs the signed AIS request flow. The published XS2A spec requires Digest, Signature, date, and PSU consent headers on /accounts.",
        )

    override suspend fun fetchBalance(
        accessToken: String,
        accountId: String,
    ): CajaIngenierosBalanceSnapshot = error(
        "Caja Ingenieros balance sync still needs the signed AIS request flow. The published XS2A spec requires Digest, Signature, date, and PSU consent headers on /accounts/{account-id}/balances.",
    )

    override suspend fun fetchTransactions(
        accessToken: String,
        accountId: String,
    ): List<CajaIngenierosTransaction> = error(
        "Caja Ingenieros transaction sync still needs the signed AIS request flow. The published XS2A spec requires Digest, Signature, date, and PSU consent headers on /accounts/{account-id}/transactions.",
    )
}

@Serializable
private data class CajaIngenierosTokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("token_type")
    val tokenType: String? = null,
    @SerialName("expires_in")
    val expiresIn: Long? = null,
    @SerialName("scope")
    val scope: String? = null,
)
