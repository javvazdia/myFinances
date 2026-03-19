package com.myfinances.app.integrations.cajaingenieros.model

import kotlinx.serialization.Serializable

@Serializable
enum class CajaIngenierosEnvironment {
    SANDBOX,
    PRODUCTION,
}

@Serializable
data class CajaIngenierosCredentialBundle(
    val environment: CajaIngenierosEnvironment,
    val consumerKey: String,
    val consumerSecret: String,
    val appId: String? = null,
)

data class CajaIngenierosRegistrationMetadata(
    val onboardingMode: String,
    val supportsSandbox: Boolean,
    val notes: String,
)

data class CajaIngenierosAccountSummary(
    val accountId: String,
    val iban: String?,
    val displayName: String,
    val currencyCode: String?,
    val accountTypeLabel: String?,
    val ownerLabel: String?,
)

data class CajaIngenierosBalanceSnapshot(
    val accountId: String,
    val currencyCode: String?,
    val availableBalance: Double?,
    val currentBalance: Double?,
    val capturedAtEpochMs: Long?,
)

data class CajaIngenierosTransaction(
    val transactionId: String,
    val accountId: String,
    val amount: Double?,
    val currencyCode: String?,
    val description: String?,
    val bookedAt: String?,
    val valueAt: String?,
    val reference: String?,
    val transactionTypeLabel: String?,
)
